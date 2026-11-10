package dev.daw.weightwatcher

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.daw.weightwatcher.DataTypeState.Status
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.comparisons.naturalOrder
import kotlin.comparisons.nullsFirst
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun interface WidgetUpdater {
  suspend fun updateWidgets()
}

fun interface ActiveDataTypesProvider {
  suspend fun getActiveDataTypes(): Set<DataType>
}

@Singleton
class SyncManager
@Inject
constructor(
  private val dataStore: DataStore<SyncSettings>,
  private val healthConnectClientProvider: Provider<HealthConnectClient>,
  private val widgetUpdater: WidgetUpdater,
  private val activeDataTypesProvider: ActiveDataTypesProvider,
) {
  companion object {
    fun getLatestRecordTimeFromChanges(
      changes: List<Change>,
      recordClass: KClass<out Record>,
    ): Instant? =
      changes
        .asSequence()
        .filterIsInstance<UpsertionChange>()
        .map { it.record }
        .filter { recordClass.isInstance(it) }
        .maxOfOrNull { recordTime(it) }

    /**
     * Returns when [record] was taken: the instant of an instantaneous record, or the end of an
     * interval record.
     */
    internal fun recordTime(record: Record): Instant =
      when (record) {
        is WeightRecord -> record.time
        is StepsRecord -> record.endTime
        is SleepSessionRecord -> record.endTime
        is ExerciseSessionRecord -> record.endTime
        is HeartRateRecord -> record.endTime
        is RestingHeartRateRecord -> record.time
        is BodyFatRecord -> record.time
        is Vo2MaxRecord -> record.time
        is BloodPressureRecord -> record.time
        else -> throw IllegalArgumentException("Unsupported Record: $record")
      }
  }

  private val healthConnectClient: HealthConnectClient by lazy { healthConnectClientProvider.get() }

  fun lastRecordTime(dataType: DataType): Flow<Instant?> =
    dataStore.data.map { settings ->
      settings.statesByTypeMap[dataType.number]?.lastRecordTimeOrNull?.toInstant()
    }

  /**
   * Syncs every data type shown on a widget, plus [extraDataTypes] (e.g. the type being viewed in
   * the app).
   */
  suspend fun performSync(extraDataTypes: Set<DataType> = emptySet()): Result<Unit> {
    val typesToSync: Set<DataType> = activeDataTypesProvider.getActiveDataTypes() + extraDataTypes
    if (typesToSync.isEmpty()) {
      Log.i("SyncManager", "No active widget data types to sync")
      return Result.success(Unit)
    }

    val granted: Set<String> = healthConnectClient.permissionController.getGrantedPermissions()
    val hasBackgroundPermission: Boolean =
      HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted

    val permittedTypes: Set<DataType> =
      if (hasBackgroundPermission) {
        typesToSync.filterTo(mutableSetOf()) { type -> type.readPermission in granted }
      } else {
        emptySet()
      }
    val unpermittedTypes: Set<DataType> = typesToSync - permittedTypes

    val settingsSnapshot: SyncSettings = dataStore.updateData { settings ->
      settings.copy {
        for (type in unpermittedTypes) {
          statesByType[type.number] = dataTypeState { status = Status.NO_PERMISSIONS }
        }
        for (type in permittedTypes) {
          statesByType[type.number] = settings.stateFor(type).copy { status = Status.SYNCING }
        }
      }
    }
    widgetUpdater.updateWidgets()

    if (permittedTypes.isEmpty()) {
      Log.i("SyncManager", "No permitted data types among $typesToSync")
      return Result.success(Unit)
    }

    var firstFailure: Throwable? = null
    val updatedStates = mutableMapOf<Int, DataTypeState>()

    for (dataType in permittedTypes) {
      val currentState: DataTypeState = settingsSnapshot.stateFor(dataType)
      val nextState: DataTypeState? =
        try {
          fetchNextDataTypeState(dataType, currentState)
        } catch (e: SecurityException) {
          Log.e("SyncManager", "SecurityException syncing $dataType: ${e.message}")
          updatedStates[dataType.number] = dataTypeState { status = Status.NO_PERMISSIONS }
          if (firstFailure == null) firstFailure = e
          continue
        } catch (e: Exception) {
          Log.e("SyncManager", "Error syncing $dataType: ${e.message}", e)
          updatedStates[dataType.number] = currentState.copy { status = Status.OK }
          if (firstFailure == null) firstFailure = e
          continue
        }

      if (nextState == null) {
        Log.i("SyncManager", "Changes token expired for $dataType, clearing and retrying")
        updatedStates[dataType.number] = currentState.copy {
          clearChangesToken()
          status = Status.OK
        }
        if (firstFailure == null) {
          firstFailure = IllegalStateException("Changes token expired for $dataType")
        }
      } else {
        updatedStates[dataType.number] = nextState
      }
    }

    dataStore.updateData { settings -> settings.copy { statesByType.putAll(updatedStates) } }
    widgetUpdater.updateWidgets()

    val failure: Throwable? = firstFailure
    return if (failure != null) Result.failure(failure) else Result.success(Unit)
  }

  /**
   * Fetches the latest record timestamp and next changes token from Health Connect for [dataType],
   * or returns `null` if the current changes token has expired.
   */
  private suspend fun fetchNextDataTypeState(
    dataType: DataType,
    state: DataTypeState,
  ): DataTypeState? {
    val recordClass: KClass<out Record> = dataType.recordClass
    val existingToken: String? = state.changesToken.takeIf { it.isNotEmpty() }
    var lastRecordTimeInstant: Instant? = state.lastRecordTimeOrNull?.toInstant()
    val nextToken: String

    if (existingToken == null) {
      nextToken =
        healthConnectClient.getChangesToken(ChangesTokenRequest(recordTypes = setOf(recordClass)))
      val response =
        healthConnectClient.readRecords(
          ReadRecordsRequest(
            recordClass,
            timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH),
            ascendingOrder = false,
            pageSize = 1,
          )
        )
      if (response.records.isNotEmpty()) {
        lastRecordTimeInstant = recordTime(response.records.single())
        Log.i("SyncManager", "Initial load for $dataType found record at $lastRecordTimeInstant")
      } else {
        Log.i("SyncManager", "Initial load for $dataType found no records")
      }
    } else {
      var currentToken: String = existingToken
      do {
        val changesResponse = healthConnectClient.getChanges(currentToken)
        if (changesResponse.changesTokenExpired) {
          return null
        }

        Log.i("SyncManager", "Fetched ${changesResponse.changes.size} changes for $dataType")

        val latestFromChanges = getLatestRecordTimeFromChanges(changesResponse.changes, recordClass)
        lastRecordTimeInstant =
          maxOf(lastRecordTimeInstant, latestFromChanges, nullsFirst(naturalOrder()))
        currentToken = changesResponse.nextChangesToken
      } while (changesResponse.hasMore)
      nextToken = currentToken
    }

    return state.copy {
      changesToken = nextToken
      if (lastRecordTimeInstant != null) {
        lastRecordTime = lastRecordTimeInstant.toTimestamp()
      } else {
        clearLastRecordTime()
      }
      status = Status.OK
    }
  }

  private fun SyncSettings.stateFor(dataType: DataType): DataTypeState =
    statesByTypeMap[dataType.number] ?: DataTypeState.getDefaultInstance()
}

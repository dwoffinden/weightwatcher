package dev.daw.weightwatcher

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ActivityIntensityRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.time.TimeRangeFilter
import dev.daw.weightwatcher.DataTypeState.Status
import java.time.Instant
import java.util.EnumMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.comparisons.naturalOrder
import kotlin.comparisons.nullsFirst
import kotlin.coroutines.cancellation.CancellationException
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
  private val healthConnectAccess: HealthConnectAccess,
  private val widgetUpdater: WidgetUpdater,
  private val activeDataTypesProvider: ActiveDataTypesProvider,
) {
  companion object {
    /** Returns the Health Connect permissions needed to sync [dataType] in the background. */
    fun requiredPermissionsFor(dataType: DataType): Set<String> =
      setOf(dataType.readPermission, HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)

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
        is HeartRateVariabilityRmssdRecord -> record.time
        is OxygenSaturationRecord -> record.time
        is RespiratoryRateRecord -> record.time
        is SkinTemperatureRecord -> record.endTime
        is DistanceRecord -> record.endTime
        is ActiveCaloriesBurnedRecord -> record.endTime
        is TotalCaloriesBurnedRecord -> record.endTime
        is FloorsClimbedRecord -> record.endTime
        is ElevationGainedRecord -> record.endTime
        is ActivityIntensityRecord -> record.endTime
        is HydrationRecord -> record.endTime
        is NutritionRecord -> record.endTime
        is BloodGlucoseRecord -> record.time
        is MindfulnessSessionRecord -> record.endTime
        is HeightRecord -> record.time
        else -> throw IllegalArgumentException("Unsupported Record: $record")
      }
  }

  fun lastRecordTime(dataType: DataType): Flow<Instant?> =
    dataStore.data.map { settings ->
      settings.stateFor(dataType)?.lastRecordTimeOrNull?.toInstant()
    }

  /**
   * Syncs every data type shown on a widget.
   *
   * Reads from Health Connect inside a single [DataStore.updateData] transaction, so concurrent
   * syncs run one after another, each starting from the state the previous one saved, and a sync
   * that is cancelled or fails unexpectedly saves nothing.
   */
  suspend fun performSync(): Result<Unit> {
    val typesToSync: Set<DataType> = activeDataTypesProvider.getActiveDataTypes()
    if (typesToSync.isEmpty()) {
      Log.i("SyncManager", "No active widget data types to sync")
      return Result.success(Unit)
    }

    val healthConnectClient: HealthConnectClient = healthConnectAccess.client()
    val granted: Set<String> = healthConnectClient.permissionController.getGrantedPermissions()
    val permittedTypes: Set<DataType> =
      typesToSync.filterTo(mutableSetOf()) { type ->
        granted.containsAll(requiredPermissionsFor(type))
      }
    val unpermittedTypes: Set<DataType> = typesToSync - permittedTypes

    // Lets widgets show that a sync is running (see WeightWorker.isSyncing).
    widgetUpdater.updateWidgets()

    var firstFailure: Throwable? = null
    dataStore.updateData { settings ->
      val states: EnumMap<DataType, DataTypeState> = settings.statesByType()
      val results: Map<DataType, DataTypeSyncResult> =
        permittedTypes.associateWithTo(emptyEnumMap()) { type ->
          syncDataType(
            healthConnectClient,
            type,
            states[type] ?: DataTypeState.getDefaultInstance(),
          )
        }
      firstFailure = results.values.firstNotNullOfOrNull { it.failure }
      for (type in unpermittedTypes) {
        states[type] = dataTypeState { status = Status.NO_PERMISSIONS }
      }
      for ((type, result) in results) {
        states[type] = result.state
      }
      settings.copy { setStatesByType(states) }
    }
    widgetUpdater.updateWidgets()

    val failure: Throwable? = firstFailure
    return if (failure != null) Result.failure(failure) else Result.success(Unit)
  }

  /** The state to save for a data type after syncing it, and why the sync failed, if it did. */
  private class DataTypeSyncResult(val state: DataTypeState, val failure: Throwable? = null)

  /**
   * Syncs [dataType] from [state], catching failures specific to it so that other types still sync.
   */
  private suspend fun syncDataType(
    healthConnectClient: HealthConnectClient,
    dataType: DataType,
    state: DataTypeState,
  ): DataTypeSyncResult {
    val nextState: DataTypeState? =
      try {
        fetchNextDataTypeState(healthConnectClient, dataType, state)
      } catch (e: CancellationException) {
        throw e
      } catch (e: SecurityException) {
        Log.e("SyncManager", "SecurityException syncing $dataType: ${e.message}")
        return DataTypeSyncResult(dataTypeState { status = Status.NO_PERMISSIONS }, e)
      } catch (e: Exception) {
        Log.e("SyncManager", "Error syncing $dataType: ${e.message}", e)
        return DataTypeSyncResult(state.copy { status = Status.OK }, e)
      }

    if (nextState == null) {
      Log.i("SyncManager", "Changes token expired for $dataType, clearing and retrying")
      return DataTypeSyncResult(
        state.copy {
          clearChangesToken()
          status = Status.OK
        },
        IllegalStateException("Changes token expired for $dataType"),
      )
    }
    return DataTypeSyncResult(nextState)
  }

  /**
   * Fetches the latest record timestamp and next changes token from Health Connect for [dataType],
   * or returns `null` if the current changes token has expired.
   */
  private suspend fun fetchNextDataTypeState(
    healthConnectClient: HealthConnectClient,
    dataType: DataType,
    state: DataTypeState,
  ): DataTypeState? {
    val existingToken: String? = state.changesToken.takeIf { it.isNotEmpty() }
    return if (existingToken == null) {
      fetchInitialState(healthConnectClient, dataType, state)
    } else {
      fetchDeltaState(healthConnectClient, dataType, state, existingToken)
    }
  }

  private suspend fun fetchInitialState(
    healthConnectClient: HealthConnectClient,
    dataType: DataType,
    state: DataTypeState,
  ): DataTypeState {
    val recordClass: KClass<out Record> = dataType.recordClass
    val nextToken: String =
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
    val lastRecordTime: Instant? =
      if (response.records.isEmpty()) {
        Log.i("SyncManager", "Initial load for $dataType found no records")
        state.lastRecordTimeOrNull?.toInstant()
      } else {
        val time: Instant = recordTime(response.records.single())
        Log.i("SyncManager", "Initial load for $dataType found record at $time")
        time
      }
    return syncedState(state, nextToken, lastRecordTime)
  }

  private suspend fun fetchDeltaState(
    healthConnectClient: HealthConnectClient,
    dataType: DataType,
    state: DataTypeState,
    initialToken: String,
  ): DataTypeState? {
    val recordClass: KClass<out Record> = dataType.recordClass
    var lastRecordTime: Instant? = state.lastRecordTimeOrNull?.toInstant()
    var currentToken: String = initialToken
    do {
      val changesResponse: ChangesResponse = healthConnectClient.getChanges(currentToken)
      if (changesResponse.changesTokenExpired) {
        return null
      }

      Log.i("SyncManager", "Fetched ${changesResponse.changes.size} changes for $dataType")

      val latestFromChanges: Instant? =
        getLatestRecordTimeFromChanges(changesResponse.changes, recordClass)
      lastRecordTime = maxOf(lastRecordTime, latestFromChanges, nullsFirst(naturalOrder()))
      currentToken = changesResponse.nextChangesToken
    } while (changesResponse.hasMore)
    return syncedState(state, currentToken, lastRecordTime)
  }

  private fun syncedState(
    state: DataTypeState,
    nextToken: String,
    lastRecordTimeInstant: Instant?,
  ): DataTypeState = state.copy {
    changesToken = nextToken
    if (lastRecordTimeInstant != null) {
      lastRecordTime = lastRecordTimeInstant.toTimestamp()
    } else {
      clearLastRecordTime()
    }
    status = Status.OK
  }
}

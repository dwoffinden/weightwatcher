package dev.daw.weightwatcher

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.daw.weightwatcher.SyncSettings.Status
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.comparisons.naturalOrder
import kotlin.comparisons.nullsFirst
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun interface WidgetUpdater {
  suspend fun updateWidgets()
}

@Singleton
class SyncManager
@Inject
constructor(
  private val dataStore: DataStore<SyncSettings>,
  private val healthConnectClientProvider: Provider<HealthConnectClient>,
  private val widgetUpdater: WidgetUpdater,
) {
  companion object {
    val REQUIRED_PERMISSIONS =
      setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
      )

    fun getLatestWeightTimeFromChanges(changes: List<Change>): Instant? =
      changes
        .asSequence()
        .filterIsInstance<UpsertionChange>()
        .map { it.record }
        .filterIsInstance<WeightRecord>()
        .maxOfOrNull { it.time }
  }

  private val healthConnectClient: HealthConnectClient by lazy { healthConnectClientProvider.get() }

  val lastWeightTime: Flow<Instant?> =
    dataStore.data.map { settings -> settings.lastWeightTimeOrNull?.toInstant() }

  suspend fun resetStatus() {
    dataStore.updateData { it.copy { status = Status.OK } }
    widgetUpdater.updateWidgets()
  }

  suspend fun performSync(): Result<Unit> {
    val granted = healthConnectClient.permissionController.getGrantedPermissions()

    if (!granted.containsAll(REQUIRED_PERMISSIONS)) {
      Log.i("SyncManager", "Permissions not granted, updating widget to Auth state")
      dataStore.updateData {
        it.copy {
          clearLastWeightTime()
          status = Status.NO_PERMISSIONS
        }
      }
      widgetUpdater.updateWidgets()
      return Result.success(Unit)
    }

    Log.i("SyncManager", "Starting Health Connect sync")
    val settings: SyncSettings = dataStore.updateData { it.copy { status = Status.SYNCING } }
    widgetUpdater.updateWidgets()

    val nextSettings: SyncSettings? =
      try {
        fetchNextSyncSettings(settings)
      } catch (e: SecurityException) {
        Log.e(
          "SyncManager",
          "SecurityException: Missing permissions for background read? ${e.message}",
        )
        dataStore.updateData {
          it.copy {
            clearLastWeightTime()
            status = Status.NO_PERMISSIONS
          }
        }
        widgetUpdater.updateWidgets()
        return Result.failure(e)
      } catch (e: Exception) {
        Log.e("SyncManager", "Error updating weight: ${e.message}", e)
        dataStore.updateData { it.copy { status = Status.OK } }
        widgetUpdater.updateWidgets()
        return Result.failure(e)
      }

    if (nextSettings == null) {
      Log.i("SyncManager", "Changes token expired, clearing and retrying")
      dataStore.updateData {
        it.copy {
          clearChangesToken()
          status = Status.OK
        }
      }
      widgetUpdater.updateWidgets()
      return Result.failure(IllegalStateException("Changes token expired"))
    }

    dataStore.updateData { nextSettings }
    widgetUpdater.updateWidgets()
    return Result.success(Unit)
  }

  /**
   * Fetches the latest weight timestamp and next changes token from Health Connect, or returns
   * `null` if the current changes token has expired.
   */
  private suspend fun fetchNextSyncSettings(settings: SyncSettings): SyncSettings? {
    val existingToken: String? = settings.changesToken.takeIf { it.isNotEmpty() }
    var lastWeightTimeInstant: Instant? = settings.lastWeightTimeOrNull?.toInstant()
    val nextToken: String

    if (existingToken == null) {
      nextToken =
        healthConnectClient.getChangesToken(
          ChangesTokenRequest(recordTypes = setOf(WeightRecord::class))
        )
      val response =
        healthConnectClient.readRecords(
          ReadRecordsRequest(
            WeightRecord::class,
            timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH),
            ascendingOrder = false,
            pageSize = 1,
          )
        )
      if (response.records.isNotEmpty()) {
        lastWeightTimeInstant = response.records.single().time
        Log.i("SyncManager", "Initial load found record at $lastWeightTimeInstant")
      } else {
        Log.i("SyncManager", "Initial load found no weight records")
      }
    } else {
      var currentToken: String = existingToken
      do {
        val changesResponse = healthConnectClient.getChanges(currentToken)
        if (changesResponse.changesTokenExpired) {
          return null
        }

        Log.i("SyncManager", "Fetched ${changesResponse.changes.size} changes")

        val latestFromChanges = getLatestWeightTimeFromChanges(changesResponse.changes)
        lastWeightTimeInstant =
          maxOf(lastWeightTimeInstant, latestFromChanges, nullsFirst(naturalOrder()))
        currentToken = changesResponse.nextChangesToken
      } while (changesResponse.hasMore)
      nextToken = currentToken
    }

    return settings.copy {
      changesToken = nextToken
      if (lastWeightTimeInstant != null) {
        lastWeightTime = lastWeightTimeInstant.toTimestamp()
      } else {
        clearLastWeightTime()
      }
      status = Status.OK
    }
  }
}

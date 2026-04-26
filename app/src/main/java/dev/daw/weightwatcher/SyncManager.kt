package dev.daw.weightwatcher

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// TODO: Remove this context extension and inject an explicit singleton via Dagger/Hilt instead.
private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(name = "sync_settings")

class SyncManager(private val context: Context) {
  private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

  val lastWeightTime: Flow<Instant?> =
      context.dataStore.data.map { prefs -> prefs[KEY_LAST_WEIGHT_TIME]?.let { Instant.parse(it) } }

  suspend fun performSync(): Result<Unit> {
    val granted = healthConnectClient.permissionController.getGrantedPermissions()

    if (!granted.containsAll(REQUIRED_PERMISSIONS)) {
      Log.i("SyncManager", "Permissions not granted, updating widget to Auth state")
      WeightGlanceWidget.updateAllEntries(context, null, hasPermissions = false, isSyncing = false)
      return Result.success(Unit)
    }

    Log.i("SyncManager", "Starting Health Connect sync")
    WeightGlanceWidget.updateAllEntries(context, null, hasPermissions = true, isSyncing = true)

    val prefs = context.dataStore.data.first()
    var changesToken = prefs[KEY_CHANGES_TOKEN]
    var lastWeightTimeInstant: Instant? = prefs[KEY_LAST_WEIGHT_TIME]?.let { Instant.parse(it) }

    try {
      if (changesToken == null) {
        val tokenRequest = ChangesTokenRequest(recordTypes = setOf(WeightRecord::class))
        changesToken = healthConnectClient.getChangesToken(tokenRequest)

        val response =
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(Instant.EPOCH, Instant.now()),
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
        val changesResponse = healthConnectClient.getChanges(changesToken)
        if (changesResponse.changesTokenExpired) {
          Log.i("SyncManager", "Changes token expired, clearing and retrying")
          context.dataStore.edit { it.remove(KEY_CHANGES_TOKEN) }
          return Result.failure(Exception("Token expired"))
        }

        Log.i("SyncManager", "Fetched ${changesResponse.changes.size} changes")

        lastWeightTimeInstant =
            getLatestWeightTimeFromChanges(changesResponse.changes, lastWeightTimeInstant)
        changesToken = changesResponse.nextChangesToken
      }

      // Save state
      context.dataStore.edit { editPrefs ->
        if (changesToken != null) {
          editPrefs[KEY_CHANGES_TOKEN] = changesToken!!
        }
        if (lastWeightTimeInstant != null) {
          editPrefs[KEY_LAST_WEIGHT_TIME] = lastWeightTimeInstant.toString()
        }
      }

      // Update UI
      WeightGlanceWidget.updateAllEntries(
          context,
          lastWeightTimeInstant,
          hasPermissions = true,
          isSyncing = false,
      )

      return Result.success(Unit)
    } catch (e: SecurityException) {
      Log.e(
          "SyncManager",
          "SecurityException: Missing permissions for background read? ${e.message}",
      )
      WeightGlanceWidget.updateAllEntries(context, null, hasPermissions = false, isSyncing = false)
      return Result.failure(e)
    } catch (e: Exception) {
      Log.e("SyncManager", "Error updating weight: ${e.message}", e)
      WeightGlanceWidget.updateAllEntries(context, null, hasPermissions = true, isSyncing = false)
      return Result.failure(e)
    }
  }

  suspend fun resetGlanceWidget() {
    WeightGlanceWidget.resetAll(context)
  }

  companion object {
    val REQUIRED_PERMISSIONS =
        setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
        )
    val KEY_CHANGES_TOKEN = stringPreferencesKey("changes_token")
    val KEY_LAST_WEIGHT_TIME = stringPreferencesKey("last_weight_time")

    fun getLatestWeightTimeFromChanges(
        changes: List<Change>,
        currentLatest: Instant?,
    ): Instant? {
      var latest = currentLatest
      for (change in changes) {
        if (change is UpsertionChange) {
          val record = change.record
          if (record is WeightRecord) {
            if (latest == null || record.time > latest) {
              latest = record.time
            }
          }
        }
      }
      return latest
    }
  }
}

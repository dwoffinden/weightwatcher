package dev.daw.weightwatcher

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class WeightWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)

    val permissions = setOf(HealthPermission.getReadPermission(WeightRecord::class))
    val granted = healthConnectClient.permissionController.getGrantedPermissions()

    if (!granted.containsAll(permissions)) {
      Log.i("WeightWorker", "Permissions not granted, updating widget to Auth state")
      WeightWidgetProvider.updateAppWidget(applicationContext, null, hasPermissions = false)
      return Result.success()
    }

    Log.i("WeightWorker", "Starting Health Connect sync")

    val prefs = applicationContext.getSharedPreferences("weight_prefs", Context.MODE_PRIVATE)
    var changesToken = prefs.getString("changes_token", null)
    var lastWeightTimeInstant: Instant? = null

    val lastTimeStr = prefs.getString("last_weight_time", null)
    if (lastTimeStr != null) {
      lastWeightTimeInstant = Instant.parse(lastTimeStr)
    }

    try {
      if (changesToken == null) {
        // Initial load: Fetch the latest token and the latest record
        val tokenRequest = ChangesTokenRequest(recordTypes = setOf(WeightRecord::class))
        changesToken = healthConnectClient.getChangesToken(tokenRequest)

        // Backfill the most recent data point
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
          lastWeightTimeInstant = response.records.first().time
          Log.i("WeightWorker", "Initial load found record at $lastWeightTimeInstant")
        } else {
          Log.i("WeightWorker", "Initial load found no weight records")
        }
      } else {
        // We have a token, fetch changelog
        val changesResponse = healthConnectClient.getChanges(changesToken)
        if (changesResponse.changesTokenExpired) {
          Log.i("WeightWorker", "Changes token expired, clearing and retrying")
          // Token expired, clear it and retry on next run
          prefs.edit().remove("changes_token").apply()
          return Result.retry()
        }

        Log.i("WeightWorker", "Fetched ${changesResponse.changes.size} changes")

        lastWeightTimeInstant =
            getLatestWeightTimeFromChanges(changesResponse.changes, lastWeightTimeInstant)
        changesToken = changesResponse.nextChangesToken
      }

      // Save state
      prefs
          .edit()
          .apply {
            putString("changes_token", changesToken)
            if (lastWeightTimeInstant != null) {
              putString("last_weight_time", lastWeightTimeInstant.toString())
            }
          }
          .apply()

      // Update UI
      if (lastWeightTimeInstant != null) {
        val hoursStale = Duration.between(lastWeightTimeInstant, Instant.now()).toHours()
        Log.i("WeightWorker", "Latest weight is $hoursStale hours stale")
        WeightWidgetProvider.updateAppWidget(applicationContext, hoursStale, hasPermissions = true)
      } else {
        Log.i("WeightWorker", "No weight records found, updating widget to default state")
        WeightWidgetProvider.updateAppWidget(applicationContext, null, hasPermissions = true)
      }

      return Result.success()
    } catch (e: SecurityException) {
      Log.e(
          "WeightWorker",
          "SecurityException: Missing permissions for background read? ${e.message}",
      )
      return Result.retry()
    } catch (e: Exception) {
      Log.e("WeightWorker", "Error updating weight: ${e.message}", e)
      return Result.retry()
    }
  }

  companion object {
    private const val WORK_NAME = "WeightPeriodicSync"

    fun enqueuePeriodicWork(context: Context) {
      val constraints = Constraints.Builder().build()

      val periodicWorkRequest =
          PeriodicWorkRequestBuilder<WeightWorker>(30, TimeUnit.MINUTES, 15, TimeUnit.MINUTES)
              .setConstraints(constraints)
              .build()

      WorkManager.getInstance(context)
          .enqueueUniquePeriodicWork(
              WORK_NAME,
              ExistingPeriodicWorkPolicy.KEEP,
              periodicWorkRequest,
          )
    }

    fun enqueueOneTimeWork(context: Context) {
      val request = OneTimeWorkRequestBuilder<WeightWorker>().build()
      WorkManager.getInstance(context)
          .enqueueUniqueWork(
              "WeightOneTimeSync",
              ExistingWorkPolicy.KEEP,
              request,
          )
    }

    fun getLatestWeightTimeFromChanges(
        changes: List<androidx.health.connect.client.changes.Change>,
        currentLatest: Instant?,
    ): Instant? {
      var latest = currentLatest
      for (change in changes) {
        if (change is androidx.health.connect.client.changes.UpsertionChange) {
          val record = change.record
          if (record is WeightRecord) {
            if (latest == null || record.time.isAfter(latest)) {
              latest = record.time
            }
          }
        }
      }
      return latest
    }
  }
}

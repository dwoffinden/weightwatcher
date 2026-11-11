package dev.daw.weightwatcher

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@HiltWorker
class WeightWorker
@AssistedInject
constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val syncManager: SyncManager,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val result = syncManager.performSync()
    return if (result.isSuccess) {
      Result.success()
    } else {
      Result.retry()
    }
  }

  companion object {
    private const val PERIODIC_WORK_NAME = "WeightPeriodicSync"
    internal const val ONE_TIME_WORK_NAME = "WeightOneTimeSync"

    fun enqueuePeriodicWork(context: Context) {
      val periodicWorkRequest =
        PeriodicWorkRequestBuilder<WeightWorker>(
            repeatInterval = 30.minutes,
            flexTimeInterval = 15.minutes,
          )
          .build()

      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
          PERIODIC_WORK_NAME,
          ExistingPeriodicWorkPolicy.KEEP,
          periodicWorkRequest,
        )
    }

    /** Emits whether a sync is running. */
    fun isSyncing(context: Context): Flow<Boolean> {
      val workManager = WorkManager.getInstance(context)
      return combine(
          workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME),
          workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME_WORK_NAME),
        ) { periodicWork, oneTimeWork ->
          (periodicWork + oneTimeWork).any { workInfo -> workInfo.state == WorkInfo.State.RUNNING }
        }
        .distinctUntilChanged()
    }

    /**
     * Requests a sync of every data type shown on a widget.
     *
     * If a one-off sync is already queued or running, this one runs after it rather than being
     * dropped, since the running sync may have read the widgets' data types before a change.
     */
    fun enqueueOneTimeWork(context: Context) {
      val request = OneTimeWorkRequestBuilder<WeightWorker>().build()
      WorkManager.getInstance(context)
        .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
  }
}

package dev.daw.weightwatcher

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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
    private const val WORK_NAME = "WeightPeriodicSync"

    fun enqueuePeriodicWork(context: Context) {
      val periodicWorkRequest =
        PeriodicWorkRequestBuilder<WeightWorker>(
            repeatInterval = 30.minutes,
            flexTimeInterval = 15.minutes,
          )
          .build()

      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, periodicWorkRequest)
    }

    fun enqueueOneTimeWork(context: Context) {
      val request = OneTimeWorkRequestBuilder<WeightWorker>().build()
      WorkManager.getInstance(context)
        .enqueueUniqueWork("WeightOneTimeSync", ExistingWorkPolicy.KEEP, request)
    }
  }
}

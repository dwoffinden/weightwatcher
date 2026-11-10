package dev.daw.weightwatcher

import android.app.Application
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class WeightWatcherApplication : Application(), Configuration.Provider {

  @field:Inject lateinit var workerFactory: HiltWorkerFactory
  @field:Inject lateinit var syncSettingsDataStore: DataStore<SyncSettings>

  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

  override fun onCreate() {
    super.onCreate()
    applicationScope.launch {
      try {
        WeightGlanceWidget()
          .migrateLegacyWidgets(this@WeightWatcherApplication, syncSettingsDataStore)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Not fatal: the migration retries on the next process start.
        Log.e("WeightWatcherApplication", "Legacy widget migration failed", e)
      }
    }
  }
}

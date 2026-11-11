package dev.daw.weightwatcher

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeightWorkerTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val healthConnectClient =
    PausingClient(FakeHealthConnectClient(permissionController = FakePermissionController()))

  @Before
  fun setUp() {
    val file: File = tempFolder.newFile()
    val syncManager =
      SyncManager(
        dataStore =
          DataStoreFactory.create(
            serializer = ProtoSerializer(SyncSettings.getDefaultInstance()),
            produceFile = { file },
          ),
        healthConnectClientProvider = { healthConnectClient },
        widgetUpdater = {},
        activeDataTypesProvider = { setOf(DataType.WEIGHT) },
      )
    val configuration: Configuration =
      Configuration.Builder()
        .setExecutor(SynchronousExecutor())
        .setWorkerFactory(
          object : WorkerFactory() {
            override fun createWorker(
              appContext: Context,
              workerClassName: String,
              workerParameters: WorkerParameters,
            ): ListenableWorker = WeightWorker(appContext, workerParameters, syncManager)
          }
        )
        .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
  }

  @After
  fun tearDown() {
    // Stops a worker still paused in Health Connect, so it doesn't outlive the test.
    WorkManager.getInstance(context).cancelAllWork().result.get()
  }

  @Test
  fun isSyncing_noSyncRequested_isFalse() =
    runBlocking<Unit> { assertThat(WeightWorker.isSyncing(context).first()).isFalse() }

  @Test
  fun isSyncing_whileSyncRuns_isTrue() =
    runBlocking<Unit> {
      WeightWorker.enqueueOneTimeWork(context)
      withTimeout(TIMEOUT) { healthConnectClient.syncStarted.await() }

      val isSyncing: Boolean = WeightWorker.isSyncing(context).first()

      assertThat(isSyncing).isTrue()
    }

  @Test
  fun isSyncing_afterSyncFinishes_isFalse() =
    runBlocking<Unit> {
      WeightWorker.enqueueOneTimeWork(context)
      withTimeout(TIMEOUT) { healthConnectClient.syncStarted.await() }

      healthConnectClient.resume.complete(Unit)
      withTimeout(TIMEOUT) { awaitOneTimeWorkFinished() }

      assertThat(WeightWorker.isSyncing(context).first()).isFalse()
    }

  private suspend fun awaitOneTimeWorkFinished() {
    WorkManager.getInstance(context)
      .getWorkInfosForUniqueWorkFlow(WeightWorker.ONE_TIME_WORK_NAME)
      .first { workInfos -> workInfos.isNotEmpty() && workInfos.all { it.state.isFinished } }
  }

  /** Pauses the first sync's initial Health Connect read until [resume] completes. */
  private class PausingClient(private val delegate: HealthConnectClient) :
    HealthConnectClient by delegate {
    val syncStarted = CompletableDeferred<Unit>()
    val resume = CompletableDeferred<Unit>()

    override suspend fun getChangesToken(request: ChangesTokenRequest): String {
      syncStarted.complete(Unit)
      resume.await()
      return delegate.getChangesToken(request)
    }
  }

  private companion object {
    val TIMEOUT: Duration = 5.seconds
  }
}

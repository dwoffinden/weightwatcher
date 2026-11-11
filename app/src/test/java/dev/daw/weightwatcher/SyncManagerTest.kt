package dev.daw.weightwatcher

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.health.connect.client.units.Mass
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.extensions.proto.LiteProtoTruth.assertThat
import dev.daw.weightwatcher.DataTypeState.Status
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncManagerTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private val permissionController = FakePermissionController(grantAll = true)
  private val fakeClient = FakeHealthConnectClient(permissionController = permissionController)
  private val observedWidgetStates = mutableListOf<SyncSettings>()
  private var activeDataTypes: Set<DataType> = setOf(DataType.WEIGHT)

  @Test
  fun getLatestRecordTimeFromChanges_emptyChanges_returnsNull() {
    val result: Instant? =
      SyncManager.getLatestRecordTimeFromChanges(emptyList(), WeightRecord::class)
    assertThat(result).isNull()
  }

  @Test
  fun getLatestRecordTimeFromChanges_multipleRecords_returnsNewestTimestamp() =
    runBlocking<Unit> {
      val token = fakeClient.getChangesToken(ChangesTokenRequest(setOf(WeightRecord::class)))
      val morning = Instant.parse("2026-01-01T10:00:00Z")
      val noon: Instant = morning + 2.hours
      val morningPlusOneHour: Instant = morning + 1.hours
      fakeClient.insertRecords(
        listOf(weightRecordAt(morning), weightRecordAt(noon), weightRecordAt(morningPlusOneHour))
      )

      val changesResponse = fakeClient.getChanges(token)
      val result: Instant? =
        SyncManager.getLatestRecordTimeFromChanges(changesResponse.changes, WeightRecord::class)

      assertThat(result).isEqualTo(noon)
    }

  @Test
  fun performSync_noActiveWidgets_doesNothing() =
    runBlocking<Unit> {
      activeDataTypes = emptySet()
      val dataStore = createDataStore()
      val syncManager = createSyncManager(dataStore)

      val result: Result<Unit> = syncManager.performSync()

      assertThat(result.isSuccess).isTrue()
      assertThat(observedWidgetStates).isEmpty()
      assertThat(dataStore.data.first()).isEqualToDefaultInstance()
    }

  @Test
  fun performSync_missingPermissions_clearsStateAndUpdatesStatusToNoPermissions() =
    runBlocking<Unit> {
      val recordTime = Instant.parse("2026-01-01T20:00:00Z")
      fakeClient.insertRecords(listOf(weightRecordAt(recordTime)))
      val dataStore = createDataStore()
      val syncManager = createSyncManager(dataStore)
      syncManager.performSync()
      permissionController.revokeAllPermissions()
      observedWidgetStates.clear()

      val result: Result<Unit> = syncManager.performSync()

      assertThat(result.isSuccess).isTrue()
      assertThat(observedWidgetStates.last())
        .isEqualTo(
          syncSettings {
            statesByType[DataType.WEIGHT.number] = dataTypeState { status = Status.NO_PERMISSIONS }
          }
        )
    }

  @Test
  fun performSync_partialPermissions_syncsPermittedTypeAndMarksUnpermittedType() =
    runBlocking<Unit> {
      activeDataTypes = setOf(DataType.WEIGHT, DataType.STEPS)
      permissionController.revokeAllPermissions()
      permissionController.grantPermissions(DataType.WEIGHT.requiredPermissions)
      val weightTime = Instant.parse("2026-01-01T20:00:00Z")
      val stepsStart = Instant.parse("2026-01-01T18:00:00Z")
      val stepsEnd: Instant = stepsStart + 1.hours
      fakeClient.insertRecords(
        listOf(
          weightRecordAt(weightTime),
          StepsRecord(
            startTime = stepsStart,
            startZoneOffset = ZoneOffset.UTC,
            endTime = stepsEnd,
            endZoneOffset = ZoneOffset.UTC,
            count = 1000,
            metadata = Metadata.manualEntry(),
          ),
        )
      )
      val dataStore = createDataStore()
      val syncManager = createSyncManager(dataStore)

      val result: Result<Unit> = syncManager.performSync()

      assertThat(result.isSuccess).isTrue()
      val savedSettings: SyncSettings = dataStore.data.first()
      val weightState: DataTypeState? = savedSettings.statesByTypeMap[DataType.WEIGHT.number]
      assertThat(weightState?.changesToken).isNotEmpty()
      assertThat(savedSettings)
        .isEqualTo(
          syncSettings {
            statesByType[DataType.WEIGHT.number] = dataTypeState {
              changesToken = weightState?.changesToken.orEmpty()
              lastRecordTime = weightTime.toTimestamp()
              status = Status.OK
            }
            statesByType[DataType.STEPS.number] = dataTypeState { status = Status.NO_PERMISSIONS }
          }
        )
    }

  @Test
  fun performSync_regrantedPermissionsAfterRevocation_queriesFromScratchAndRestoresWeightTime() =
    runBlocking<Unit> {
      val recordTime = Instant.parse("2026-01-01T20:00:00Z")
      fakeClient.insertRecords(listOf(weightRecordAt(recordTime)))
      val dataStore = createDataStore()
      val syncManager = createSyncManager(dataStore)
      syncManager.performSync()
      permissionController.revokeAllPermissions()
      syncManager.performSync()
      permissionController.grantPermissions(DataType.WEIGHT.requiredPermissions)

      val result: Result<Unit> = syncManager.performSync()

      assertThat(result.isSuccess).isTrue()
      val savedSettings: SyncSettings = dataStore.data.first()
      val weightState: DataTypeState? = savedSettings.statesByTypeMap[DataType.WEIGHT.number]
      assertThat(weightState?.changesToken).isNotEmpty()
      assertThat(savedSettings)
        .isEqualTo(
          syncSettings {
            statesByType[DataType.WEIGHT.number] = dataTypeState {
              changesToken = weightState?.changesToken.orEmpty()
              lastRecordTime = recordTime.toTimestamp()
              status = Status.OK
            }
          }
        )
    }

  @Test
  fun performSync_initialSyncWithRecord_persistsTokenWeightTimeAndStatus() =
    runBlocking<Unit> {
      val recordTime = Instant.parse("2026-01-01T20:00:00Z")
      fakeClient.insertRecords(listOf(weightRecordAt(recordTime)))
      val dataStore = createDataStore()
      val syncManager = createSyncManager(dataStore)

      val result: Result<Unit> = syncManager.performSync()

      assertThat(result.isSuccess).isTrue()
      val savedSettings: SyncSettings = dataStore.data.first()
      val weightState: DataTypeState? = savedSettings.statesByTypeMap[DataType.WEIGHT.number]
      assertThat(weightState?.changesToken).isNotEmpty()
      assertThat(savedSettings)
        .isEqualTo(
          syncSettings {
            statesByType[DataType.WEIGHT.number] = dataTypeState {
              changesToken = weightState?.changesToken.orEmpty()
              lastRecordTime = recordTime.toTimestamp()
              status = Status.OK
            }
          }
        )
      assertThat(syncManager.lastRecordTime(DataType.WEIGHT).first()).isEqualTo(recordTime)
      assertThat(observedWidgetStates)
        .containsExactly(SyncSettings.getDefaultInstance(), savedSettings)
        .inOrder()
    }

  @Test
  fun performSync_deltaSyncWithOlderRecord_preservesNewerWeightTime() =
    runBlocking<Unit> {
      val initialTime = Instant.parse("2026-01-01T12:00:00Z")
      val olderBackfillTime: Instant = initialTime - 1.hours
      fakeClient.insertRecords(listOf(weightRecordAt(initialTime)))
      val dataStore = createDataStore()
      val syncManager = createSyncManager(dataStore)
      syncManager.performSync()
      fakeClient.insertRecords(listOf(weightRecordAt(olderBackfillTime)))

      val result: Result<Unit> = syncManager.performSync()

      assertThat(result.isSuccess).isTrue()
      val savedSettings: SyncSettings = dataStore.data.first()
      val weightState: DataTypeState? = savedSettings.statesByTypeMap[DataType.WEIGHT.number]
      assertThat(weightState?.changesToken).isNotEmpty()
      assertThat(savedSettings)
        .isEqualTo(
          syncSettings {
            statesByType[DataType.WEIGHT.number] = dataTypeState {
              changesToken = weightState?.changesToken.orEmpty()
              lastRecordTime = initialTime.toTimestamp()
              status = Status.OK
            }
          }
        )
    }

  @Test
  fun performSync_deltaSyncAcrossMultiplePages_paginatesAndFindsNewestWeightTime() =
    runBlocking<Unit> {
      fakeClient.pageSizeGetChanges = 1
      val dataStore = createDataStore()
      val syncManager = createSyncManager(dataStore)
      syncManager.performSync()
      val firstPageTime = Instant.parse("2026-01-01T09:00:00Z")
      val secondPageNewestTime: Instant = firstPageTime + 9.hours
      val thirdPageTime: Instant = firstPageTime + 5.hours
      fakeClient.insertRecords(
        listOf(
          weightRecordAt(firstPageTime),
          weightRecordAt(secondPageNewestTime),
          weightRecordAt(thirdPageTime),
        )
      )

      val result: Result<Unit> = syncManager.performSync()

      assertThat(result.isSuccess).isTrue()
      val savedSettings: SyncSettings = dataStore.data.first()
      val weightState: DataTypeState? = savedSettings.statesByTypeMap[DataType.WEIGHT.number]
      assertThat(weightState?.changesToken).isNotEmpty()
      assertThat(savedSettings)
        .isEqualTo(
          syncSettings {
            statesByType[DataType.WEIGHT.number] = dataTypeState {
              changesToken = weightState?.changesToken.orEmpty()
              lastRecordTime = secondPageNewestTime.toTimestamp()
              status = Status.OK
            }
          }
        )
    }

  @Test
  fun performSync_expiredChangesToken_clearsTokenPreservesWeightTimeAndFails() =
    runBlocking<Unit> {
      val initialTime = Instant.parse("2026-01-01T12:00:00Z")
      fakeClient.insertRecords(listOf(weightRecordAt(initialTime)))
      val dataStore = createDataStore()
      val syncManager = createSyncManager(dataStore)
      syncManager.performSync()
      val tokenToExpire: String =
        dataStore.data.first().statesByTypeMap[DataType.WEIGHT.number]?.changesToken.orEmpty()
      fakeClient.expireToken(tokenToExpire)

      val result: Result<Unit> = syncManager.performSync()

      assertThat(result.isFailure).isTrue()
      assertThat(observedWidgetStates.last())
        .isEqualTo(
          syncSettings {
            statesByType[DataType.WEIGHT.number] = dataTypeState {
              lastRecordTime = initialTime.toTimestamp()
              status = Status.OK
            }
          }
        )
    }

  @Test
  fun performSync_extraDataTypeWithoutWidget_syncsIt() =
    runBlocking<Unit> {
      activeDataTypes = emptySet()
      val recordTime = Instant.parse("2026-01-01T20:00:00Z")
      fakeClient.insertRecords(listOf(sampleRecordOf(DataType.STEPS, recordTime)))
      val dataStore = createDataStore()
      val syncManager = createSyncManager(dataStore)

      syncManager.performSync(extraDataTypes = setOf(DataType.STEPS))

      assertThat(syncManager.lastRecordTime(DataType.STEPS).first()).isEqualTo(recordTime)
    }

  @Test
  fun performSync_deltaSyncWithNewRecordOfOneType_keepsOtherTypesLastRecordTime() =
    runBlocking<Unit> {
      activeDataTypes = setOf(DataType.WEIGHT, DataType.STEPS)
      val weightTime = Instant.parse("2026-01-01T08:00:00Z")
      val firstStepsTime = Instant.parse("2026-01-01T09:00:00Z")
      val secondStepsTime: Instant = firstStepsTime + 3.hours
      fakeClient.insertRecords(
        listOf(weightRecordAt(weightTime), sampleRecordOf(DataType.STEPS, firstStepsTime))
      )
      val dataStore = createDataStore()
      val syncManager = createSyncManager(dataStore)
      syncManager.performSync()
      fakeClient.insertRecords(listOf(sampleRecordOf(DataType.STEPS, secondStepsTime)))

      syncManager.performSync()

      assertThat(syncManager.lastRecordTime(DataType.WEIGHT).first()).isEqualTo(weightTime)
      assertThat(syncManager.lastRecordTime(DataType.STEPS).first()).isEqualTo(secondStepsTime)
    }

  @Test
  fun performSync_oneTypeFails_syncsOtherTypeAndReturnsFailure() =
    runBlocking<Unit> {
      activeDataTypes = setOf(DataType.WEIGHT, DataType.STEPS)
      val weightTime = Instant.parse("2026-01-01T08:00:00Z")
      fakeClient.insertRecords(listOf(weightRecordAt(weightTime)))
      val dataStore = createDataStore()
      val syncManager =
        createSyncManager(
          dataStore,
          client = FailingReadsClient(fakeClient, failingType = StepsRecord::class),
        )

      val result: Result<Unit> = syncManager.performSync()

      assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
      val savedSettings: SyncSettings = dataStore.data.first()
      val weightState: DataTypeState? = savedSettings.statesByTypeMap[DataType.WEIGHT.number]
      assertThat(savedSettings)
        .isEqualTo(
          syncSettings {
            statesByType[DataType.WEIGHT.number] = dataTypeState {
              changesToken = weightState?.changesToken.orEmpty()
              lastRecordTime = weightTime.toTimestamp()
              status = Status.OK
            }
            statesByType[DataType.STEPS.number] = dataTypeState { status = Status.OK }
          }
        )
    }

  @Test
  fun performSync_cancelledWhileFetching_savesNothing() =
    runBlocking<Unit> {
      val weightTime = Instant.parse("2026-01-01T08:00:00Z")
      fakeClient.insertRecords(listOf(weightRecordAt(weightTime)))
      val dataStore = createDataStore()
      createSyncManager(dataStore).performSync()
      val settingsBefore: SyncSettings = dataStore.data.first()
      fakeClient.insertRecords(listOf(weightRecordAt(weightTime + 1.hours)))
      val hangingClient = HangingChangesClient(fakeClient)
      val syncManager = createSyncManager(dataStore, client = hangingClient)
      val sync: Job = launch { syncManager.performSync() }
      hangingClient.changesRequested.await()

      sync.cancelAndJoin()

      assertThat(dataStore.data.first()).isEqualTo(settingsBefore)
    }

  @Test
  fun performSync_concurrentSyncs_secondStartsFromFirstsResult() =
    runBlocking<Unit> {
      val firstTime = Instant.parse("2026-01-01T08:00:00Z")
      fakeClient.insertRecords(listOf(weightRecordAt(firstTime)))
      val dataStore = createDataStore()
      createSyncManager(dataStore).performSync()
      fakeClient.insertRecords(listOf(weightRecordAt(firstTime + 1.hours)))
      val gatedClient = GatedChangesClient(fakeClient)
      val syncManager = createSyncManager(dataStore, client = gatedClient)
      val firstSync: Job = launch { syncManager.performSync() }
      gatedClient.firstChangesRequested.await()
      val secondSync: Job = launch { syncManager.performSync() }
      yield()

      gatedClient.gate.complete(Unit)
      joinAll(firstSync, secondSync)

      assertThat(gatedClient.requestedTokens).hasSize(2)
      assertThat(gatedClient.requestedTokens).containsNoDuplicates()
    }

  @Test
  fun dataStore_corruptedFile_throwsCorruptionException() =
    runBlocking<Unit> {
      val file: File = tempFolder.newFile("corrupted_sync_settings.pb")
      file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
      val dataStore: DataStore<SyncSettings> =
        DataStoreFactory.create(
          serializer = ProtoSerializer(SyncSettings.getDefaultInstance()),
          produceFile = { file },
        )

      assertFailsWith<CorruptionException> { dataStore.data.first() }
    }

  private fun createDataStore(): DataStore<SyncSettings> {
    val file: File = tempFolder.newFile()
    return DataStoreFactory.create(
      serializer = ProtoSerializer(SyncSettings.getDefaultInstance()),
      produceFile = { file },
    )
  }

  private fun createSyncManager(
    dataStore: DataStore<SyncSettings>,
    client: HealthConnectClient = fakeClient,
  ): SyncManager =
    SyncManager(
      dataStore = dataStore,
      healthConnectAccess = FakeHealthConnectAccess(client),
      widgetUpdater = { observedWidgetStates += dataStore.data.first() },
      activeDataTypesProvider = { activeDataTypes },
    )

  private fun weightRecordAt(time: Instant): WeightRecord =
    WeightRecord(
      time = time,
      zoneOffset = ZoneOffset.UTC,
      weight = Mass.kilograms(70.0),
      metadata = Metadata.manualEntry(),
    )

  /** Fails every read of [failingType], as if Health Connect errored. */
  private class FailingReadsClient(
    private val delegate: HealthConnectClient,
    private val failingType: KClass<out Record>,
  ) : HealthConnectClient by delegate {
    override suspend fun <T : Record> readRecords(
      request: ReadRecordsRequest<T>
    ): ReadRecordsResponse<T> =
      if (request.recordType == failingType) {
        throw IOException("Simulated failure reading $failingType")
      } else {
        delegate.readRecords(request)
      }
  }

  /**
   * Records the tokens passed to [getChanges], and holds the first call until [gate] completes, so
   * another sync can start meanwhile.
   */
  private class GatedChangesClient(private val delegate: HealthConnectClient) :
    HealthConnectClient by delegate {
    val firstChangesRequested = CompletableDeferred<Unit>()
    val gate = CompletableDeferred<Unit>()
    val requestedTokens = mutableListOf<String>()

    override suspend fun getChanges(changesToken: String): ChangesResponse {
      requestedTokens += changesToken
      if (firstChangesRequested.complete(Unit)) {
        gate.await()
      }
      return delegate.getChanges(changesToken)
    }
  }

  /** Never returns from [getChanges], so a sync can be cancelled mid-fetch. */
  private class HangingChangesClient(private val delegate: HealthConnectClient) :
    HealthConnectClient by delegate {
    val changesRequested = CompletableDeferred<Unit>()

    override suspend fun getChanges(changesToken: String): ChangesResponse {
      changesRequested.complete(Unit)
      awaitCancellation()
    }
  }
}

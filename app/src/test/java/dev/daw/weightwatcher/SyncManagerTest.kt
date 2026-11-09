package dev.daw.weightwatcher

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.health.connect.client.units.Mass
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.extensions.proto.LiteProtoTruth.assertThat
import dev.daw.weightwatcher.SyncSettings.Status
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

  @Test
  fun getLatestWeightTimeFromChanges_emptyChanges_returnsNull() {
    val result: Instant? = SyncManager.getLatestWeightTimeFromChanges(emptyList())
    assertThat(result).isNull()
  }

  @Test
  fun getLatestWeightTimeFromChanges_multipleRecords_returnsNewestTimestamp() = runTest {
    val token = fakeClient.getChangesToken(ChangesTokenRequest(setOf(WeightRecord::class)))
    val morning = Instant.parse("2026-01-01T10:00:00Z")
    val noon: Instant = morning + 2.hours
    val morningPlusOneHour: Instant = morning + 1.hours
    fakeClient.insertRecords(
      listOf(weightRecordAt(morning), weightRecordAt(noon), weightRecordAt(morningPlusOneHour))
    )

    val changesResponse = fakeClient.getChanges(token)
    val result: Instant? = SyncManager.getLatestWeightTimeFromChanges(changesResponse.changes)

    assertThat(result).isEqualTo(noon)
  }

  @Test
  fun performSync_missingPermissions_updatesStatusToNoPermissions() = runTest {
    permissionController.revokeAllPermissions()
    val dataStore = createDataStore(backgroundScope)
    val syncManager = createSyncManager(dataStore)

    val result: Result<Unit> = syncManager.performSync()

    assertThat(result.isSuccess).isTrue()
    assertThat(observedWidgetStates)
      .containsExactly(syncSettings { status = Status.NO_PERMISSIONS })
  }

  @Test
  fun performSync_initialSyncWithRecord_persistsTokenWeightTimeAndStatus() = runTest {
    val recordTime = Instant.parse("2026-01-01T20:00:00Z")
    fakeClient.insertRecords(listOf(weightRecordAt(recordTime)))
    val dataStore = createDataStore(backgroundScope)
    val syncManager = createSyncManager(dataStore)

    val result: Result<Unit> = syncManager.performSync()

    assertThat(result.isSuccess).isTrue()
    val savedSettings: SyncSettings = dataStore.data.first()
    assertThat(savedSettings.changesToken).isNotEmpty()
    assertThat(savedSettings)
      .isEqualTo(
        syncSettings {
          changesToken = savedSettings.changesToken
          lastWeightTime = recordTime.toTimestamp()
          status = Status.OK
        }
      )
    assertThat(syncManager.lastWeightTime.first()).isEqualTo(recordTime)
    assertThat(observedWidgetStates)
      .containsExactly(syncSettings { status = Status.SYNCING }, savedSettings)
      .inOrder()
  }

  @Test
  fun performSync_deltaSyncWithOlderRecord_preservesNewerWeightTime() = runTest {
    val initialTime = Instant.parse("2026-01-01T12:00:00Z")
    val olderBackfillTime: Instant = initialTime - 1.hours
    fakeClient.insertRecords(listOf(weightRecordAt(initialTime)))
    val dataStore = createDataStore(backgroundScope)
    val syncManager = createSyncManager(dataStore)
    syncManager.performSync()
    fakeClient.insertRecords(listOf(weightRecordAt(olderBackfillTime)))

    val result: Result<Unit> = syncManager.performSync()

    assertThat(result.isSuccess).isTrue()
    val savedSettings: SyncSettings = dataStore.data.first()
    assertThat(savedSettings.changesToken).isNotEmpty()
    assertThat(savedSettings)
      .isEqualTo(
        syncSettings {
          changesToken = savedSettings.changesToken
          lastWeightTime = initialTime.toTimestamp()
          status = Status.OK
        }
      )
  }

  @Test
  fun performSync_deltaSyncAcrossMultiplePages_paginatesAndFindsNewestWeightTime() = runTest {
    fakeClient.pageSizeGetChanges = 1
    val dataStore = createDataStore(backgroundScope)
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
    assertThat(savedSettings.changesToken).isNotEmpty()
    assertThat(savedSettings)
      .isEqualTo(
        syncSettings {
          changesToken = savedSettings.changesToken
          lastWeightTime = secondPageNewestTime.toTimestamp()
          status = Status.OK
        }
      )
  }

  @Test
  fun performSync_expiredChangesToken_clearsTokenPreservesWeightTimeAndFails() = runTest {
    val initialTime = Instant.parse("2026-01-01T12:00:00Z")
    fakeClient.insertRecords(listOf(weightRecordAt(initialTime)))
    val dataStore = createDataStore(backgroundScope)
    val syncManager = createSyncManager(dataStore)
    syncManager.performSync()
    val tokenToExpire: String = dataStore.data.first().changesToken
    fakeClient.expireToken(tokenToExpire)

    val result: Result<Unit> = syncManager.performSync()

    assertThat(result.isFailure).isTrue()
    assertThat(observedWidgetStates.last())
      .isEqualTo(
        syncSettings {
          lastWeightTime = initialTime.toTimestamp()
          status = Status.OK
        }
      )
  }

  @Test
  fun resetStatus_setsStatusToOkAndUpdatesWidgets() = runTest {
    permissionController.revokeAllPermissions()
    val dataStore = createDataStore(backgroundScope)
    val syncManager = createSyncManager(dataStore)
    syncManager.performSync()
    observedWidgetStates.clear()

    syncManager.resetStatus()

    assertThat(observedWidgetStates).containsExactly(syncSettings { status = Status.OK })
  }

  @Test
  fun dataStore_corruptedFile_throwsCorruptionException() = runTest {
    val file: File = tempFolder.newFile("corrupted_sync_settings.pb")
    file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
    val dataStore: DataStore<SyncSettings> =
      DataStoreFactory.create(
        serializer = ProtoSerializer(SyncSettings.getDefaultInstance(), SyncSettings.parser()),
        scope = backgroundScope,
        produceFile = { file },
      )

    assertFailsWith<CorruptionException> { dataStore.data.first() }
  }

  private fun createDataStore(scope: CoroutineScope): DataStore<SyncSettings> {
    val file: File = tempFolder.newFile()
    return DataStoreFactory.create(
      serializer = ProtoSerializer(SyncSettings.getDefaultInstance(), SyncSettings.parser()),
      scope = scope,
      produceFile = { file },
    )
  }

  private fun createSyncManager(dataStore: DataStore<SyncSettings>): SyncManager =
    SyncManager(
      dataStore = dataStore,
      healthConnectClientProvider = { fakeClient },
      widgetUpdater = { observedWidgetStates += dataStore.data.first() },
    )

  private fun weightRecordAt(time: Instant): WeightRecord =
    WeightRecord(
      time = time,
      zoneOffset = ZoneOffset.UTC,
      weight = Mass.kilograms(70.0),
      metadata = Metadata.manualEntry(),
    )
}

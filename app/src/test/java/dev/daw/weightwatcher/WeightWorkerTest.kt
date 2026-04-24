package dev.daw.weightwatcher

import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.units.Mass
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WeightWorkerTest {

  private val fakeClient = FakeHealthConnectClient()

  @Test
  fun testEmptyChangesReturnsCurrent() {
    val current = Instant.now()
    val result = SyncManager.getLatestWeightTimeFromChanges(emptyList(), current)
    assertThat(result).isEqualTo(current)
  }

  @Test
  fun testNullCurrentEmptyChangesReturnsNull() {
    val result = SyncManager.getLatestWeightTimeFromChanges(emptyList(), null)
    assertThat(result).isNull()
  }

  @Test
  fun testFindsNewestInChanges() = runTest {
    // Use FakeHealthConnectClient to generate "real" UpsertionChange objects
    val token = fakeClient.getChangesToken(ChangesTokenRequest(setOf(WeightRecord::class)))
    val t1 = Instant.parse("2024-01-01T10:00:00Z")
    val t2 = Instant.parse("2024-01-01T12:00:00Z")
    val t3 = Instant.parse("2024-01-01T11:00:00Z")
    val r1 =
        WeightRecord(
            time = t1,
            zoneOffset = ZoneOffset.UTC,
            weight = Mass.kilograms(70.0),
            metadata = Metadata.manualEntry(),
        )
    val r2 =
        WeightRecord(
            time = t2,
            zoneOffset = ZoneOffset.UTC,
            weight = Mass.kilograms(71.0),
            metadata = Metadata.manualEntry(),
        )
    val r3 =
        WeightRecord(
            time = t3,
            zoneOffset = ZoneOffset.UTC,
            weight = Mass.kilograms(72.0),
            metadata = Metadata.manualEntry(),
        )

    fakeClient.insertRecords(listOf(r1, r2, r3))

    val response = fakeClient.getChanges(token)
    val changes = response.changes

    val result = SyncManager.getLatestWeightTimeFromChanges(changes, null)
    assertThat(result).isEqualTo(t2)
  }

  @Test
  fun testIgnoresOlderThanCurrent() = runTest {
    val current = Instant.parse("2024-01-01T12:00:00Z")
    val older = Instant.parse("2024-01-01T11:00:00Z")

    val token = fakeClient.getChangesToken(ChangesTokenRequest(setOf(WeightRecord::class)))
    val r =
        WeightRecord(
            time = older,
            zoneOffset = ZoneOffset.UTC,
            weight = Mass.kilograms(70.0),
            metadata = Metadata.manualEntry(),
        )
    fakeClient.insertRecords(listOf(r))

    val response = fakeClient.getChanges(token)
    val changes = response.changes

    val result = SyncManager.getLatestWeightTimeFromChanges(changes, current)
    assertThat(result).isEqualTo(current)
  }
}

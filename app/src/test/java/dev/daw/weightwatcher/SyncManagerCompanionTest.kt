package dev.daw.weightwatcher

import androidx.health.connect.client.permission.HealthPermission
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith

/** Pure-JVM parameterised tests for [SyncManager]'s companion helpers. */
@RunWith(TestParameterInjector::class)
class SyncManagerCompanionTest {

  @Test
  fun requiredPermissionsFor_includesTypeReadAndBackgroundReadPermissions(
    @TestParameter dataType: DataType
  ) {
    assertThat(SyncManager.requiredPermissionsFor(dataType))
      .containsExactly(
        dataType.readPermission,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
      )
  }

  @Test
  fun recordTime_returnsInstantOrIntervalEnd(@TestParameter dataType: DataType) {
    val takenAt = Instant.parse("2026-01-01T10:00:00Z")
    val record = sampleRecordOf(dataType, takenAt)

    val result: Instant = SyncManager.recordTime(record)

    assertThat(result).isEqualTo(takenAt)
  }
}

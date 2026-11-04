package dev.daw.weightwatcher

import com.google.common.truth.extensions.proto.LiteProtoTruth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameters
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class WeightGlanceWidgetTest {

  @Test
  fun createMetadata_withLastWeightTime_setsTimestampAndOkStatus() {
    val time = Instant.parse("2026-01-01T12:00:00Z")

    val state: WidgetState = WeightGlanceWidget.createMetadata(lastWeightTime = time)

    assertThat(state)
      .isEqualTo(
        widgetState {
          lastWeightTime = time.toTimestamp()
          status = WidgetState.Status.OK
        }
      )
  }

  @Test
  @TestParameters("{hasPermissions: true, isSyncing: false, expectedStatus: OK}")
  @TestParameters("{hasPermissions: true, isSyncing: true, expectedStatus: SYNCING}")
  @TestParameters("{hasPermissions: false, isSyncing: false, expectedStatus: NO_PERMISSIONS}")
  @TestParameters("{hasPermissions: false, isSyncing: true, expectedStatus: SYNCING}")
  fun createMetadata_withoutLastWeightTime_setsExpectedStatus(
    hasPermissions: Boolean,
    isSyncing: Boolean,
    expectedStatus: WidgetState.Status,
  ) {
    val state: WidgetState =
      WeightGlanceWidget.createMetadata(
        lastWeightTime = null,
        hasPermissions = hasPermissions,
        isSyncing = isSyncing,
      )

    assertThat(state).isEqualTo(widgetState { status = expectedStatus })
  }
}

package dev.daw.weightwatcher

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.extensions.proto.LiteProtoTruth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameters
import java.time.Duration
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class WeightGlanceWidgetTest {

  @Test
  @TestParameters("{staleness: '-5h', expectedText: '-5h'}")
  @TestParameters("{staleness: '0h', expectedText: '0h'}")
  @TestParameters("{staleness: '20h', expectedText: '20h'}")
  @TestParameters("{staleness: '71h', expectedText: '71h'}")
  @TestParameters("{staleness: '72h', expectedText: '3d'}")
  @TestParameters("{staleness: '95h', expectedText: '3d'}")
  @TestParameters("{staleness: '96h', expectedText: '4d'}")
  @TestParameters("{staleness: '700h', expectedText: '29d'}")
  fun formatStaleness_returnsHoursBelow72hAndDaysOtherwise(
    staleness: Duration,
    expectedText: String,
  ) {
    assertThat(WeightGlanceWidget.formatStaleness(staleness)).isEqualTo(expectedText)
  }

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

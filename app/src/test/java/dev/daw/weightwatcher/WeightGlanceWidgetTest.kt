package dev.daw.weightwatcher

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameters
import dev.daw.weightwatcher.SyncSettings.Status
import java.time.Duration
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
  fun isSyncing_returnsTrueOnlyForSyncingAndUnspecified(@TestParameter status: Status) {
    assertThat(WeightGlanceWidget.isSyncing(status))
      .isEqualTo(status == Status.SYNCING || status == Status.STATUS_UNSPECIFIED)
  }
}

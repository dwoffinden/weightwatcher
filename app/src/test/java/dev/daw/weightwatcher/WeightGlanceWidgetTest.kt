package dev.daw.weightwatcher

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.KotlinTestParameters.testValues
import com.google.testing.junit.testparameterinjector.KotlinTestParameters.testValuesIn
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameters
import dev.daw.weightwatcher.DataTypeState.Status
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
  fun isSyncing_syncRunning_returnsTrue(@TestParameter status: Status) {
    assertThat(WeightGlanceWidget.isSyncing(status, isSyncRunning = true)).isTrue()
  }

  @Test
  fun isSyncing_syncNotRunningBeforeFirstSync_returnsTrue() {
    assertThat(WeightGlanceWidget.isSyncing(Status.STATUS_UNSPECIFIED, isSyncRunning = false))
      .isTrue()
  }

  @Test
  fun isSyncing_syncNotRunningAfterSync_returnsFalse(
    @TestParameter status: Status = testValues(Status.OK, Status.NO_PERMISSIONS)
  ) {
    assertThat(WeightGlanceWidget.isSyncing(status, isSyncRunning = false)).isFalse()
  }

  @Test
  fun resolveDataType_null_returnsNull() {
    assertThat(WeightGlanceWidget.resolveDataType(null)).isNull()
  }

  @Test
  fun resolveDataType_supportedTypeNumber_returnsType(
    @TestParameter dataType: DataType = testValuesIn(SUPPORTED_DATA_TYPES)
  ) {
    assertThat(WeightGlanceWidget.resolveDataType(dataType.number)).isEqualTo(dataType)
  }

  @Test
  fun resolveDataType_unsupportedNumber_returnsNull(
    @TestParameter("0", "-1", "999") rawNumber: Int
  ) {
    assertThat(WeightGlanceWidget.resolveDataType(rawNumber)).isNull()
  }
}

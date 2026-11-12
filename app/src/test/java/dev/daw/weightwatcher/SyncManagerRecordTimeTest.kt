package dev.daw.weightwatcher

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parameterised tests for [SyncManager.recordTime], kept apart from [SyncManagerTest] because that
 * uses the Robolectric runner, which can't be combined with [TestParameterInjector].
 */
@RunWith(TestParameterInjector::class)
class SyncManagerRecordTimeTest {

  @Test
  fun recordTime_returnsInstantOrIntervalEnd(@TestParameter dataType: DataType) {
    val takenAt = Instant.parse("2026-01-01T10:00:00Z")
    val record = sampleRecordOf(dataType, takenAt)

    val result: Instant = SyncManager.recordTime(record)

    assertThat(result).isEqualTo(takenAt)
  }
}

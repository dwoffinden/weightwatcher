package dev.daw.weightwatcher

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.extensions.proto.LiteProtoTruth.assertThat
import com.google.protobuf.Timestamp
import com.google.protobuf.timestamp
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameters
import java.time.Duration
import java.time.Instant
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class TimestampsTest {

  @Test
  fun intDays_returnsDuration() {
    assertThat(365.days).isEqualTo(Duration.ofDays(365))
  }

  @Test
  fun longDays_returnsDuration() {
    assertThat(365L.days).isEqualTo(Duration.ofDays(365))
  }

  @Test
  fun intHours_returnsDuration() {
    assertThat(20.hours).isEqualTo(Duration.ofHours(20))
  }

  @Test
  fun longHours_returnsDuration() {
    assertThat(20L.hours).isEqualTo(Duration.ofHours(20))
  }

  @Test
  fun intMinutes_returnsDuration() {
    assertThat(15.minutes).isEqualTo(Duration.ofMinutes(15))
  }

  @Test
  fun longMinutes_returnsDuration() {
    assertThat(15L.minutes).isEqualTo(Duration.ofMinutes(15))
  }

  @Test
  fun intSeconds_returnsDuration() {
    assertThat(5.seconds).isEqualTo(Duration.ofSeconds(5))
  }

  @Test
  fun longSeconds_returnsDuration() {
    assertThat(5L.seconds).isEqualTo(Duration.ofSeconds(5))
  }

  @Test
  fun minus_returnsDurationBetweenInstants() {
    val earlier = Instant.parse("2026-01-01T10:00:00Z")
    val later = Instant.parse("2026-01-01T12:30:00Z")

    val difference: Duration = later - earlier

    assertThat(difference).isEqualTo(150.minutes)
  }

  @Test
  fun toTimestamp_convertsSecondsAndNanos() {
    val instant = Instant.ofEpochSecond(1_700_000_000L, 123_456_789L)
    val result: Timestamp = instant.toTimestamp()
    assertThat(result)
      .isEqualTo(
        timestamp {
          seconds = 1_700_000_000L
          nanos = 123_456_789
        }
      )
  }

  @Test
  fun toInstant_convertsSecondsAndNanos() {
    val protoTimestamp: Timestamp = timestamp {
      seconds = 1_700_000_000L
      nanos = 123_456_789
    }
    val result: Instant = protoTimestamp.toInstant()
    assertThat(result).isEqualTo(Instant.ofEpochSecond(1_700_000_000L, 123_456_789L))
  }

  @Test
  @TestParameters("{seconds: -62135596800, nanos: 0}")
  @TestParameters("{seconds: 253402300799, nanos: 999999999}")
  fun toInstant_acceptsBoundaryTimestamps(seconds: Long, nanos: Int) {
    val boundaryTimestamp: Timestamp = timestamp {
      this.seconds = seconds
      this.nanos = nanos
    }

    val result: Instant = boundaryTimestamp.toInstant()

    assertThat(result.epochSecond).isEqualTo(seconds)
    assertThat(result.nano).isEqualTo(nanos)
  }

  @Test
  @TestParameters("{seconds: -62135596801, nanos: 0}")
  @TestParameters("{seconds: 253402300800, nanos: 0}")
  @TestParameters("{seconds: 0, nanos: -1}")
  @TestParameters("{seconds: 0, nanos: 1000000000}")
  fun toInstant_outOfRange_throwsIllegalArgumentException(seconds: Long, nanos: Int) {
    val invalidTimestamp: Timestamp = timestamp {
      this.seconds = seconds
      this.nanos = nanos
    }
    assertFailsWith<IllegalArgumentException> { invalidTimestamp.toInstant() }
  }
}

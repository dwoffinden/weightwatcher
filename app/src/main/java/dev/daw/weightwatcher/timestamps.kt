package dev.daw.weightwatcher

import com.google.protobuf.Timestamp
import java.time.Duration
import java.time.Instant

/** Returns the [Duration] between [other] and this [Instant]. */
operator fun Instant.minus(other: Instant): Duration = Duration.between(other, this)

/** Converts an [Instant] to a [Timestamp]. */
fun Instant.toTimestamp(): Timestamp {
  return Timestamp.newBuilder().setSeconds(epochSecond).setNanos(nano).build()
}

/** Converts a [Timestamp] to an [Instant]. Throws [IllegalArgumentException] if out of range. */
fun Timestamp.toInstant(): Instant {
  require(seconds in -62135596800L..253402300799L) { "Seconds out of range: $seconds" }
  require(nanos in 0..999999999) { "Nanos out of range: $nanos" }
  return Instant.ofEpochSecond(seconds, nanos.toLong())
}

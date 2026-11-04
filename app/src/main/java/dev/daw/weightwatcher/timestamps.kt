package dev.daw.weightwatcher

import com.google.protobuf.Timestamp
import com.google.protobuf.timestamp
import java.time.Duration
import java.time.Instant

/**
 * Returns a [Duration] of this many hours (the [java.time.Duration] equivalent of
 * [kotlin.time.Duration.Companion.hours]).
 */
val Int.hours: Duration
  get() = toLong().hours

/**
 * Returns a [Duration] of this many hours (the [java.time.Duration] equivalent of
 * [kotlin.time.Duration.Companion.hours]).
 */
val Long.hours: Duration
  get() = Duration.ofHours(this)

/**
 * Returns a [Duration] of this many minutes (the [java.time.Duration] equivalent of
 * [kotlin.time.Duration.Companion.minutes]).
 */
val Int.minutes: Duration
  get() = toLong().minutes

/**
 * Returns a [Duration] of this many minutes (the [java.time.Duration] equivalent of
 * [kotlin.time.Duration.Companion.minutes]).
 */
val Long.minutes: Duration
  get() = Duration.ofMinutes(this)

/** Returns the [Duration] between [other] and this [Instant]. */
operator fun Instant.minus(other: Instant): Duration = Duration.between(other, this)

/**
 * Converts an [Instant] to a [Timestamp] (https://github.com/protocolbuffers/protobuf/issues/6641).
 */
fun Instant.toTimestamp(): Timestamp = timestamp {
  seconds = epochSecond
  nanos = nano
}

/**
 * Converts a [Timestamp] to an [Instant] (https://github.com/protocolbuffers/protobuf/issues/6641).
 * Throws [IllegalArgumentException] if out of range.
 */
fun Timestamp.toInstant(): Instant {
  checkValid()
  return Instant.ofEpochSecond(seconds, nanos.toLong())
}

/**
 * Validates that this [Timestamp] is within the `google.protobuf.Timestamp` spec range
 * (`0001-01-01T00:00:00Z` to `9999-12-31T23:59:59.999999999Z`), which is stricter than [Instant].
 *
 * Equivalent to `com.google.protobuf.util.Timestamps.checkValid`
 * (https://protobuf.dev/reference/java/api-docs/com/google/protobuf/util/Timestamps.html), which is
 * not included in `protobuf-javalite` (https://github.com/protocolbuffers/protobuf/issues/7094).
 */
private fun Timestamp.checkValid() {
  require(seconds in -62135596800L..253402300799L) { "Seconds out of range: $seconds" }
  require(nanos in 0..999_999_999) { "Nanos out of range: $nanos" }
}

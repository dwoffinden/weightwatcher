package dev.daw.weightwatcher.widget.remotecompose

import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteInt
import androidx.compose.remote.creation.compose.state.RemoteTimeVariables
import androidx.compose.remote.creation.compose.state.ri
import com.google.common.primitives.Ints
import dev.daw.weightwatcher.days
import dev.daw.weightwatcher.hours
import dev.daw.weightwatcher.minutes
import java.time.Duration
import java.time.Instant

/**
 * Compile-time-only wrapper around a RemoteCompose duration (stored as 32-bit seconds), providing
 * strongly-typed [Duration] thresholds and whole-unit conversions evaluated live by the player.
 *
 * Uses 32-bit [RemoteTimeVariables.epochSecond] (so this only works until the Year 2038 rollover)
 * rather than float-backed `deltaFromReferenceInHours`: `RemoteFloat.toRemoteInt()` in
 * `androidx.compose.remote:1.0.0-alpha23` passes the float's ID into an integer expression that
 * reads the integer store instead of the float store (always evaluating to `0`), so float-backed
 * comparisons cannot drive component visibility.
 */
@JvmInline
internal value class RemoteDuration(private val seconds: RemoteInt) {
  companion object {
    fun since(reference: Instant): RemoteDuration =
      RemoteDuration(RemoteTimeVariables.epochSecond - Ints.saturatedCast(reference.epochSecond).ri)
  }

  infix fun isGreaterThan(threshold: Duration): RemoteBoolean =
    seconds.isGreaterThan(Ints.saturatedCast(threshold.seconds).ri)

  infix fun isLessThan(threshold: Duration): RemoteBoolean =
    seconds.isLessThan(Ints.saturatedCast(threshold.seconds).ri)

  fun toWholeMinutes(): RemoteInt = seconds / Ints.saturatedCast(1.minutes.seconds)

  fun toWholeHours(): RemoteInt = seconds / Ints.saturatedCast(1.hours.seconds)

  fun toWholeDays(): RemoteInt = seconds / Ints.saturatedCast(1.days.seconds)

  fun toWholeYears(yearLength: Duration): RemoteInt =
    seconds / Ints.saturatedCast(yearLength.seconds)
}

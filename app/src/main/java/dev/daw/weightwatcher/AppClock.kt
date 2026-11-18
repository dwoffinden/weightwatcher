package dev.daw.weightwatcher

import android.os.SystemClock
import java.time.Duration
import java.time.Instant
import java.time.InstantSource

/** Provides both wall-clock [Instant] and monotonic boot-relative [elapsedRealtime]. */
interface AppClock : InstantSource {
  fun elapsedRealtime(): Duration

  companion object {
    fun system(): AppClock =
      object : AppClock {
        override fun instant(): Instant = InstantSource.system().instant()

        override fun elapsedRealtime(): Duration =
          Duration.ofNanos(SystemClock.elapsedRealtimeNanos())
      }

    fun fixed(instant: Instant, elapsedRealtime: Duration = Duration.ZERO): AppClock =
      object : AppClock {
        override fun instant(): Instant = instant

        override fun elapsedRealtime(): Duration = elapsedRealtime
      }
  }
}

package dev.daw.weightwatcher.widget.glance

import android.content.Context
import android.widget.RemoteViews
import dev.daw.weightwatcher.R
import java.time.Duration

/**
 * Builds a `<Chronometer>` [RemoteViews] for Glance widgets whose staleness is under an hour,
 * ticking `MM:SS` from [elapsedRealtime] minus [staleness] without waking the app.
 */
internal fun createChronometerRemoteViews(
  context: Context,
  staleness: Duration,
  elapsedRealtime: Duration,
): RemoteViews =
  RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
    setChronometer(
      R.id.widget_chronometer,
      /* base= */ (elapsedRealtime - staleness).toMillis(),
      /* format= */ null,
      /* started= */ true,
    )
  }

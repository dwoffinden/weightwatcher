package dev.daw.weightwatcher.widget.remotecompose

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import dev.daw.weightwatcher.WeightGlanceWidget
import dev.daw.weightwatcher.WeightWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Triggers an immediate [WeightWorker] sync when a RemoteCompose widget is tapped, pushing
 * `"Sync..."` first so fast syncs still give immediate visual feedback without racing the post-sync
 * update.
 *
 * Not exported: the widget's click action is a [PendingIntent] delivered as this app.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {

  companion object {
    fun createPendingIntent(context: Context): PendingIntent =
      PendingIntent.getBroadcast(
        context,
        /* requestCode= */ 0,
        Intent(context, WidgetRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
  }

  override fun onReceive(context: Context, intent: Intent) {
    val appWidgetIds: IntArray = WeightGlanceWidget.getPlacedAppWidgetIds(context)
    if (!WeightGlanceWidget.useRemoteCompose() || appWidgetIds.isEmpty()) {
      WeightWorker.enqueueOneTimeWork(context)
      return
    }
    val pendingResult: PendingResult? = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
      try {
        val dependencies: WeightGlanceWidget.Dependencies =
          EntryPointAccessors.fromApplication(context)
        updateRemoteComposeWidgets(
          context = context,
          appWidgetIds = appWidgetIds,
          dataStore = dependencies.syncSettingsDataStore(),
          isSyncRunning = true,
        )
      } finally {
        WeightWorker.enqueueOneTimeWork(context)
        pendingResult?.finish()
      }
    }
  }
}

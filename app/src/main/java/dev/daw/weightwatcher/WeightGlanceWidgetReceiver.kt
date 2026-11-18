package dev.daw.weightwatcher

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.EntryPointAccessors
import dev.daw.weightwatcher.widget.remotecompose.updateRemoteComposeWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WeightGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = WeightGlanceWidget()

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    if (WeightGlanceWidget.useRemoteCompose()) {
      val pendingResult: PendingResult? = goAsync()
      CoroutineScope(Dispatchers.Default).launch {
        try {
          val dependencies: WeightGlanceWidget.Dependencies =
            EntryPointAccessors.fromApplication(context)
          updateRemoteComposeWidgets(
            context = context,
            appWidgetIds = appWidgetIds,
            dataStore = dependencies.syncSettingsDataStore(),
            isSyncRunning = WeightWorker.isSyncing(context).first(),
          )
        } finally {
          WeightWorker.enqueuePeriodicWork(context)
          WeightWorker.enqueueOneTimeWork(context)
          pendingResult?.finish()
        }
      }
      return
    }
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    WeightWorker.enqueuePeriodicWork(context)
    WeightWorker.enqueueOneTimeWork(context)
  }
}

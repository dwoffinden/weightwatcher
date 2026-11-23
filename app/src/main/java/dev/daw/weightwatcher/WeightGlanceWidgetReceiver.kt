package dev.daw.weightwatcher

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
      val appWidgetIds: IntArray = WeightGlanceWidget.getPlacedAppWidgetIds(context)
      if (appWidgetIds.isNotEmpty()) {
        onUpdate(context, AppWidgetManager.getInstance(context), appWidgetIds)
      }
      return
    }
    super.onReceive(context, intent)
  }

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    if (WeightGlanceWidget.useRemoteCompose()) {
      updateRemoteComposeAsync(context, appWidgetIds, enqueueSync = true)
      return
    }
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    WeightWorker.enqueuePeriodicWork(context)
    WeightWorker.enqueueOneTimeWork(context)
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle,
  ) {
    if (WeightGlanceWidget.useRemoteCompose()) {
      updateRemoteComposeAsync(context, intArrayOf(appWidgetId), enqueueSync = false)
      return
    }
    super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
  }

  private fun updateRemoteComposeAsync(
    context: Context,
    appWidgetIds: IntArray,
    enqueueSync: Boolean,
  ) {
    if (!WeightGlanceWidget.useRemoteCompose()) {
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
          isSyncRunning = WeightWorker.isSyncing(context).first(),
        )
      } finally {
        if (enqueueSync) {
          WeightWorker.enqueuePeriodicWork(context)
          WeightWorker.enqueueOneTimeWork(context)
        }
        pendingResult?.finish()
      }
    }
  }
}

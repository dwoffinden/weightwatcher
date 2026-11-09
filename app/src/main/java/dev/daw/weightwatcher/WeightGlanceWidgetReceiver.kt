package dev.daw.weightwatcher

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WeightGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
  @Inject override lateinit var glanceAppWidget: WeightGlanceWidget

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    WeightWorker.enqueuePeriodicWork(context)
    WeightWorker.enqueueOneTimeWork(context)
  }
}

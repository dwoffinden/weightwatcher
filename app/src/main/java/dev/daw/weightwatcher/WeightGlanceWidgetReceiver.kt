package dev.daw.weightwatcher

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WeightGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
  @Inject override lateinit var glanceAppWidget: WeightGlanceWidget

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    WeightWorker.enqueuePeriodicWork(context)
    WeightWorker.enqueueOneTimeWork(context)
  }
}

package dev.daw.weightwatcher

import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WeightGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
  @Inject override lateinit var glanceAppWidget: WeightGlanceWidget
}

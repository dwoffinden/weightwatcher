package dev.daw.weightwatcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.atomic.AtomicInteger
import org.robolectric.Shadows.shadowOf

private val nextAppWidgetId = AtomicInteger(1)

/**
 * Places a [WeightGlanceWidgetReceiver] widget on the (fake) home screen and returns its ID.
 *
 * Each call returns a new ID, even across tests, because Glance caches per-widget state in memory
 * for the whole process, so a reused ID would see an earlier test's state.
 */
fun placeWidget(context: Context): Int {
  val appWidgetId: Int = nextAppWidgetId.getAndIncrement()
  val providerInfo =
    AppWidgetProviderInfo().apply {
      provider = ComponentName(context, WeightGlanceWidgetReceiver::class.java)
    }
  val appWidgetManager = shadowOf(AppWidgetManager.getInstance(context))
  appWidgetManager.addInstalledProvider(providerInfo)
  appWidgetManager.addBoundWidget(appWidgetId, providerInfo)
  return appWidgetId
}

/** Initialises a fresh test WorkManager, whose default initializer the manifest removes. */
fun initializeWorkManager(context: Context) {
  WorkManagerTestInitHelper.initializeTestWorkManager(context)
}

package dev.daw.weightwatcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class WeightWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
  ) {
    val now = TimeSource.Monotonic.markNow()
    val lastMark = lastUpdateMark
    if (lastMark != null && now - lastMark < 2.seconds) {
      Log.i("WeightWidgetProvider", "onUpdate throttled (last update ${now - lastMark} ago)")
      return
    }
    lastUpdateMark = now
    Log.i("WeightWidgetProvider", "onUpdate called, enqueuing work")
    // Enqueue background work instead of doing it on main thread
    WeightWorker.enqueueOneTimeWork(context)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_REFRESH) {
      Log.i("WeightWidgetProvider", "ACTION_REFRESH received, enqueuing work")
      WeightWorker.enqueueOneTimeWork(context)
    }
  }

  companion object {
    const val ACTION_REFRESH = "dev.daw.weightwatcher.ACTION_REFRESH"
    private var lastUpdateMark: ComparableTimeMark? = null

    fun updateAppWidget(context: Context, hoursStale: Long?, hasPermissions: Boolean = true) {
      Log.i(
          "WeightWidgetProvider",
          "Updating ALL widgets: hoursStale=$hoursStale, hasPermissions=$hasPermissions",
      )
      val appWidgetManager = AppWidgetManager.getInstance(context)
      val appWidgetIds =
          appWidgetManager.getAppWidgetIds(ComponentName(context, WeightWidgetProvider::class.java))
      Log.i("WeightWidgetProvider", "Updating ${appWidgetIds.size} widgets")

      for (appWidgetId in appWidgetIds) {
        val views = createRemoteViews(context, hoursStale, hasPermissions)
        appWidgetManager.updateAppWidget(appWidgetId, views)
      }
    }

    fun createRemoteViews(
        context: Context,
        hoursStale: Long?,
        hasPermissions: Boolean,
    ): RemoteViews {
      val views = RemoteViews(context.packageName, R.layout.weight_widget_layout)

      if (!hasPermissions) {
        views.setTextViewText(R.id.staleness_text, "Auth")
        views.setImageViewResource(R.id.status_icon, android.R.drawable.ic_dialog_alert)
      } else if (hoursStale == null) {
        views.setTextViewText(R.id.staleness_text, "None")
        views.setImageViewResource(R.id.status_icon, android.R.drawable.ic_menu_help)
      } else {
        views.setTextViewText(R.id.staleness_text, "${hoursStale}h")
        if (hoursStale <= 20) {
          views.setImageViewResource(R.id.status_icon, R.drawable.ic_check_green)
        } else {
          views.setImageViewResource(R.id.status_icon, R.drawable.ic_close_red)
        }
      }

      // Set up click intent
      val pendingIntent =
          if (!hasPermissions) {
            val appIntent = Intent(context, MainActivity::class.java)
            PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
          } else {
            val refreshIntent =
                Intent(context, WeightWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
          }
      views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
      return views
    }
  }
}

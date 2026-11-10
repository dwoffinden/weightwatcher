package dev.daw.weightwatcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the success callback of a widget pin request and stores the requested data type on the
 * newly placed widget.
 *
 * Not exported: the callback is a [PendingIntent], which is delivered as this app.
 */
class WidgetPinReceiver : BroadcastReceiver() {

  companion object {
    /**
     * Returns the callback for [AppWidgetManager.requestPinAppWidget], which the launcher fills in
     * with [AppWidgetManager.EXTRA_APPWIDGET_ID] once the widget is placed.
     */
    fun createSuccessCallback(context: Context, dataType: DataType): PendingIntent {
      val intent =
        Intent(context, WidgetPinReceiver::class.java)
          .putExtra(MainActivity.EXTRA_DATA_TYPE, dataType.number)
      // PendingIntents that differ only in extras are the same PendingIntent, so with
      // FLAG_UPDATE_CURRENT a pin request for one type would overwrite another type's pending
      // extras. A distinct request code per type keeps them separate.
      // FLAG_MUTABLE is needed so the launcher can add EXTRA_APPWIDGET_ID.
      return PendingIntent.getBroadcast(
        context,
        /* requestCode= */ dataType.number,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
      )
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    val appWidgetId: Int =
      intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    val dataType: DataType =
      WeightGlanceWidget.resolveDataType(intent.getIntExtra(MainActivity.EXTRA_DATA_TYPE, 0))
        ?: run {
          Log.w("WidgetPinReceiver", "Ignoring pin callback without a valid data type")
          return
        }
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
      try {
        WeightGlanceWidget().configure(context, appWidgetId, dataType)
      } catch (e: IllegalArgumentException) {
        // The widget was removed before the callback arrived (or the ID is missing).
        Log.w("WidgetPinReceiver", "Couldn't configure widget $appWidgetId", e)
      } finally {
        pendingResult.finish()
      }
    }
  }
}

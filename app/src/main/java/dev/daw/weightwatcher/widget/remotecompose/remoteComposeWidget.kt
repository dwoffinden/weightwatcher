package dev.daw.weightwatcher.widget.remotecompose

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.google.common.collect.ImmutableSet
import dev.daw.weightwatcher.DataType
import dev.daw.weightwatcher.MainActivity
import dev.daw.weightwatcher.SyncSettings
import dev.daw.weightwatcher.WeightGlanceWidget
import dev.daw.weightwatcher.getGlanceIdOrNull
import dev.daw.weightwatcher.putDataType
import dev.daw.weightwatcher.widget.WidgetClickAction
import dev.daw.weightwatcher.widget.WidgetState
import dev.daw.weightwatcher.widget.resolveWidgetState
import kotlinx.coroutines.flow.first

private const val TAG = "RemoteComposeWidget"
private const val CONFIGURE_REQUEST_CODE_OFFSET = 10_000

/**
 * Publishes standalone `RemoteViews(DrawInstructions)` directly to [AppWidgetManager] on Android
 * 16+, bypassing Glance's `SessionWorker` (whose 45-second idle session ignores `updateAll` calls
 * when `provideGlance` returns without `provideContent`).
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
internal suspend fun updateRemoteComposeWidgets(
  context: Context,
  appWidgetIds: IntArray,
  dataStore: DataStore<SyncSettings>,
  isSyncRunning: Boolean,
) {
  if (appWidgetIds.isEmpty()) {
    return
  }
  val appWidgetManager = AppWidgetManager.getInstance(context)
  val glanceAppWidgetManager = GlanceAppWidgetManager(context)
  val settings: SyncSettings = dataStore.data.first()
  for (appWidgetId in appWidgetIds) {
    val glanceId = glanceAppWidgetManager.getGlanceIdOrNull(appWidgetId)
    if (glanceId == null) {
      Log.w(TAG, "Skipping invalid appWidgetId $appWidgetId")
      continue
    }
    val dataTypes: ImmutableSet<DataType> = WeightGlanceWidget.getWidgetDataTypes(context, glanceId)
    val remoteViews: RemoteViews =
      buildRemoteComposeWidgetViews(
        context = context,
        appWidgetId = appWidgetId,
        dataTypes = dataTypes,
        settings = settings,
        isSyncRunning = isSyncRunning,
      )
    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
  }
}

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
internal suspend fun buildRemoteComposeWidgetViews(
  context: Context,
  appWidgetId: Int,
  dataTypes: ImmutableSet<DataType>,
  settings: SyncSettings,
  isSyncRunning: Boolean,
): RemoteViews {
  val state: WidgetState =
    resolveWidgetState(
      appWidgetId = appWidgetId,
      dataTypes = dataTypes,
      settings = settings,
      isSyncRunning = isSyncRunning,
    )
  return captureWidgetRemoteViews(
    context = context,
    state = state,
    pendingIntentForAction = { action -> action.toPendingIntent(context) },
  )
}

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
private fun WidgetClickAction.toPendingIntent(context: Context): PendingIntent =
  when (this) {
    is WidgetClickAction.ConfigureWidget ->
      createMainActivityPendingIntent(
        context = context,
        requestCode = CONFIGURE_REQUEST_CODE_OFFSET + appWidgetId,
        intent =
          Intent(context, MainActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
      )
    is WidgetClickAction.RequestPermissions ->
      createMainActivityPendingIntent(
        context = context,
        requestCode = dataType.ordinal,
        intent = Intent(context, MainActivity::class.java).putDataType(dataType),
      )
    WidgetClickAction.Refresh -> WidgetRefreshReceiver.createPendingIntent(context)
  }

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
private fun createMainActivityPendingIntent(
  context: Context,
  requestCode: Int,
  intent: Intent,
): PendingIntent {
  val options =
    ActivityOptions.makeBasic()
      .setPendingIntentCreatorBackgroundActivityStartMode(
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
      )
      .toBundle()
  return PendingIntent.getActivity(
    context,
    requestCode,
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    options,
  )
}

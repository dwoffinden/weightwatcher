package dev.daw.weightwatcher.widget.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.daw.weightwatcher.AppClock
import dev.daw.weightwatcher.DataType
import dev.daw.weightwatcher.MainActivity
import dev.daw.weightwatcher.SyncSettings
import dev.daw.weightwatcher.WeightGlanceWidget
import dev.daw.weightwatcher.dataTypeActionParameters
import dev.daw.weightwatcher.getDataType
import dev.daw.weightwatcher.minus
import dev.daw.weightwatcher.widget.WidgetClickAction
import dev.daw.weightwatcher.widget.WidgetState
import dev.daw.weightwatcher.widget.resolveWidgetState
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private val EXTRA_APPWIDGET_ID_KEY: ActionParameters.Key<Int> =
  ActionParameters.Key(AppWidgetManager.EXTRA_APPWIDGET_ID)

internal suspend fun GlanceAppWidget.provideGlanceWidgetContent(
  context: Context,
  appWidgetId: Int,
  dataStore: DataStore<SyncSettings>,
  clock: AppClock,
  isSyncRunningFlow: Flow<Boolean>,
) {
  val initialSettings: SyncSettings = dataStore.data.first()
  val initialIsSyncRunning: Boolean = isSyncRunningFlow.first()
  val isPlacedWidget: Boolean = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID

  provideContent {
    val prefs: Preferences = currentState()
    val dataType: DataType? = prefs.getDataType()
    val settings: SyncSettings by dataStore.data.collectAsState(initial = initialSettings)
    val isSyncRunning: Boolean by isSyncRunningFlow.collectAsState(initial = initialIsSyncRunning)
    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = appWidgetId,
        dataType = dataType,
        settings = settings,
        isSyncRunning = isSyncRunning,
      )

    GlanceTheme {
      GlanceWidgetContent(
        context = context,
        state = state,
        clock = clock,
        useChronometer = isPlacedWidget,
      )
    }
  }
}

@Composable
private fun GlanceWidgetContent(
  context: Context,
  state: WidgetState,
  clock: AppClock,
  useChronometer: Boolean,
) {
  val backgroundColor =
    ColorProvider(
      day = dynamicLightColorScheme(context).surfaceContainer.copy(alpha = 0.8f),
      night = dynamicDarkColorScheme(context).surfaceContainer.copy(alpha = 0.8f),
    )

  Column(
    modifier =
      GlanceModifier.fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)
        .background(backgroundColor)
        .padding(8.dp)
        .clickable(state.clickAction.toGlanceAction()),
    verticalAlignment = Alignment.CenterVertically,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val staleness: Duration? =
      if (state is WidgetState.Active) clock.instant() - state.lastRecordTime else null

    val iconTint =
      when (state) {
        is WidgetState.Unconfigured,
        is WidgetState.Syncing,
        is WidgetState.NoRecords -> GlanceTheme.colors.onSurfaceVariant
        is WidgetState.Unauthorized -> GlanceTheme.colors.error
        is WidgetState.Active ->
          if (staleness != null && staleness > WeightGlanceWidget.STALE_THRESHOLD) {
            GlanceTheme.colors.error
          } else {
            GlanceTheme.colors.primary
          }
      }

    Image(
      provider = ImageProvider(state.iconRes),
      contentDescription = state.contentDescription,
      colorFilter = ColorFilter.tint(iconTint),
      modifier = GlanceModifier.size(32.dp),
    )
    Spacer(modifier = GlanceModifier.height(4.dp))
    if (useChronometer && staleness != null && staleness < WeightGlanceWidget.HOURS_THRESHOLD) {
      AndroidRemoteViews(
        remoteViews = createChronometerRemoteViews(context, staleness, clock.elapsedRealtime()),
        modifier = GlanceModifier.wrapContentSize(),
      )
    } else {
      val text: String =
        if (staleness != null) {
          WeightGlanceWidget.formatStaleness(staleness)
        } else {
          when (state) {
            is WidgetState.Unconfigured,
            is WidgetState.Syncing -> "Sync..."
            is WidgetState.Unauthorized -> "Auth"
            is WidgetState.NoRecords -> "None"
            is WidgetState.Active -> error("Unreachable: Active always computes staleness")
          }
        }
      Text(
        text = text,
        style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
      )
    }
  }
}

private fun WidgetClickAction.toGlanceAction(): Action =
  when (this) {
    is WidgetClickAction.ConfigureWidget ->
      actionStartActivity<MainActivity>(actionParametersOf(EXTRA_APPWIDGET_ID_KEY to appWidgetId))
    is WidgetClickAction.RequestPermissions ->
      actionStartActivity<MainActivity>(dataTypeActionParameters(dataType))
    WidgetClickAction.Refresh -> actionRunCallback<RefreshCallback>()
  }

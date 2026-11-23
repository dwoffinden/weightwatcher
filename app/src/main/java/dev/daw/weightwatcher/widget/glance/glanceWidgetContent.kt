package dev.daw.weightwatcher.widget.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
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
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.common.collect.ImmutableSet
import dev.daw.weightwatcher.AppClock
import dev.daw.weightwatcher.DataType
import dev.daw.weightwatcher.MainActivity
import dev.daw.weightwatcher.R
import dev.daw.weightwatcher.SyncSettings
import dev.daw.weightwatcher.WeightGlanceWidget
import dev.daw.weightwatcher.dataTypeActionParameters
import dev.daw.weightwatcher.getWidgetDataTypes
import dev.daw.weightwatcher.minus
import dev.daw.weightwatcher.widget.WidgetClickAction
import dev.daw.weightwatcher.widget.WidgetItemState
import dev.daw.weightwatcher.widget.WidgetState
import dev.daw.weightwatcher.widget.resolveWidgetState
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private val EXTRA_APPWIDGET_ID_KEY: ActionParameters.Key<Int> =
  ActionParameters.Key(AppWidgetManager.EXTRA_APPWIDGET_ID)

private val WIDGET_BACKGROUND_COLOR: ColorProvider = ColorProvider(R.color.widget_background)
private val WIDGET_PRIMARY_COLOR: ColorProvider = ColorProvider(R.color.widget_primary)
private val WIDGET_ERROR_COLOR: ColorProvider = ColorProvider(R.color.widget_error)
private val WIDGET_ON_SURFACE_COLOR: ColorProvider = ColorProvider(R.color.widget_on_surface)
private val WIDGET_ON_SURFACE_VARIANT_COLOR: ColorProvider =
  ColorProvider(R.color.widget_on_surface_variant)

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
    val dataTypes: ImmutableSet<DataType> = prefs.getWidgetDataTypes()
    val settings: SyncSettings by dataStore.data.collectAsState(initial = initialSettings)
    val isSyncRunning: Boolean by isSyncRunningFlow.collectAsState(initial = initialIsSyncRunning)
    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = appWidgetId,
        dataTypes = dataTypes,
        settings = settings,
        isSyncRunning = isSyncRunning,
      )

    GlanceWidgetContent(
      context = context,
      state = state,
      clock = clock,
      useChronometer = isPlacedWidget,
    )
  }
}

@Composable
internal fun GlanceWidgetContent(
  context: Context,
  state: WidgetState,
  clock: AppClock,
  useChronometer: Boolean,
) {
  val cardModifier: GlanceModifier =
    GlanceModifier.fillMaxSize()
      .appWidgetBackground()
      .cornerRadius(android.R.dimen.system_app_widget_background_radius)
      .background(WIDGET_BACKGROUND_COLOR)

  when (state) {
    is WidgetState.Unconfigured -> {
      Column(
        modifier = cardModifier.padding(8.dp).clickable(state.clickAction.toGlanceAction()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Image(
          provider = ImageProvider(state.iconRes),
          contentDescription = state.contentDescription,
          colorFilter = ColorFilter.tint(WIDGET_ON_SURFACE_VARIANT_COLOR),
          modifier = GlanceModifier.size(32.dp),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
          text = "Sync...",
          style = TextStyle(fontWeight = FontWeight.Bold, color = WIDGET_ON_SURFACE_COLOR),
        )
      }
    }
    is WidgetState.Configured -> {
      if (state.items.size == 1) {
        val item: WidgetItemState = state.items.single()
        GlanceWidgetTile(
          context = context,
          item = item,
          clock = clock,
          useChronometer = useChronometer,
          iconSize = 32.dp,
          modifier = cardModifier.padding(8.dp).clickable(item.clickAction.toGlanceAction()),
        )
      } else {
        val iconSize: Dp = if (state.items.size <= 3) 28.dp else 24.dp
        Row(
          modifier = cardModifier.padding(horizontal = 4.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          for (item in state.items) {
            GlanceWidgetTile(
              context = context,
              item = item,
              clock = clock,
              useChronometer = useChronometer,
              iconSize = iconSize,
              modifier =
                GlanceModifier.defaultWeight()
                  .fillMaxHeight()
                  .clickable(item.clickAction.toGlanceAction()),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GlanceWidgetTile(
  context: Context,
  item: WidgetItemState,
  clock: AppClock,
  useChronometer: Boolean,
  iconSize: Dp,
  modifier: GlanceModifier,
) {
  val staleness: Duration? =
    if (item is WidgetItemState.Active) clock.instant() - item.lastRecordTime else null
  val iconTint: ColorProvider =
    when (item) {
      is WidgetItemState.Syncing,
      is WidgetItemState.NoRecords -> WIDGET_ON_SURFACE_VARIANT_COLOR
      is WidgetItemState.Unauthorized -> WIDGET_ERROR_COLOR
      is WidgetItemState.Active ->
        if (staleness != null && staleness > WeightGlanceWidget.STALE_THRESHOLD) {
          WIDGET_ERROR_COLOR
        } else {
          WIDGET_PRIMARY_COLOR
        }
    }

  Column(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Image(
      provider = ImageProvider(item.iconRes),
      contentDescription = item.contentDescription,
      colorFilter = ColorFilter.tint(iconTint),
      modifier = GlanceModifier.size(iconSize),
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
          when (item) {
            is WidgetItemState.Syncing -> "Sync..."
            is WidgetItemState.Unauthorized -> "Auth"
            is WidgetItemState.NoRecords -> "None"
            is WidgetItemState.Active -> error("Unreachable: Active always computes staleness")
          }
        }
      Text(
        text = text,
        style = TextStyle(fontWeight = FontWeight.Bold, color = WIDGET_ON_SURFACE_COLOR),
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

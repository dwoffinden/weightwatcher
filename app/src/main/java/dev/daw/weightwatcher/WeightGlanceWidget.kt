package dev.daw.weightwatcher

import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.daw.weightwatcher.SyncSettings.Status
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
import kotlinx.coroutines.flow.first

/**
 * The home screen widget.
 *
 * Has no constructor dependencies because Glance instantiates [WeightGlanceWidgetReceiver] by
 * reflection (to map receivers to widgets) and reads its [GlanceAppWidgetReceiver.glanceAppWidget]
 * without injecting it. [provideGlance] gets its dependencies through [Dependencies] instead.
 */
class WeightGlanceWidget : GlanceAppWidget() {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface Dependencies {
    fun syncSettingsDataStore(): DataStore<SyncSettings>

    fun clock(): InstantSource
  }

  companion object {
    private val STALE_THRESHOLD: Duration = 20.hours
    private val DAYS_THRESHOLD: Duration = 72.hours

    internal fun formatStaleness(staleness: Duration): String =
      if (staleness < DAYS_THRESHOLD) "${staleness.toHours()}h" else "${staleness.toDays()}d"

    internal fun isSyncing(status: Status): Boolean =
      status == Status.SYNCING || status == Status.STATUS_UNSPECIFIED
  }

  override val stateDefinition: GlanceStateDefinition<*>? = null

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val dependencies: Dependencies = EntryPointAccessors.fromApplication(context)
    val dataStore: DataStore<SyncSettings> = dependencies.syncSettingsDataStore()
    val clock: InstantSource = dependencies.clock()
    val initialState: SyncSettings = dataStore.data.first()
    provideContent {
      val state: SyncSettings by dataStore.data.collectAsState(initial = initialState)
      val lastWeightTime: Instant? = state.lastWeightTimeOrNull?.toInstant()
      val status: Status = state.status

      GlanceTheme {
        WidgetContent(
          context,
          staleness = if (lastWeightTime != null) clock.instant() - lastWeightTime else null,
          hasPermissions = status != Status.NO_PERMISSIONS,
          isSyncing = isSyncing(status),
        )
      }
    }
  }

  @Composable
  private fun WidgetContent(
    context: Context,
    staleness: Duration?,
    hasPermissions: Boolean,
    isSyncing: Boolean,
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
          .clickable(
            if (!hasPermissions) {
              actionStartActivity<MainActivity>()
            } else {
              actionRunCallback<RefreshCallback>()
            }
          ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val iconRes =
        when {
          isSyncing -> R.drawable.ic_sync
          !hasPermissions -> R.drawable.ic_warning
          staleness == null -> R.drawable.ic_help
          staleness <= STALE_THRESHOLD -> R.drawable.ic_check
          else -> R.drawable.ic_close
        }

      val iconTint =
        when {
          isSyncing || staleness == null -> GlanceTheme.colors.onSurfaceVariant
          !hasPermissions || staleness > STALE_THRESHOLD -> GlanceTheme.colors.error
          else -> GlanceTheme.colors.primary
        }

      val text =
        when {
          isSyncing -> "Sync..."
          !hasPermissions -> "Auth"
          staleness == null -> "None"
          else -> formatStaleness(staleness)
        }

      Image(
        provider = ImageProvider(iconRes),
        contentDescription = "Status",
        colorFilter = ColorFilter.tint(iconTint),
        modifier = GlanceModifier.size(32.dp),
      )
      Spacer(modifier = GlanceModifier.height(4.dp))
      Text(
        text = text,
        style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
      )
    }
  }
}

class RefreshCallback : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    WeightWorker.enqueueOneTimeWork(context)
  }
}

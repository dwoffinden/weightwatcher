package dev.daw.weightwatcher

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class WeightGlanceWidget : GlanceAppWidget() {

  override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent {
      val prefs = currentState<Preferences>()
      val hoursStale = if (prefs.contains(KEY_HOURS_STALE)) prefs[KEY_HOURS_STALE] else null
      val hasPermissions = prefs[KEY_HAS_PERMISSIONS] ?: true
      val isSyncing = prefs[KEY_IS_SYNCING] ?: false

      WidgetContent(context, hoursStale, hasPermissions, isSyncing)
    }
  }

  @Composable
  private fun WidgetContent(
      context: Context,
      hoursStale: Long?,
      hasPermissions: Boolean,
      isSyncing: Boolean,
  ) {
    Column(
        modifier =
            GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
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
            isSyncing -> android.R.drawable.stat_notify_sync
            !hasPermissions -> android.R.drawable.ic_dialog_alert
            hoursStale == null -> android.R.drawable.ic_menu_help
            hoursStale <= 20 -> R.drawable.ic_check_green
            else -> R.drawable.ic_close_red
          }

      val text =
          when {
            isSyncing -> "Sync..."
            !hasPermissions -> "Auth"
            hoursStale == null -> "None"
            else -> "${hoursStale}h"
          }

      Image(
          provider = ImageProvider(iconRes),
          contentDescription = "Status",
          modifier = GlanceModifier.size(32.dp),
      )
      Spacer(modifier = GlanceModifier.height(4.dp))
      Text(
          text = text,
          style =
              TextStyle(
                  fontWeight = FontWeight.Bold,
                  color = ColorProvider(Color.White),
              ),
      )
    }
  }

  companion object {
    val KEY_HOURS_STALE = longPreferencesKey("hours_stale")
    val KEY_HAS_PERMISSIONS = booleanPreferencesKey("has_permissions")
    val KEY_IS_SYNCING = booleanPreferencesKey("is_syncing")
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

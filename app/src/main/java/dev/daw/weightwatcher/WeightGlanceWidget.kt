package dev.daw.weightwatcher

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.io.File
import java.time.Instant

class WeightGlanceWidget : GlanceAppWidget() {
  override val stateDefinition: GlanceStateDefinition<WidgetState> = WidgetStateDefinition

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent {
      val state = currentState<WidgetState>()
      val lastWeightTime = state.lastWeightTimeOrNull?.toInstant()
      val status = state.status

      WidgetContent(
          context,
          lastWeightTime,
          hasPermissions = status != WidgetState.Status.NO_PERMISSIONS,
          isSyncing = status == WidgetState.Status.SYNCING,
      )
    }
  }

  @Composable
  private fun WidgetContent(
      context: Context,
      lastWeightTime: Instant?,
      hasPermissions: Boolean,
      isSyncing: Boolean,
  ) {
    val hoursStale = lastWeightTime?.let { (Instant.now() - it).toHours() }

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
    /** Updates the state for all instances of this widget. */
    suspend fun updateAllEntries(
        context: Context,
        lastWeightTime: Instant?,
        hasPermissions: Boolean,
        isSyncing: Boolean,
    ) {
      val manager = GlanceAppWidgetManager(context)
      val glanceIds = manager.getGlanceIds(WeightGlanceWidget::class.java)
      glanceIds.forEach { glanceId ->
        updateAppWidgetState(context, WidgetStateDefinition, glanceId) { state: WidgetState ->
          state.copy {
            if (lastWeightTime != null) {
              this.lastWeightTime = lastWeightTime.toTimestamp()
            } else {
              clearLastWeightTime()
            }
            status =
                when {
                  isSyncing -> WidgetState.Status.SYNCING
                  !hasPermissions -> WidgetState.Status.NO_PERMISSIONS
                  else -> WidgetState.Status.OK
                }
          }
        }
      }
      WeightGlanceWidget().updateAll(context)
    }

    /** Resets the state for all instances of this widget. */
    suspend fun resetAll(context: Context) {
      val manager = GlanceAppWidgetManager(context)
      val glanceIds = manager.getGlanceIds(WeightGlanceWidget::class.java)
      glanceIds.forEach { glanceId ->
        updateAppWidgetState(context, WidgetStateDefinition, glanceId) {
          WidgetState.getDefaultInstance()
        }
      }
      WeightGlanceWidget().updateAll(context)
    }

    /**
     * Returns a [WidgetState] object suitable for
     * [GlanceAppWidgetManager.requestPinGlanceAppWidget].
     */
    fun createMetadata(
        lastWeightTime: Instant?,
        hasPermissions: Boolean = true,
        isSyncing: Boolean = false,
    ): WidgetState =
        WidgetState.newBuilder()
            .apply {
              if (lastWeightTime != null) {
                setLastWeightTime(lastWeightTime.toTimestamp())
              }
              setStatus(
                  when {
                    isSyncing -> WidgetState.Status.SYNCING
                    !hasPermissions -> WidgetState.Status.NO_PERMISSIONS
                    else -> WidgetState.Status.OK
                  }
              )
            }
            .build()
  }
}

object WidgetStateDefinition : GlanceStateDefinition<WidgetState> {
  override suspend fun getDataStore(context: Context, fileKey: String): DataStore<WidgetState> =
      DataStoreFactory.create(
          serializer = ProtoSerializer(WidgetState.getDefaultInstance(), WidgetState.parser()),
          produceFile = { getLocation(context, fileKey) },
      )

  override fun getLocation(context: Context, fileKey: String): File =
      context.dataStoreFile("widget_state_$fileKey.pb")
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

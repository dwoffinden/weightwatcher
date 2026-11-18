package dev.daw.weightwatcher.widget

import androidx.annotation.DrawableRes
import dev.daw.weightwatcher.DataType
import dev.daw.weightwatcher.DataTypeState
import dev.daw.weightwatcher.DataTypeState.Status
import dev.daw.weightwatcher.R
import dev.daw.weightwatcher.SyncSettings
import dev.daw.weightwatcher.WeightGlanceWidget
import dev.daw.weightwatcher.lastRecordTimeOrNull
import dev.daw.weightwatcher.stateFor
import dev.daw.weightwatcher.toInstant
import java.time.Instant

/** Action taken when the user taps the widget. */
internal sealed interface WidgetClickAction {
  data class ConfigureWidget(val appWidgetId: Int) : WidgetClickAction

  data class RequestPermissions(val dataType: DataType) : WidgetClickAction

  data object Refresh : WidgetClickAction
}

/** Shared display and interaction state rendered by both the Glance and RemoteCompose backends. */
internal sealed interface WidgetState {
  @get:DrawableRes val iconRes: Int
  val contentDescription: String
  val clickAction: WidgetClickAction

  data class Unconfigured(val appWidgetId: Int) : WidgetState {
    override val iconRes: Int = R.drawable.ic_sync
    override val contentDescription: String = "Setting up"
    override val clickAction: WidgetClickAction = WidgetClickAction.ConfigureWidget(appWidgetId)
  }

  data class Syncing(val dataType: DataType, val hasPermissions: Boolean) : WidgetState {
    override val iconRes: Int = R.drawable.ic_sync
    override val contentDescription: String = dataType.displayName
    override val clickAction: WidgetClickAction =
      if (hasPermissions) {
        WidgetClickAction.Refresh
      } else {
        WidgetClickAction.RequestPermissions(dataType)
      }
  }

  data class Unauthorized(val dataType: DataType) : WidgetState {
    override val iconRes: Int = R.drawable.ic_warning
    override val contentDescription: String = dataType.displayName
    override val clickAction: WidgetClickAction = WidgetClickAction.RequestPermissions(dataType)
  }

  data class NoRecords(val dataType: DataType) : WidgetState {
    override val iconRes: Int = dataType.iconRes
    override val contentDescription: String = dataType.displayName
    override val clickAction: WidgetClickAction = WidgetClickAction.Refresh
  }

  data class Active(val dataType: DataType, val lastRecordTime: Instant) : WidgetState {
    override val iconRes: Int = dataType.iconRes
    override val contentDescription: String = dataType.displayName
    override val clickAction: WidgetClickAction = WidgetClickAction.Refresh
  }
}

internal fun resolveWidgetState(
  appWidgetId: Int,
  dataType: DataType?,
  settings: SyncSettings,
  isSyncRunning: Boolean,
): WidgetState {
  if (dataType == null) {
    return WidgetState.Unconfigured(appWidgetId)
  }
  val typeState: DataTypeState = settings.stateFor(dataType) ?: DataTypeState.getDefaultInstance()
  val hasPermissions: Boolean = typeState.status != Status.NO_PERMISSIONS
  val lastRecordTime: Instant? = typeState.lastRecordTimeOrNull?.toInstant()
  return when {
    WeightGlanceWidget.isSyncing(typeState.status, isSyncRunning) ->
      WidgetState.Syncing(dataType, hasPermissions)
    !hasPermissions -> WidgetState.Unauthorized(dataType)
    lastRecordTime == null -> WidgetState.NoRecords(dataType)
    else -> WidgetState.Active(dataType, lastRecordTime)
  }
}

package dev.daw.weightwatcher.widget

import androidx.annotation.DrawableRes
import com.google.common.collect.ImmutableSet
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

/** Action taken when the user taps a widget or an individual data type tile. */
internal sealed interface WidgetClickAction {
  data class ConfigureWidget(val appWidgetId: Int) : WidgetClickAction

  data class RequestPermissions(val dataType: DataType) : WidgetClickAction

  data object Refresh : WidgetClickAction
}

/** Display and interaction state for a single data type tile inside a configured widget. */
internal sealed interface WidgetItemState {
  val dataType: DataType
  @get:DrawableRes val iconRes: Int
  val contentDescription: String
  val clickAction: WidgetClickAction

  data class Syncing(override val dataType: DataType, val hasPermissions: Boolean) :
    WidgetItemState {
    override val iconRes: Int = R.drawable.ic_sync
    override val contentDescription: String = dataType.displayName
    override val clickAction: WidgetClickAction =
      if (hasPermissions) {
        WidgetClickAction.Refresh
      } else {
        WidgetClickAction.RequestPermissions(dataType)
      }
  }

  data class Unauthorized(override val dataType: DataType) : WidgetItemState {
    override val iconRes: Int = R.drawable.ic_warning
    override val contentDescription: String = dataType.displayName
    override val clickAction: WidgetClickAction = WidgetClickAction.RequestPermissions(dataType)
  }

  data class NoRecords(override val dataType: DataType) : WidgetItemState {
    override val iconRes: Int = dataType.iconRes
    override val contentDescription: String = dataType.displayName
    override val clickAction: WidgetClickAction = WidgetClickAction.Refresh
  }

  data class Active(
    override val dataType: DataType,
    val lastRecordTime: Instant,
    val originPackage: String? = null,
  ) : WidgetItemState {
    override val iconRes: Int = dataType.iconRes
    override val contentDescription: String = dataType.displayName
    override val clickAction: WidgetClickAction = WidgetClickAction.Refresh
  }
}

/** Shared display and interaction state rendered by both the Glance and RemoteCompose backends. */
internal sealed interface WidgetState {
  data class Unconfigured(val appWidgetId: Int) : WidgetState {
    @get:DrawableRes val iconRes: Int = R.drawable.ic_sync
    val contentDescription: String = "Setting up"
    val clickAction: WidgetClickAction = WidgetClickAction.ConfigureWidget(appWidgetId)
  }

  data class Configured(val items: List<WidgetItemState>) : WidgetState {
    init {
      require(items.isNotEmpty()) { "Configured widget must have at least one item" }
    }
  }
}

internal fun resolveWidgetState(
  appWidgetId: Int,
  dataTypes: ImmutableSet<DataType>,
  settings: SyncSettings,
  isSyncRunning: Boolean,
): WidgetState {
  if (dataTypes.isEmpty()) {
    return WidgetState.Unconfigured(appWidgetId)
  }
  return WidgetState.Configured(
    items = dataTypes.map { dataType -> resolveWidgetItemState(dataType, settings, isSyncRunning) }
  )
}

private fun resolveWidgetItemState(
  dataType: DataType,
  settings: SyncSettings,
  isSyncRunning: Boolean,
): WidgetItemState {
  val typeState: DataTypeState = settings.stateFor(dataType) ?: DataTypeState.getDefaultInstance()
  val hasPermissions: Boolean = typeState.status != Status.NO_PERMISSIONS
  val lastRecordTime: Instant? = typeState.lastRecordTimeOrNull?.toInstant()
  val originPackage: String? = typeState.lastRecordOriginPackage.takeIf { it.isNotEmpty() }
  return when {
    WeightGlanceWidget.isSyncing(typeState.status, isSyncRunning) ->
      WidgetItemState.Syncing(dataType, hasPermissions)
    !hasPermissions -> WidgetItemState.Unauthorized(dataType)
    lastRecordTime == null -> WidgetItemState.NoRecords(dataType)
    else -> WidgetItemState.Active(dataType, lastRecordTime, originPackage)
  }
}

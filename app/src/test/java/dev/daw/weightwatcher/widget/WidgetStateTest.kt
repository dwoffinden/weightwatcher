package dev.daw.weightwatcher.widget

import com.google.common.truth.Truth.assertThat
import dev.daw.weightwatcher.DataType
import dev.daw.weightwatcher.DataTypeState.Status
import dev.daw.weightwatcher.R
import dev.daw.weightwatcher.SyncSettings
import dev.daw.weightwatcher.dataTypeState
import dev.daw.weightwatcher.setStatesByType
import dev.daw.weightwatcher.syncSettings
import dev.daw.weightwatcher.toTimestamp
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class WidgetStateTest {

  @Test
  fun resolveWidgetState_unconfigured_returnsUnconfiguredWithConfigureAction() {
    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 77,
        dataType = null,
        settings = SyncSettings.getDefaultInstance(),
        isSyncRunning = false,
      )

    assertThat(state).isEqualTo(WidgetState.Unconfigured(appWidgetId = 77))
    assertThat(state.iconRes).isEqualTo(R.drawable.ic_sync)
    assertThat(state.contentDescription).isEqualTo("Setting up")
    assertThat(state.clickAction).isEqualTo(WidgetClickAction.ConfigureWidget(appWidgetId = 77))
  }

  @Test
  fun resolveWidgetState_neverSynced_returnsSyncingWithRefreshAction() {
    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataType = DataType.WEIGHT,
        settings = SyncSettings.getDefaultInstance(),
        isSyncRunning = false,
      )

    assertThat(state).isEqualTo(WidgetState.Syncing(DataType.WEIGHT, hasPermissions = true))
    assertThat(state.iconRes).isEqualTo(R.drawable.ic_sync)
    assertThat(state.clickAction).isEqualTo(WidgetClickAction.Refresh)
  }

  @Test
  fun resolveWidgetState_syncRunningWithoutPermissions_keepsPermissionRequestAction() {
    val settings: SyncSettings = syncSettings {
      setStatesByType(mapOf(DataType.SLEEP to dataTypeState { status = Status.NO_PERMISSIONS }))
    }

    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataType = DataType.SLEEP,
        settings = settings,
        isSyncRunning = true,
      )

    assertThat(state).isEqualTo(WidgetState.Syncing(DataType.SLEEP, hasPermissions = false))
    assertThat(state.clickAction).isEqualTo(WidgetClickAction.RequestPermissions(DataType.SLEEP))
  }

  @Test
  fun resolveWidgetState_noPermissions_returnsUnauthorized() {
    val settings: SyncSettings = syncSettings {
      setStatesByType(mapOf(DataType.SLEEP to dataTypeState { status = Status.NO_PERMISSIONS }))
    }

    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataType = DataType.SLEEP,
        settings = settings,
        isSyncRunning = false,
      )

    assertThat(state).isEqualTo(WidgetState.Unauthorized(DataType.SLEEP))
    assertThat(state.iconRes).isEqualTo(R.drawable.ic_warning)
    assertThat(state.clickAction).isEqualTo(WidgetClickAction.RequestPermissions(DataType.SLEEP))
  }

  @Test
  fun resolveWidgetState_okWithoutRecord_returnsNoRecords() {
    val settings: SyncSettings = syncSettings {
      setStatesByType(mapOf(DataType.STEPS to dataTypeState { status = Status.OK }))
    }

    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataType = DataType.STEPS,
        settings = settings,
        isSyncRunning = false,
      )

    assertThat(state).isEqualTo(WidgetState.NoRecords(DataType.STEPS))
    assertThat(state.iconRes).isEqualTo(DataType.STEPS.iconRes)
    assertThat(state.clickAction).isEqualTo(WidgetClickAction.Refresh)
  }

  @Test
  fun resolveWidgetState_okWithRecord_returnsActive() {
    val lastRecordTime = Instant.parse("2026-01-01T00:00:00Z")
    val settings: SyncSettings = syncSettings {
      setStatesByType(
        mapOf(
          DataType.WEIGHT to
            dataTypeState {
              status = Status.OK
              this.lastRecordTime = lastRecordTime.toTimestamp()
            }
        )
      )
    }

    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataType = DataType.WEIGHT,
        settings = settings,
        isSyncRunning = false,
      )

    assertThat(state).isEqualTo(WidgetState.Active(DataType.WEIGHT, lastRecordTime))
    assertThat(state.iconRes).isEqualTo(DataType.WEIGHT.iconRes)
    assertThat(state.contentDescription).isEqualTo(DataType.WEIGHT.displayName)
    assertThat(state.clickAction).isEqualTo(WidgetClickAction.Refresh)
  }
}

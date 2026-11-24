package dev.daw.weightwatcher.widget

import com.google.common.collect.ImmutableSet
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
        dataTypes = ImmutableSet.of(),
        settings = SyncSettings.getDefaultInstance(),
        isSyncRunning = false,
      )

    val expected = WidgetState.Unconfigured(appWidgetId = 77)
    assertThat(state).isEqualTo(expected)
    assertThat(expected.iconRes).isEqualTo(R.drawable.ic_sync)
    assertThat(expected.contentDescription).isEqualTo("Setting up")
    assertThat(expected.clickAction).isEqualTo(WidgetClickAction.ConfigureWidget(appWidgetId = 77))
  }

  @Test
  fun resolveWidgetState_neverSynced_returnsSyncingWithRefreshAction() {
    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataTypes = ImmutableSet.of(DataType.WEIGHT),
        settings = SyncSettings.getDefaultInstance(),
        isSyncRunning = false,
      )

    val expectedItem = WidgetItemState.Syncing(DataType.WEIGHT, hasPermissions = true)
    assertThat(state).isEqualTo(WidgetState.Configured(listOf(expectedItem)))
    assertThat(expectedItem.iconRes).isEqualTo(R.drawable.ic_sync)
    assertThat(expectedItem.clickAction).isEqualTo(WidgetClickAction.Refresh)
  }

  @Test
  fun resolveWidgetState_syncRunningWithoutPermissions_keepsPermissionRequestAction() {
    val settings: SyncSettings = syncSettings {
      setStatesByType(mapOf(DataType.SLEEP to dataTypeState { status = Status.NO_PERMISSIONS }))
    }

    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataTypes = ImmutableSet.of(DataType.SLEEP),
        settings = settings,
        isSyncRunning = true,
      )

    val expectedItem = WidgetItemState.Syncing(DataType.SLEEP, hasPermissions = false)
    assertThat(state).isEqualTo(WidgetState.Configured(listOf(expectedItem)))
    assertThat(expectedItem.clickAction)
      .isEqualTo(WidgetClickAction.RequestPermissions(DataType.SLEEP))
  }

  @Test
  fun resolveWidgetState_noPermissions_returnsUnauthorized() {
    val settings: SyncSettings = syncSettings {
      setStatesByType(mapOf(DataType.SLEEP to dataTypeState { status = Status.NO_PERMISSIONS }))
    }

    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataTypes = ImmutableSet.of(DataType.SLEEP),
        settings = settings,
        isSyncRunning = false,
      )

    val expectedItem = WidgetItemState.Unauthorized(DataType.SLEEP)
    assertThat(state).isEqualTo(WidgetState.Configured(listOf(expectedItem)))
    assertThat(expectedItem.iconRes).isEqualTo(R.drawable.ic_warning)
    assertThat(expectedItem.clickAction)
      .isEqualTo(WidgetClickAction.RequestPermissions(DataType.SLEEP))
  }

  @Test
  fun resolveWidgetState_okWithoutRecord_returnsNoRecords() {
    val settings: SyncSettings = syncSettings {
      setStatesByType(mapOf(DataType.STEPS to dataTypeState { status = Status.OK }))
    }

    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataTypes = ImmutableSet.of(DataType.STEPS),
        settings = settings,
        isSyncRunning = false,
      )

    val expectedItem = WidgetItemState.NoRecords(DataType.STEPS)
    assertThat(state).isEqualTo(WidgetState.Configured(listOf(expectedItem)))
    assertThat(expectedItem.iconRes).isEqualTo(DataType.STEPS.iconRes)
    assertThat(expectedItem.clickAction).isEqualTo(WidgetClickAction.Refresh)
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
              lastRecordOriginPackage = "com.example.fitbit"
            }
        )
      )
    }

    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataTypes = ImmutableSet.of(DataType.WEIGHT),
        settings = settings,
        isSyncRunning = false,
      )

    val expectedItem =
      WidgetItemState.Active(DataType.WEIGHT, lastRecordTime, originPackage = "com.example.fitbit")
    assertThat(state).isEqualTo(WidgetState.Configured(listOf(expectedItem)))
    assertThat(expectedItem.iconRes).isEqualTo(DataType.WEIGHT.iconRes)
    assertThat(expectedItem.contentDescription).isEqualTo(DataType.WEIGHT.displayName)
    assertThat(expectedItem.clickAction).isEqualTo(WidgetClickAction.Refresh)
  }

  @Test
  fun resolveWidgetState_okWithRecordAndNoOrigin_returnsActiveWithNullOrigin() {
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
        dataTypes = ImmutableSet.of(DataType.WEIGHT),
        settings = settings,
        isSyncRunning = false,
      )

    assertThat(state)
      .isEqualTo(
        WidgetState.Configured(
          listOf(WidgetItemState.Active(DataType.WEIGHT, lastRecordTime, originPackage = null))
        )
      )
  }

  @Test
  fun resolveWidgetState_multipleTypes_preservesOrderAndResolvesEachIndependently() {
    val weightTime = Instant.parse("2026-01-01T00:00:00Z")
    val settings: SyncSettings = syncSettings {
      setStatesByType(
        mapOf(
          DataType.WEIGHT to
            dataTypeState {
              status = Status.OK
              lastRecordTime = weightTime.toTimestamp()
            },
          DataType.SLEEP to dataTypeState { status = Status.NO_PERMISSIONS },
          DataType.STEPS to dataTypeState { status = Status.OK },
        )
      )
    }

    val state: WidgetState =
      resolveWidgetState(
        appWidgetId = 42,
        dataTypes = ImmutableSet.of(DataType.SLEEP, DataType.WEIGHT, DataType.STEPS),
        settings = settings,
        isSyncRunning = false,
      )

    assertThat(state)
      .isEqualTo(
        WidgetState.Configured(
          listOf(
            WidgetItemState.Unauthorized(DataType.SLEEP),
            WidgetItemState.Active(DataType.WEIGHT, weightTime),
            WidgetItemState.NoRecords(DataType.STEPS),
          )
        )
      )
  }
}

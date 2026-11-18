package dev.daw.weightwatcher

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.daw.weightwatcher.DataTypeState.Status
import dev.daw.weightwatcher.widget.glance.provideGlanceWidgetContent
import dev.daw.weightwatcher.widget.remotecompose.updateRemoteComposeWidgets
import java.time.Duration
import kotlinx.coroutines.flow.Flow
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

    fun clock(): AppClock
  }

  companion object {
    internal val HOURS_THRESHOLD: Duration = 1.hours
    internal val STALE_THRESHOLD: Duration = 20.hours
    internal val DAYS_THRESHOLD: Duration = 72.hours
    internal val YEARS_THRESHOLD: Duration = 365.days

    internal fun formatStaleness(staleness: Duration): String =
      when {
        staleness < HOURS_THRESHOLD -> "${staleness.toMinutes()}m"
        staleness < DAYS_THRESHOLD -> "${staleness.toHours()}h"
        staleness < YEARS_THRESHOLD -> "${staleness.toDays()}d"
        else -> "${staleness.toDays() / 365}y"
      }

    /**
     * Returns whether to show a data type as syncing: while a sync runs, or until its first sync
     * finishes.
     */
    internal fun isSyncing(status: Status, isSyncRunning: Boolean): Boolean =
      isSyncRunning || status == Status.STATUS_UNSPECIFIED

    fun previewPreferences(dataType: DataType): Preferences = dataTypePreferencesOf(dataType)

    /** Returns the widget's data type, or `null` if it hasn't been configured yet. */
    suspend fun getWidgetDataType(context: Context, glanceId: GlanceId): DataType? =
      getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId).getDataType()

    suspend fun setWidgetDataType(context: Context, glanceId: GlanceId, dataType: DataType) {
      updateAppWidgetState(context, glanceId) { prefs -> prefs.setDataType(dataType) }
    }

    /** Returns the data types of all configured widgets. */
    suspend fun getActiveDataTypes(context: Context): Set<DataType> =
      getPlacedGlanceIds(context).mapNotNullTo(mutableSetOf()) { glanceId ->
        getWidgetDataType(context, glanceId)
      }

    /**
     * Returns the [AppWidgetManager] IDs of all placed widgets.
     *
     * Asks [AppWidgetManager] directly rather than using [GlanceAppWidgetManager.getGlanceIds],
     * which relies on a receiver-to-widget mapping that Glance records as receivers get broadcasts,
     * and so misses widgets if that mapping is stale.
     */
    internal fun getPlacedAppWidgetIds(context: Context): IntArray =
      AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, WeightGlanceWidgetReceiver::class.java))

    private fun getPlacedGlanceIds(context: Context): List<GlanceId> {
      val glanceAppWidgetManager = GlanceAppWidgetManager(context)
      return getPlacedAppWidgetIds(context).map { appWidgetId ->
        glanceAppWidgetManager.getGlanceIdBy(appWidgetId)
      }
    }
  }

  /**
   * Updates all placed widgets. On Android 16+, pushes RemoteCompose directly without Glance's
   * `SessionWorker`.
   */
  suspend fun updateAllWidgets(context: Context, isSyncRunning: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
      val appWidgetIds: IntArray = getPlacedAppWidgetIds(context)
      if (appWidgetIds.isEmpty()) {
        return
      }
      val dependencies: Dependencies = EntryPointAccessors.fromApplication(context)
      updateRemoteComposeWidgets(
        context = context,
        appWidgetIds = appWidgetIds,
        dataStore = dependencies.syncSettingsDataStore(),
        isSyncRunning = isSyncRunning,
      )
      return
    }
    updateAll(context)
  }

  /** Makes widget [appWidgetId] show [dataType], and syncs it. */
  suspend fun configure(context: Context, appWidgetId: Int, dataType: DataType) {
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    setWidgetDataType(context, glanceId, dataType)
    updateWidget(context, appWidgetId, glanceId)
    WeightWorker.enqueuePeriodicWork(context)
    WeightWorker.enqueueOneTimeWork(context)
  }

  /**
   * Assigns [DataType.WEIGHT] to widgets placed before widgets had a data type, once, recording
   * that it has done so in [dataStore].
   *
   * Run this before any new widget can be pinned (i.e. at process start): an unconfigured widget
   * found later is a fresh pin whose type hasn't arrived yet, not a legacy one.
   */
  suspend fun migrateLegacyWidgets(context: Context, dataStore: DataStore<SyncSettings>) {
    if (dataStore.data.first().legacyWidgetsMigrated) {
      return
    }
    val glanceAppWidgetManager = GlanceAppWidgetManager(context)
    var migratedAny = false
    for (appWidgetId in getPlacedAppWidgetIds(context)) {
      val glanceId = glanceAppWidgetManager.getGlanceIdBy(appWidgetId)
      if (getWidgetDataType(context, glanceId) == null) {
        setWidgetDataType(context, glanceId, DataType.WEIGHT)
        updateWidget(context, appWidgetId, glanceId, dataStore)
        migratedAny = true
      }
    }
    dataStore.updateData { it.copy { legacyWidgetsMigrated = true } }
    if (migratedAny) {
      WeightWorker.enqueueOneTimeWork(context)
    }
  }

  private suspend fun updateWidget(
    context: Context,
    appWidgetId: Int,
    glanceId: GlanceId,
    dataStore: DataStore<SyncSettings>? = null,
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
      val resolvedDataStore: DataStore<SyncSettings> =
        dataStore
          ?: EntryPointAccessors.fromApplication<Dependencies>(context).syncSettingsDataStore()
      updateRemoteComposeWidgets(
        context = context,
        appWidgetIds = intArrayOf(appWidgetId),
        dataStore = resolvedDataStore,
        isSyncRunning = WeightWorker.isSyncing(context).first(),
      )
      return
    }
    update(context, glanceId)
  }

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val dependencies: Dependencies = EntryPointAccessors.fromApplication(context)
    val dataStore: DataStore<SyncSettings> = dependencies.syncSettingsDataStore()
    val clock: AppClock = dependencies.clock()
    val isSyncRunningFlow: Flow<Boolean> = WeightWorker.isSyncing(context)
    val appWidgetId: Int =
      runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }
        .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)

    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
        appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
    ) {
      updateRemoteComposeWidgets(
        context = context,
        appWidgetIds = intArrayOf(appWidgetId),
        dataStore = dataStore,
        isSyncRunning = isSyncRunningFlow.first(),
      )
      return
    }

    provideGlanceWidgetContent(
      context = context,
      appWidgetId = appWidgetId,
      dataStore = dataStore,
      clock = clock,
      isSyncRunningFlow = isSyncRunningFlow,
    )
  }
}

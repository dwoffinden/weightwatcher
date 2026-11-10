package dev.daw.weightwatcher

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.daw.weightwatcher.DataTypeState.Status
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
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

    fun clock(): InstantSource
  }

  companion object {
    private val STALE_THRESHOLD: Duration = 20.hours
    private val DAYS_THRESHOLD: Duration = 72.hours
    val DATA_TYPE_PREF_KEY: Preferences.Key<Int> = intPreferencesKey("data_type")
    private val EXTRA_DATA_TYPE_KEY: ActionParameters.Key<Int> =
      ActionParameters.Key(MainActivity.EXTRA_DATA_TYPE)
    private val EXTRA_APPWIDGET_ID_KEY: ActionParameters.Key<Int> =
      ActionParameters.Key(AppWidgetManager.EXTRA_APPWIDGET_ID)

    internal fun formatStaleness(staleness: Duration): String =
      if (staleness < DAYS_THRESHOLD) "${staleness.toHours()}h" else "${staleness.toDays()}d"

    /**
     * Returns whether to show a data type as syncing: while a sync runs, or until its first sync
     * finishes.
     */
    internal fun isSyncing(status: Status, isSyncRunning: Boolean): Boolean =
      isSyncRunning || status == Status.STATUS_UNSPECIFIED

    /** Returns the supported [DataType] numbered [rawNumber], or `null` if there isn't one. */
    internal fun resolveDataType(rawNumber: Int?): DataType? =
      rawNumber?.let(DataType::forNumber)?.takeIf { it in SUPPORTED_DATA_TYPES }

    fun previewPreferences(dataType: DataType): Preferences =
      preferencesOf(DATA_TYPE_PREF_KEY to dataType.number)

    /** Returns the widget's data type, or `null` if it hasn't been configured yet. */
    suspend fun getWidgetDataType(context: Context, glanceId: GlanceId): DataType? {
      val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
      return resolveDataType(prefs[DATA_TYPE_PREF_KEY])
    }

    suspend fun setWidgetDataType(context: Context, glanceId: GlanceId, dataType: DataType) {
      updateAppWidgetState(context, glanceId) { prefs ->
        prefs[DATA_TYPE_PREF_KEY] = dataType.number
      }
    }

    /** Returns the data types of all configured widgets. */
    suspend fun getActiveDataTypes(context: Context): Set<DataType> =
      getPlacedGlanceIds(context).mapNotNullTo(mutableSetOf()) { glanceId ->
        getWidgetDataType(context, glanceId)
      }

    /**
     * Returns the IDs of all placed widgets.
     *
     * Asks [AppWidgetManager] directly rather than using [GlanceAppWidgetManager.getGlanceIds],
     * which relies on a receiver-to-widget mapping that Glance records as receivers get broadcasts,
     * and so misses widgets if that mapping is stale.
     */
    private fun getPlacedGlanceIds(context: Context): List<GlanceId> {
      val appWidgetIds: IntArray =
        AppWidgetManager.getInstance(context)
          .getAppWidgetIds(ComponentName(context, WeightGlanceWidgetReceiver::class.java))
      val glanceAppWidgetManager = GlanceAppWidgetManager(context)
      return appWidgetIds.map { appWidgetId -> glanceAppWidgetManager.getGlanceIdBy(appWidgetId) }
    }
  }

  /** Makes widget [appWidgetId] show [dataType], and syncs it. */
  suspend fun configure(context: Context, appWidgetId: Int, dataType: DataType) {
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    setWidgetDataType(context, glanceId, dataType)
    update(context, glanceId)
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
    val legacyIds =
      getPlacedGlanceIds(context).filter { glanceId ->
        getWidgetDataType(context, glanceId) == null
      }
    for (glanceId in legacyIds) {
      setWidgetDataType(context, glanceId, DataType.WEIGHT)
      update(context, glanceId)
    }
    dataStore.updateData { it.copy { legacyWidgetsMigrated = true } }
    if (legacyIds.isNotEmpty()) {
      WeightWorker.enqueueOneTimeWork(context)
    }
  }

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val dependencies: Dependencies = EntryPointAccessors.fromApplication(context)
    val dataStore: DataStore<SyncSettings> = dependencies.syncSettingsDataStore()
    val clock: InstantSource = dependencies.clock()
    val initialSettings: SyncSettings = dataStore.data.first()
    val isSyncRunningFlow: Flow<Boolean> = WeightWorker.isSyncing(context)
    val initialIsSyncRunning: Boolean = isSyncRunningFlow.first()
    val appWidgetId: Int = GlanceAppWidgetManager(context).getAppWidgetId(id)
    provideContent {
      val prefs: Preferences = currentState()
      val dataType: DataType? = resolveDataType(prefs[DATA_TYPE_PREF_KEY])
      val settings: SyncSettings by dataStore.data.collectAsState(initial = initialSettings)
      val isSyncRunning: Boolean by isSyncRunningFlow.collectAsState(initial = initialIsSyncRunning)

      GlanceTheme {
        if (dataType == null) {
          // A freshly pinned widget is drawn before its data type arrives; show it as syncing
          // rather than guessing. Tapping opens the picker in case the type never arrives.
          WidgetContent(
            context = context,
            iconRes = R.drawable.ic_sync,
            contentDescription = "Setting up",
            staleness = null,
            hasPermissions = true,
            isSyncing = true,
            onClick =
              actionStartActivity<MainActivity>(
                actionParametersOf(EXTRA_APPWIDGET_ID_KEY to appWidgetId)
              ),
          )
        } else {
          val typeState: DataTypeState =
            settings.statesByTypeMap[dataType.number] ?: DataTypeState.getDefaultInstance()
          val lastRecordTime: Instant? = typeState.lastRecordTimeOrNull?.toInstant()
          val hasPermissions: Boolean = typeState.status != Status.NO_PERMISSIONS
          WidgetContent(
            context = context,
            iconRes = dataType.iconRes,
            contentDescription = dataType.displayName,
            staleness = if (lastRecordTime != null) clock.instant() - lastRecordTime else null,
            hasPermissions = hasPermissions,
            isSyncing = isSyncing(typeState.status, isSyncRunning),
            onClick =
              if (hasPermissions) {
                actionRunCallback<RefreshCallback>()
              } else {
                actionStartActivity<MainActivity>(
                  actionParametersOf(EXTRA_DATA_TYPE_KEY to dataType.number)
                )
              },
          )
        }
      }
    }
  }

  @Composable
  private fun WidgetContent(
    context: Context,
    iconRes: Int,
    contentDescription: String,
    staleness: Duration?,
    hasPermissions: Boolean,
    isSyncing: Boolean,
    onClick: Action,
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
          .clickable(onClick),
      verticalAlignment = Alignment.CenterVertically,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val shownIconRes =
        when {
          isSyncing -> R.drawable.ic_sync
          !hasPermissions -> R.drawable.ic_warning
          else -> iconRes
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
        provider = ImageProvider(shownIconRes),
        contentDescription = contentDescription,
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

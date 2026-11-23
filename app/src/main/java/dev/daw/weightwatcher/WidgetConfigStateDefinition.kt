package dev.daw.weightwatcher

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import java.io.File
import java.io.IOException
import okio.buffer
import okio.source

/**
 * Per-widget [GlanceStateDefinition] backed by a Proto [DataStore] of [WidgetConfig], migrating
 * lazily on first read from Glance's legacy `Preferences` file (`*.preferences_pb`).
 */
object WidgetConfigStateDefinition : GlanceStateDefinition<WidgetConfig> {

  private const val TAG = "WidgetConfigStateDef"
  private val LEGACY_WIDGET_CONFIG_PREF_KEY: Preferences.Key<ByteArray> =
    byteArrayPreferencesKey("widget_config")
  private val LEGACY_DATA_TYPE_PREF_KEY: Preferences.Key<Int> = intPreferencesKey("data_type")

  override fun getLocation(context: Context, fileKey: String): File =
    context.dataStoreFile("$fileKey.pb")

  override suspend fun getDataStore(context: Context, fileKey: String): DataStore<WidgetConfig> =
    DataStoreFactory.create(
      serializer = ProtoSerializer(WidgetConfig.getDefaultInstance()),
      corruptionHandler = ReplaceFileCorruptionHandler { WidgetConfig.getDefaultInstance() },
      migrations = listOf(LegacyPreferencesMigration(context, fileKey)),
      produceFile = { getLocation(context, fileKey) },
    )

  /** Deletes any unmigrated legacy `.preferences_pb` file left behind when a widget is removed. */
  internal fun deleteLegacyFile(context: Context, appWidgetId: Int) {
    val fileKey = "appWidget-$appWidgetId"
    PreferencesGlanceStateDefinition.getLocation(context, fileKey).delete()
  }

  private class LegacyPreferencesMigration(
    private val context: Context,
    private val fileKey: String,
  ) : DataMigration<WidgetConfig> {

    private val legacyFile: File
      get() = PreferencesGlanceStateDefinition.getLocation(context, fileKey)

    override suspend fun shouldMigrate(currentData: WidgetConfig): Boolean =
      currentData.dataTypesList.isEmpty() && legacyFile.exists()

    override suspend fun migrate(currentData: WidgetConfig): WidgetConfig {
      val prefs: Preferences =
        try {
          legacyFile.source().buffer().use { source -> PreferencesSerializer.readFrom(source) }
        } catch (e: CorruptionException) {
          Log.w(TAG, "Ignoring corrupt legacy widget preferences for $fileKey", e)
          return currentData
        } catch (e: IOException) {
          Log.w(TAG, "Failed reading legacy widget preferences for $fileKey", e)
          return currentData
        }
      val configBytes: ByteArray? = prefs[LEGACY_WIDGET_CONFIG_PREF_KEY]
      if (configBytes != null) {
        return WidgetConfig.parser().parseOr(configBytes) { WidgetConfig.getDefaultInstance() }
      }
      val legacyNumber: Int? = prefs[LEGACY_DATA_TYPE_PREF_KEY]
      val legacyProto: SyncSettings.DataType? =
        if (legacyNumber != null) SyncSettings.DataType.forNumber(legacyNumber) else null
      val legacyType: DataType? = if (legacyProto != null) DataType.fromProto(legacyProto) else null
      return if (legacyType != null) {
        widgetConfig { dataTypes += legacyType.proto }
      } else {
        currentData
      }
    }

    override suspend fun cleanUp() {
      legacyFile.delete()
    }
  }
}

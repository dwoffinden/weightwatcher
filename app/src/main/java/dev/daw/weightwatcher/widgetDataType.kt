package dev.daw.weightwatcher

import android.content.Intent
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import com.google.common.collect.ImmutableSet

private const val EXTRA_DATA_TYPE = "dev.daw.weightwatcher.EXTRA_DATA_TYPE"
private const val EXTRA_WIDGET_CONFIG = "dev.daw.weightwatcher.EXTRA_WIDGET_CONFIG"
private val WIDGET_CONFIG_PREF_KEY: Preferences.Key<ByteArray> =
  byteArrayPreferencesKey("widget_config")
private val LEGACY_DATA_TYPE_PREF_KEY: Preferences.Key<Int> = intPreferencesKey("data_type")
private val EXTRA_DATA_TYPE_KEY: ActionParameters.Key<Int> = ActionParameters.Key(EXTRA_DATA_TYPE)

/**
 * Persisted encodings for Android/Glance key-value stores ([Preferences], [Intent], and
 * [ActionParameters]) that don't take custom enums directly.
 *
 * Ordered multi-type widget selections are serialized as a [WidgetConfig] proto [ByteArray], with
 * read fallback to the legacy single-type `data_type` integer preference for existing widgets.
 */
// TODO: Consider migrating to a custom GlanceStateDefinition<WidgetConfig> (pure Proto DataStore
// per widget) once existing widgets no longer need zero-migration fallback from legacy "data_type"
// Preferences files.
internal fun widgetDataTypesPreferencesOf(dataTypes: ImmutableSet<DataType>): Preferences =
  preferencesOf(WIDGET_CONFIG_PREF_KEY to encodeWidgetConfig(dataTypes))

internal fun Preferences.getWidgetDataTypes(): ImmutableSet<DataType> {
  val configBytes: ByteArray? = this[WIDGET_CONFIG_PREF_KEY]
  if (configBytes != null) {
    return decodeWidgetConfig(configBytes)
  }
  val legacyType: DataType? = dataTypeFromNumber(this[LEGACY_DATA_TYPE_PREF_KEY])
  return if (legacyType != null) ImmutableSet.of(legacyType) else ImmutableSet.of()
}

internal fun MutablePreferences.setWidgetDataTypes(dataTypes: ImmutableSet<DataType>) {
  this[WIDGET_CONFIG_PREF_KEY] = encodeWidgetConfig(dataTypes)
  remove(LEGACY_DATA_TYPE_PREF_KEY)
}

internal fun dataTypeActionParameters(dataType: DataType): ActionParameters =
  actionParametersOf(EXTRA_DATA_TYPE_KEY to dataType.toNumber())

internal fun Intent.putDataType(dataType: DataType): Intent =
  putExtra(EXTRA_DATA_TYPE, dataType.toNumber())

internal fun Intent.getDataType(): DataType? = dataTypeFromNumber(getIntExtra(EXTRA_DATA_TYPE, 0))

internal fun Intent.putWidgetDataTypes(dataTypes: ImmutableSet<DataType>): Intent =
  putExtra(EXTRA_WIDGET_CONFIG, encodeWidgetConfig(dataTypes))

internal fun Intent.getWidgetDataTypes(): ImmutableSet<DataType> {
  val configBytes: ByteArray? = getByteArrayExtra(EXTRA_WIDGET_CONFIG)
  if (configBytes != null) {
    return decodeWidgetConfig(configBytes)
  }
  val singleType: DataType? = getDataType()
  return if (singleType != null) ImmutableSet.of(singleType) else ImmutableSet.of()
}

private fun encodeWidgetConfig(dataTypes: ImmutableSet<DataType>): ByteArray =
  widgetConfig { this.dataTypes += dataTypes.map { it.proto } }.toByteArray()

private fun decodeWidgetConfig(bytes: ByteArray): ImmutableSet<DataType> {
  val builder = ImmutableSet.builder<DataType>()
  for (protoType in
    WidgetConfig.parser().parseOr(bytes) { WidgetConfig.getDefaultInstance() }.dataTypesList) {
    val dataType: DataType? = DataType.fromProto(protoType)
    if (dataType != null) {
      builder.add(dataType)
    }
  }
  return builder.build()
}

private fun DataType.toNumber(): Int = proto.number

private fun dataTypeFromNumber(number: Int?): DataType? =
  if (number != null) SyncSettings.DataType.forNumber(number)?.let(DataType::fromProto) else null

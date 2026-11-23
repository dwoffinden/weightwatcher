package dev.daw.weightwatcher

import android.content.Intent
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import com.google.common.collect.ImmutableSet

private const val EXTRA_DATA_TYPE = "dev.daw.weightwatcher.EXTRA_DATA_TYPE"
private const val EXTRA_WIDGET_CONFIG = "dev.daw.weightwatcher.EXTRA_WIDGET_CONFIG"
private val EXTRA_DATA_TYPE_KEY: ActionParameters.Key<Int> = ActionParameters.Key(EXTRA_DATA_TYPE)

/**
 * Encodings for [WidgetConfig] and Android/Glance IPC stores ([Intent] and [ActionParameters]) that
 * don't take custom enums directly.
 */
internal fun ImmutableSet<DataType>.toWidgetConfig(): WidgetConfig = widgetConfig {
  dataTypes += map { it.proto }
}

internal fun WidgetConfig.toDataTypes(): ImmutableSet<DataType> {
  val builder = ImmutableSet.builder<DataType>()
  for (protoType in dataTypesList) {
    val dataType: DataType? = DataType.fromProto(protoType)
    if (dataType != null) {
      builder.add(dataType)
    }
  }
  return builder.build()
}

internal fun dataTypeActionParameters(dataType: DataType): ActionParameters =
  actionParametersOf(EXTRA_DATA_TYPE_KEY to dataType.toNumber())

internal fun Intent.putDataType(dataType: DataType): Intent =
  putExtra(EXTRA_DATA_TYPE, dataType.toNumber())

internal fun Intent.getDataType(): DataType? = dataTypeFromNumber(getIntExtra(EXTRA_DATA_TYPE, 0))

internal fun Intent.putWidgetDataTypes(dataTypes: ImmutableSet<DataType>): Intent =
  putExtra(EXTRA_WIDGET_CONFIG, dataTypes.toWidgetConfig().toByteArray())

internal fun Intent.getWidgetDataTypes(): ImmutableSet<DataType> {
  val configBytes: ByteArray? = getByteArrayExtra(EXTRA_WIDGET_CONFIG)
  if (configBytes != null) {
    return WidgetConfig.parser()
      .parseOr(configBytes) { WidgetConfig.getDefaultInstance() }
      .toDataTypes()
  }
  val singleType: DataType? = getDataType()
  return if (singleType != null) ImmutableSet.of(singleType) else ImmutableSet.of()
}

private fun DataType.toNumber(): Int = proto.number

private fun dataTypeFromNumber(number: Int?): DataType? =
  if (number != null) SyncSettings.DataType.forNumber(number)?.let(DataType::fromProto) else null

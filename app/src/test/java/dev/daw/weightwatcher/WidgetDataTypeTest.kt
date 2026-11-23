package dev.daw.weightwatcher

import android.app.Application
import android.content.Intent
import androidx.glance.action.ActionParameters
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.annotation.Config

@RunWith(RobolectricTestParameterInjector::class)
@Config(sdk = [34], application = Application::class)
class WidgetDataTypeTest {

  @Test
  fun toWidgetConfig_roundTripsSingleDataType(@TestParameter dataType: DataType) {
    assertThat(ImmutableSet.of(dataType).toWidgetConfig().toDataTypes()).containsExactly(dataType)
  }

  @Test
  fun toDataTypes_duplicateEntriesInConfig_deduplicatesPreservingOrder() {
    val config: WidgetConfig = widgetConfig {
      dataTypes +=
        listOf(
          SyncSettings.DataType.SLEEP,
          SyncSettings.DataType.WEIGHT,
          SyncSettings.DataType.SLEEP,
          SyncSettings.DataType.STEPS,
        )
    }

    assertThat(config.toDataTypes())
      .containsExactly(DataType.SLEEP, DataType.WEIGHT, DataType.STEPS)
      .inOrder()
  }

  @Test
  fun toDataTypes_unknownProtoEnumInConfig_ignoresUnknownEntries() {
    val config: WidgetConfig =
      WidgetConfig.newBuilder()
        .addDataTypesValue(999)
        .addDataTypes(SyncSettings.DataType.WEIGHT)
        .addDataTypes(SyncSettings.DataType.DATA_TYPE_UNSPECIFIED)
        .build()

    assertThat(config.toDataTypes()).containsExactly(DataType.WEIGHT)
  }

  @Test
  fun putDataType_storesDataTypeOnIntent(@TestParameter dataType: DataType) {
    val intent = Intent().putDataType(dataType)
    assertThat(intent.getDataType()).isEqualTo(dataType)
    assertThat(intent.getWidgetDataTypes()).containsExactly(dataType)
  }

  @Test
  fun putWidgetDataTypes_storesOrderedSetOnIntent() {
    val intent = Intent().putWidgetDataTypes(ImmutableSet.of(DataType.HEART_RATE, DataType.STEPS))
    assertThat(intent.getWidgetDataTypes())
      .containsExactly(DataType.HEART_RATE, DataType.STEPS)
      .inOrder()
  }

  @Test
  fun getWidgetDataTypes_corruptConfigBytesOnIntent_returnsEmpty() {
    val intent =
      Intent().putExtra("dev.daw.weightwatcher.EXTRA_WIDGET_CONFIG", byteArrayOf(0xFF.toByte()))
    assertThat(intent.getWidgetDataTypes()).isEmpty()
  }

  @Test
  fun getDataType_intentWithoutExtra_returnsNull() {
    assertThat(Intent().getDataType()).isNull()
    assertThat(Intent().getWidgetDataTypes()).isEmpty()
  }

  @Test
  fun getDataType_unknownIntentExtra_returnsNull(@TestParameter("0", "-1", "999") number: Int) {
    val intent = Intent().putExtra("dev.daw.weightwatcher.EXTRA_DATA_TYPE", number)
    assertThat(intent.getDataType()).isNull()
  }

  @Test
  fun dataTypeActionParameters_matchesIntentExtraKeyAndValue(@TestParameter dataType: DataType) {
    val params: Map<ActionParameters.Key<*>, Any> = dataTypeActionParameters(dataType).asMap()
    val entry: Map.Entry<ActionParameters.Key<*>, Any> = params.entries.single()

    val intent = Intent().putExtra(entry.key.name, entry.value as Int)

    assertThat(intent.getDataType()).isEqualTo(dataType)
  }
}

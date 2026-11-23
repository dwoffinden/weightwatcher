package dev.daw.weightwatcher

import android.app.Application
import android.content.Intent
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
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
  fun widgetDataTypesPreferencesOf_storesDataType(@TestParameter dataType: DataType) {
    assertThat(widgetDataTypesPreferencesOf(ImmutableSet.of(dataType)).getWidgetDataTypes())
      .containsExactly(dataType)
  }

  @Test
  fun setWidgetDataTypes_storesOrderedSetAndClearsLegacyKey() {
    val prefs: MutablePreferences = mutablePreferencesOf(intPreferencesKey("data_type") to 1)

    prefs.setWidgetDataTypes(ImmutableSet.of(DataType.SLEEP, DataType.WEIGHT, DataType.STEPS))

    assertThat(prefs.getWidgetDataTypes())
      .containsExactly(DataType.SLEEP, DataType.WEIGHT, DataType.STEPS)
      .inOrder()
    assertThat(prefs[intPreferencesKey("data_type")]).isNull()
  }

  @Test
  fun getWidgetDataTypes_emptyPreferences_returnsEmpty() {
    assertThat(emptyPreferences().getWidgetDataTypes()).isEmpty()
  }

  @Test
  fun getWidgetDataTypes_legacyIntPreference_returnsSingleElement(
    @TestParameter dataType: DataType
  ) {
    val prefs = preferencesOf(intPreferencesKey("data_type") to dataType.proto.number)
    assertThat(prefs.getWidgetDataTypes()).containsExactly(dataType)
  }

  @Test
  fun getWidgetDataTypes_unknownLegacyPreferenceNumber_returnsEmpty(
    @TestParameter("0", "-1", "999") number: Int
  ) {
    val prefs = preferencesOf(intPreferencesKey("data_type") to number)
    assertThat(prefs.getWidgetDataTypes()).isEmpty()
  }

  @Test
  fun getWidgetDataTypes_corruptConfigBytes_returnsEmpty() {
    val prefs =
      preferencesOf(byteArrayPreferencesKey("widget_config") to byteArrayOf(0xFF.toByte()))
    assertThat(prefs.getWidgetDataTypes()).isEmpty()
  }

  @Test
  fun getWidgetDataTypes_duplicateEntriesInConfig_deduplicatesPreservingOrder() {
    val bytes: ByteArray =
      widgetConfig {
          dataTypes +=
            listOf(
              SyncSettings.DataType.SLEEP,
              SyncSettings.DataType.WEIGHT,
              SyncSettings.DataType.SLEEP,
              SyncSettings.DataType.STEPS,
            )
        }
        .toByteArray()
    val prefs = preferencesOf(byteArrayPreferencesKey("widget_config") to bytes)

    assertThat(prefs.getWidgetDataTypes())
      .containsExactly(DataType.SLEEP, DataType.WEIGHT, DataType.STEPS)
      .inOrder()
  }

  @Test
  fun getWidgetDataTypes_unknownProtoEnumInConfig_ignoresUnknownEntries() {
    val bytes: ByteArray =
      WidgetConfig.newBuilder()
        .addDataTypesValue(999)
        .addDataTypes(SyncSettings.DataType.WEIGHT)
        .addDataTypes(SyncSettings.DataType.DATA_TYPE_UNSPECIFIED)
        .build()
        .toByteArray()
    val prefs = preferencesOf(byteArrayPreferencesKey("widget_config") to bytes)

    assertThat(prefs.getWidgetDataTypes()).containsExactly(DataType.WEIGHT)
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

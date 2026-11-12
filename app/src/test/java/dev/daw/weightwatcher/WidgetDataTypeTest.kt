package dev.daw.weightwatcher

import android.app.Application
import android.content.Intent
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.action.ActionParameters
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
  fun dataTypePreferencesOf_storesDataType(@TestParameter dataType: DataType) {
    assertThat(dataTypePreferencesOf(dataType).getDataType()).isEqualTo(dataType)
  }

  @Test
  fun setDataType_storesDataType(@TestParameter dataType: DataType) {
    val prefs: MutablePreferences = mutablePreferencesOf()
    prefs.setDataType(dataType)
    assertThat(prefs.getDataType()).isEqualTo(dataType)
  }

  @Test
  fun getDataType_emptyPreferences_returnsNull() {
    assertThat(emptyPreferences().getDataType()).isNull()
  }

  @Test
  fun getDataType_unknownPreferenceNumber_returnsNull(
    @TestParameter("0", "-1", "999") number: Int
  ) {
    val prefs = preferencesOf(intPreferencesKey("data_type") to number)
    assertThat(prefs.getDataType()).isNull()
  }

  @Test
  fun putDataType_storesDataTypeOnIntent(@TestParameter dataType: DataType) {
    val intent = Intent().putDataType(dataType)
    assertThat(intent.getDataType()).isEqualTo(dataType)
  }

  @Test
  fun getDataType_intentWithoutExtra_returnsNull() {
    assertThat(Intent().getDataType()).isNull()
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

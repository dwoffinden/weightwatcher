package dev.daw.weightwatcher

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests of [WeightGlanceWidget]'s per-widget state, which needs Robolectric's [AppWidgetManager]
 * (so can't share [WeightGlanceWidgetTest]'s TestParameterInjector runner).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeightGlanceWidgetStateTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var dataStore: DataStore<SyncSettings>
  private val widget = WeightGlanceWidget()

  @Before
  fun setUp() {
    initializeWorkManager(context)
    val file: File = tempFolder.newFile()
    this.dataStore =
      DataStoreFactory.create(
        serializer = ProtoSerializer(SyncSettings.getDefaultInstance(), SyncSettings.parser()),
        produceFile = { file },
      )
  }

  @Test
  fun getWidgetDataType_unconfiguredWidget_returnsNull() =
    runBlocking<Unit> {
      val appWidgetId: Int = placeWidget(context)
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)

      assertThat(WeightGlanceWidget.getWidgetDataType(context, glanceId)).isNull()
    }

  @Test
  fun getWidgetDataType_afterSetWidgetDataType_returnsIt() =
    runBlocking<Unit> {
      val appWidgetId: Int = placeWidget(context)
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)

      WeightGlanceWidget.setWidgetDataType(context, glanceId, DataType.SLEEP)

      assertThat(WeightGlanceWidget.getWidgetDataType(context, glanceId)).isEqualTo(DataType.SLEEP)
    }

  @Test
  fun configure_placedWidget_storesDataType() =
    runBlocking<Unit> {
      val appWidgetId: Int = placeWidget(context)

      widget.configure(context, appWidgetId, DataType.BLOOD_PRESSURE)

      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
      assertThat(WeightGlanceWidget.getWidgetDataType(context, glanceId))
        .isEqualTo(DataType.BLOOD_PRESSURE)
    }

  @Test
  fun getActiveDataTypes_mixedWidgets_returnsConfiguredTypesOnly() =
    runBlocking<Unit> {
      val stepsWidgetId: Int = placeWidget(context)
      val sleepWidgetId: Int = placeWidget(context)
      placeWidget(context)
      widget.configure(context, stepsWidgetId, DataType.STEPS)
      widget.configure(context, sleepWidgetId, DataType.SLEEP)

      val activeDataTypes: Set<DataType> = WeightGlanceWidget.getActiveDataTypes(context)

      assertThat(activeDataTypes).containsExactly(DataType.STEPS, DataType.SLEEP)
    }

  @Test
  fun migrateLegacyWidgets_notYetMigrated_assignsWeightToUnconfiguredWidgets() =
    runBlocking<Unit> {
      placeWidget(context)
      val configuredWidgetId: Int = placeWidget(context)
      widget.configure(context, configuredWidgetId, DataType.STEPS)

      widget.migrateLegacyWidgets(context, dataStore)

      assertThat(WeightGlanceWidget.getActiveDataTypes(context))
        .containsExactly(DataType.WEIGHT, DataType.STEPS)
    }

  @Test
  fun migrateLegacyWidgets_notYetMigrated_recordsMigration() =
    runBlocking<Unit> {
      placeWidget(context)

      widget.migrateLegacyWidgets(context, dataStore)

      assertThat(dataStore.data.first().legacyWidgetsMigrated).isTrue()
    }

  @Test
  fun migrateLegacyWidgets_alreadyMigrated_leavesUnconfiguredWidgetsAlone() =
    runBlocking<Unit> {
      dataStore.updateData { it.copy { legacyWidgetsMigrated = true } }
      val appWidgetId: Int = placeWidget(context)

      widget.migrateLegacyWidgets(context, dataStore)

      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
      assertThat(WeightGlanceWidget.getWidgetDataType(context, glanceId)).isNull()
    }
}

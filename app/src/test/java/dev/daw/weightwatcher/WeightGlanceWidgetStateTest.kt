package dev.daw.weightwatcher

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableSet
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
        serializer = ProtoSerializer(SyncSettings.getDefaultInstance()),
        produceFile = { file },
      )
  }

  @Test
  fun getWidgetDataTypes_unconfiguredWidget_returnsEmpty() =
    runBlocking<Unit> {
      val appWidgetId: Int = placeWidget(context)
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)

      assertThat(WeightGlanceWidget.getWidgetDataTypes(context, glanceId)).isEmpty()
    }

  @Test
  fun getWidgetDataTypes_afterSetWidgetDataTypes_preservesOrder() =
    runBlocking<Unit> {
      val appWidgetId: Int = placeWidget(context)
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)

      WeightGlanceWidget.setWidgetDataTypes(
        context,
        glanceId,
        ImmutableSet.of(DataType.SLEEP, DataType.WEIGHT, DataType.STEPS),
      )

      assertThat(WeightGlanceWidget.getWidgetDataTypes(context, glanceId))
        .containsExactly(DataType.SLEEP, DataType.WEIGHT, DataType.STEPS)
        .inOrder()
    }

  @Test
  fun getWidgetDataTypes_legacySingleIntPreference_readsAsSingleElementList() =
    runBlocking<Unit> {
      val appWidgetId: Int = placeWidget(context)
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
      updateAppWidgetState(context, glanceId) { prefs ->
        prefs[intPreferencesKey("data_type")] = DataType.HEART_RATE.proto.number
      }

      assertThat(WeightGlanceWidget.getWidgetDataTypes(context, glanceId))
        .containsExactly(DataType.HEART_RATE)
    }

  @Test
  fun configure_placedWidget_storesMultipleDataTypesInOrder() =
    runBlocking<Unit> {
      val appWidgetId: Int = placeWidget(context)

      widget.configure(
        context,
        appWidgetId,
        ImmutableSet.of(DataType.BLOOD_PRESSURE, DataType.STEPS),
      )

      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
      assertThat(WeightGlanceWidget.getWidgetDataTypes(context, glanceId))
        .containsExactly(DataType.BLOOD_PRESSURE, DataType.STEPS)
        .inOrder()
    }

  @Test
  fun getActiveDataTypes_mixedWidgets_returnsUnionOfConfiguredTypes() =
    runBlocking<Unit> {
      val multiWidgetId: Int = placeWidget(context)
      val sleepWidgetId: Int = placeWidget(context)
      placeWidget(context)
      widget.configure(context, multiWidgetId, ImmutableSet.of(DataType.STEPS, DataType.HEART_RATE))
      widget.configure(context, sleepWidgetId, DataType.SLEEP)

      val activeDataTypes: Set<DataType> = WeightGlanceWidget.getActiveDataTypes(context)

      assertThat(activeDataTypes)
        .containsExactly(DataType.STEPS, DataType.HEART_RATE, DataType.SLEEP)
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
      assertThat(WeightGlanceWidget.getWidgetDataTypes(context, glanceId)).isEmpty()
    }
}

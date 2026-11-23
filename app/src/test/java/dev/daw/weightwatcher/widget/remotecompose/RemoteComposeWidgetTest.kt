package dev.daw.weightwatcher.widget.remotecompose

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import dev.daw.weightwatcher.DataType
import dev.daw.weightwatcher.DataTypeState.Status
import dev.daw.weightwatcher.MainActivity
import dev.daw.weightwatcher.ProtoSerializer
import dev.daw.weightwatcher.SyncSettings
import dev.daw.weightwatcher.WeightGlanceWidget
import dev.daw.weightwatcher.WeightWorker
import dev.daw.weightwatcher.dataTypeState
import dev.daw.weightwatcher.getDataType
import dev.daw.weightwatcher.initializeWorkManager
import dev.daw.weightwatcher.placeWidget
import dev.daw.weightwatcher.setStatesByType
import dev.daw.weightwatcher.syncSettings
import dev.daw.weightwatcher.toTimestamp
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class RemoteComposeWidgetTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private val application: Application = ApplicationProvider.getApplicationContext()
  private val context: Context = application
  private lateinit var dataStore: DataStore<SyncSettings>

  @Before
  fun setUp() {
    initializeWorkManager(context)
    // RemoteViews(DrawInstructions) has layoutId 0, matching Robolectric's uninflated WidgetInfo
    // default (0) and tricking ShadowAppWidgetManager into calling reapply(context, null).
    shadowOf(AppWidgetManager.getInstance(context)).setAlwaysRecreateViewsDuringUpdate(true)
    val file: File = tempFolder.newFile()
    this.dataStore =
      DataStoreFactory.create(
        serializer = ProtoSerializer(SyncSettings.getDefaultInstance()),
        produceFile = { file },
      )
  }

  @Test
  fun updateRemoteComposeWidgets_updatesWidgetInAppWidgetManager() =
    runBlocking<Unit> {
      val appWidgetId: Int = placeWidget(context)
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
      WeightGlanceWidget.setWidgetDataTypes(context, glanceId, ImmutableSet.of(DataType.WEIGHT))
      dataStore.updateData {
        syncSettings {
          setStatesByType(
            mapOf(
              DataType.WEIGHT to
                dataTypeState {
                  status = Status.OK
                  lastRecordTime = Instant.parse("2026-01-01T00:00:00Z").toTimestamp()
                }
            )
          )
        }
      }

      updateRemoteComposeWidgets(
        context = context,
        appWidgetIds = intArrayOf(appWidgetId),
        dataStore = dataStore,
        isSyncRunning = false,
      )

      val shadowAppWidgetManager = shadowOf(AppWidgetManager.getInstance(context))
      assertThat(shadowAppWidgetManager.getViewFor(appWidgetId)).isNotNull()
    }

  @Test
  fun updateRemoteComposeWidgets_invalidAppWidgetId_skipsAndUpdatesValidWidgets() =
    runBlocking<Unit> {
      val validWidgetId: Int = placeWidget(context)
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(validWidgetId)
      WeightGlanceWidget.setWidgetDataTypes(context, glanceId, ImmutableSet.of(DataType.WEIGHT))

      updateRemoteComposeWidgets(
        context = context,
        appWidgetIds = intArrayOf(999_999, validWidgetId),
        dataStore = dataStore,
        isSyncRunning = false,
      )

      val shadowAppWidgetManager = shadowOf(AppWidgetManager.getInstance(context))
      assertThat(shadowAppWidgetManager.getViewFor(validWidgetId)).isNotNull()
    }

  @Test
  fun buildRemoteComposeWidgetViews_activeWidgetTapped_enqueuesOneTimeSyncWork() =
    runBlocking<Unit> {
      val settings: SyncSettings = syncSettings {
        setStatesByType(
          mapOf(
            DataType.WEIGHT to
              dataTypeState {
                status = Status.OK
                lastRecordTime = Instant.parse("2026-01-01T00:00:00Z").toTimestamp()
              }
          )
        )
      }
      val remoteViews: RemoteViews =
        buildRemoteComposeWidgetViews(
          context = context,
          appWidgetId = 42,
          dataTypes = ImmutableSet.of(DataType.WEIGHT),
          settings = settings,
          isSyncRunning = false,
        )
      val view: View = inflateAndDrawRemoteViews(remoteViews)

      tapAt(view, x = WIDGET_SIZE_PX / 2f, y = WIDGET_SIZE_PX / 2f)

      val workInfos: List<WorkInfo> =
        WorkManager.getInstance(context)
          .getWorkInfosForUniqueWork(WeightWorker.ONE_TIME_WORK_NAME)
          .get()
      assertThat(workInfos).isNotEmpty()
    }

  @Test
  fun buildRemoteComposeWidgetViews_unauthorizedWidgetTapped_startsMainActivityForDataType() =
    runBlocking<Unit> {
      val settings: SyncSettings = syncSettings {
        setStatesByType(mapOf(DataType.SLEEP to dataTypeState { status = Status.NO_PERMISSIONS }))
      }
      val remoteViews: RemoteViews =
        buildRemoteComposeWidgetViews(
          context = context,
          appWidgetId = 42,
          dataTypes = ImmutableSet.of(DataType.SLEEP),
          settings = settings,
          isSyncRunning = false,
        )
      val view: View = inflateAndDrawRemoteViews(remoteViews)

      tapAt(view, x = WIDGET_SIZE_PX / 2f, y = WIDGET_SIZE_PX / 2f)

      val startedIntent: Intent? = shadowOf(application).nextStartedActivity
      assertThat(startedIntent?.component?.className).isEqualTo(MainActivity::class.java.name)
      assertThat(startedIntent?.getDataType()).isEqualTo(DataType.SLEEP)
    }

  @Test
  fun buildRemoteComposeWidgetViews_multiItemWidget_routesPerTileTapsIndependently() =
    runBlocking<Unit> {
      val settings: SyncSettings = syncSettings {
        setStatesByType(
          mapOf(
            DataType.WEIGHT to
              dataTypeState {
                status = Status.OK
                lastRecordTime = Instant.parse("2026-01-01T00:00:00Z").toTimestamp()
              },
            DataType.SLEEP to dataTypeState { status = Status.NO_PERMISSIONS },
          )
        )
      }
      val remoteViews: RemoteViews =
        buildRemoteComposeWidgetViews(
          context = context,
          appWidgetId = 42,
          dataTypes = ImmutableSet.of(DataType.WEIGHT, DataType.SLEEP),
          settings = settings,
          isSyncRunning = false,
        )
      val widthPx = WIDGET_SIZE_PX * 2
      val view: View = inflateAndDrawRemoteViews(remoteViews, widthPx = widthPx)

      // Tap right tile (Unauthorized Sleep) -> opens MainActivity for Sleep.
      tapAt(view, x = widthPx * 0.75f, y = WIDGET_SIZE_PX / 2f)
      val startedIntent: Intent? = shadowOf(application).nextStartedActivity
      assertThat(startedIntent?.component?.className).isEqualTo(MainActivity::class.java.name)
      assertThat(startedIntent?.getDataType()).isEqualTo(DataType.SLEEP)

      // Tap left tile (Active Weight) -> enqueues one-time sync.
      tapAt(view, x = widthPx * 0.25f, y = WIDGET_SIZE_PX / 2f)
      val workInfos: List<WorkInfo> =
        WorkManager.getInstance(context)
          .getWorkInfosForUniqueWork(WeightWorker.ONE_TIME_WORK_NAME)
          .get()
      assertThat(workInfos).isNotEmpty()
    }

  @Test
  fun buildRemoteComposeWidgetViews_unconfiguredWidgetTapped_startsMainActivityForWidgetId() =
    runBlocking<Unit> {
      val remoteViews: RemoteViews =
        buildRemoteComposeWidgetViews(
          context = context,
          appWidgetId = 77,
          dataTypes = ImmutableSet.of(),
          settings = SyncSettings.getDefaultInstance(),
          isSyncRunning = false,
        )
      val view: View = inflateAndDrawRemoteViews(remoteViews)

      tapAt(view, x = WIDGET_SIZE_PX / 2f, y = WIDGET_SIZE_PX / 2f)

      val startedIntent: Intent? = shadowOf(application).nextStartedActivity
      assertThat(startedIntent?.component?.className).isEqualTo(MainActivity::class.java.name)
      assertThat(
          startedIntent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
          )
        )
        .isEqualTo(77)
    }

  @Test
  fun widgetRefreshReceiver_onReceive_enqueuesOneTimeSyncWork() {
    WidgetRefreshReceiver().onReceive(context, Intent(context, WidgetRefreshReceiver::class.java))

    val workInfos: List<WorkInfo> =
      WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(WeightWorker.ONE_TIME_WORK_NAME)
        .get()
    assertThat(workInfos).isNotEmpty()
  }

  private fun inflateAndDrawRemoteViews(
    remoteViews: RemoteViews,
    widthPx: Int = WIDGET_SIZE_PX,
    heightPx: Int = WIDGET_SIZE_PX,
  ): View {
    val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val container = FrameLayout(activity)
    activity.setContentView(container)
    val view: View = remoteViews.apply(activity, container)
    container.addView(view)
    container.measure(
      MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY),
    )
    container.layout(0, 0, widthPx, heightPx)
    val bitmap: Bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    container.draw(Canvas(bitmap))
    return view
  }

  private fun tapAt(view: View, x: Float, y: Float) {
    val downTime: Long = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
    val up = MotionEvent.obtain(downTime, downTime + 10L, MotionEvent.ACTION_UP, x, y, 0)
    try {
      view.dispatchTouchEvent(down)
      view.dispatchTouchEvent(up)
    } finally {
      down.recycle()
      up.recycle()
    }
    ShadowLooper.idleMainLooper()
  }

  private companion object {
    const val WIDGET_SIZE_PX = 200
  }
}

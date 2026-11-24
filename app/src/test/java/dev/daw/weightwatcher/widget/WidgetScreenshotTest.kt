package dev.daw.weightwatcher.widget

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.SystemClock as RemoteSystemClock
import androidx.compose.remote.core.operations.Theme
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import androidx.compose.remote.player.core.platform.AndroidRemoteContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.testing.junit.testparameterinjector.TestParameter
import dev.daw.weightwatcher.AppClock
import dev.daw.weightwatcher.DataType
import dev.daw.weightwatcher.hours
import dev.daw.weightwatcher.minutes
import dev.daw.weightwatcher.seconds
import dev.daw.weightwatcher.widget.glance.GlanceWidgetContent
import dev.daw.weightwatcher.widget.remotecompose.captureWidgetDocumentBytes
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestParameterInjector::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w320dp-h120dp-xxhdpi")
class WidgetScreenshotTest {

  enum class VisualScenario(val widthDp: Int = WIDGET_CELL_SIZE_DP) {
    ACTIVE_20M,
    ACTIVE_2H,
    STALE_24H,
    SYNCING,
    UNAUTHORIZED,
    NO_RECORDS,
    MULTI_3_2X1(widthDp = 184),
    MULTI_5_3X1(widthDp = 280),
  }

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    val packageManager = shadowOf(context.packageManager)
    packageManager.setApplicationIcon(
      PACKAGE_FITBIT,
      createFakeAppIcon(
        backgroundColour = Color.rgb(0x00, 0xB0, 0xB9),
        foregroundColour = Color.WHITE,
      ),
    )
    packageManager.setApplicationIcon(
      PACKAGE_STRAVA,
      createFakeAppIcon(
        backgroundColour = Color.rgb(0xFC, 0x4C, 0x02),
        foregroundColour = Color.WHITE,
      ),
    )
  }

  @Test
  fun glance_light(@TestParameter scenario: VisualScenario) =
    runBlocking<Unit> { captureGlance(scenario, theme = "light") }

  @Test
  @Config(qualifiers = "w320dp-h120dp-night-xxhdpi")
  fun glance_dark(@TestParameter scenario: VisualScenario) =
    runBlocking<Unit> { captureGlance(scenario, theme = "dark") }

  @Test
  // RcPlatformProfiles and Theme are @RestrictTo(LIBRARY_GROUP) in androidx.compose.remote.
  @Suppress("RestrictedApi")
  fun remoteComposeV6_light(@TestParameter scenario: VisualScenario) =
    runBlocking<Unit> {
      captureRemoteCompose(
        scenario = scenario,
        profile = RcPlatformProfiles.WIDGETS_V6,
        theme = Theme.LIGHT,
        name = "remote_v6_light",
      )
    }

  @Test
  @Config(qualifiers = "w320dp-h120dp-night-xxhdpi")
  // RcPlatformProfiles and Theme are @RestrictTo(LIBRARY_GROUP) in androidx.compose.remote.
  @Suppress("RestrictedApi")
  fun remoteComposeV6_dark(@TestParameter scenario: VisualScenario) =
    runBlocking<Unit> {
      captureRemoteCompose(
        scenario = scenario,
        profile = RcPlatformProfiles.WIDGETS_V6,
        theme = Theme.DARK,
        name = "remote_v6_dark",
      )
    }

  @Test
  // RcPlatformProfiles and Theme are @RestrictTo(LIBRARY_GROUP) in androidx.compose.remote.
  @Suppress("RestrictedApi")
  fun remoteComposeV7_light(@TestParameter scenario: VisualScenario) =
    runBlocking<Unit> {
      captureRemoteCompose(
        scenario = scenario,
        profile = RcPlatformProfiles.WIDGETS_V7,
        theme = Theme.LIGHT,
        name = "remote_v7_light",
      )
    }

  @Test
  // RcPlatformProfiles and Theme are @RestrictTo(LIBRARY_GROUP) in androidx.compose.remote.
  @Suppress("RestrictedApi")
  fun remoteComposeV7_dark(@TestParameter scenario: VisualScenario) =
    runBlocking<Unit> {
      captureRemoteCompose(
        scenario = scenario,
        profile = RcPlatformProfiles.WIDGETS_V7,
        theme = Theme.DARK,
        name = "remote_v7_dark",
      )
    }

  @OptIn(ExperimentalGlanceRemoteViewsApi::class)
  private suspend fun captureGlance(scenario: VisualScenario, theme: String) {
    val state: WidgetState = scenario.toWidgetState(FIXED_NOW)
    val remoteViews: RemoteViews =
      GlanceRemoteViews()
        .compose(context = context, size = DpSize(scenario.widthDp.dp, WIDGET_CELL_SIZE_DP.dp)) {
          GlanceWidgetContent(
            context = context,
            state = state,
            clock =
              AppClock.fixed(
                instant = FIXED_NOW,
                elapsedRealtime = Duration.ofMillis(SystemClock.elapsedRealtime()),
              ),
            useChronometer = true,
          )
        }
        .remoteViews
    captureRemoteViews(
      remoteViews = remoteViews,
      widthDp = scenario.widthDp,
      fileName = "glance_${theme}_${scenario.name.lowercase()}.png",
    )
  }

  // CoreDocument, RemoteComposeBuffer, SystemClock, and AndroidRemoteContext are LIBRARY_GROUP.
  @Suppress("RestrictedApi")
  private suspend fun captureRemoteCompose(
    scenario: VisualScenario,
    profile: Profile,
    theme: Int,
    name: String,
  ) {
    val bytes: ByteArray =
      captureWidgetDocumentBytes(
        context = context,
        state = scenario.toWidgetState(FIXED_NOW),
        profile = profile,
      )
    val density: Float = context.resources.displayMetrics.density
    val widgetWidthPx: Int = (scenario.widthDp * density).toInt()
    val widgetHeightPx: Int = (WIDGET_CELL_SIZE_DP * density).toInt()
    val paddingPx: Int = (FRAME_PADDING_DP * density).toInt()
    val totalWidthPx: Int = widgetWidthPx + 2 * paddingPx
    val totalHeightPx: Int = widgetHeightPx + 2 * paddingPx
    val widgetBitmap: Bitmap =
      Bitmap.createBitmap(widgetWidthPx, widgetHeightPx, Bitmap.Config.ARGB_8888)
    val widgetCanvas = Canvas(widgetBitmap)
    val remoteClock = RemoteSystemClock(Clock.fixed(FIXED_NOW, ZoneOffset.UTC))
    val remoteContext =
      AndroidRemoteContext(remoteClock).apply {
        setAndroidContext(context)
        setDensity(density)
        setDocLoadTime()
        setAnimationEnabled(true)
        currentTime = remoteClock.millis()
        useCanvas(widgetCanvas)
        mWidth = widgetWidthPx.toFloat()
        mHeight = widgetHeightPx.toFloat()
        setPaintTheme(theme)
      }
    val document = CoreDocument(remoteClock)
    document.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
    document.initializeContext(remoteContext)
    document.applyDataOperations(remoteContext)
    repeat(5) {
      document.paint(remoteContext, theme)
      if (document.needsRepaint() != 1) return@repeat
    }
    widgetBitmap.eraseColor(0)
    document.paint(remoteContext, theme)

    val framedBitmap: Bitmap =
      Bitmap.createBitmap(totalWidthPx, totalHeightPx, Bitmap.Config.ARGB_8888)
    val framedCanvas = Canvas(framedBitmap)
    framedCanvas.drawColor(FRAME_BACKGROUND_COLOR)
    framedCanvas.drawBitmap(widgetBitmap, paddingPx.toFloat(), paddingPx.toFloat(), null)
    framedBitmap.captureRoboImage("src/test/snapshots/${name}_${scenario.name.lowercase()}.png")
  }

  private fun captureRemoteViews(remoteViews: RemoteViews, widthDp: Int, fileName: String) {
    val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val density: Float = activity.resources.displayMetrics.density
    val widgetWidthPx: Int = (widthDp * density).toInt()
    val widgetHeightPx: Int = (WIDGET_CELL_SIZE_DP * density).toInt()
    val paddingPx: Int = (FRAME_PADDING_DP * density).toInt()
    val totalWidthPx: Int = widgetWidthPx + 2 * paddingPx
    val totalHeightPx: Int = widgetHeightPx + 2 * paddingPx
    val frame =
      FrameLayout(activity).apply {
        setBackgroundColor(FRAME_BACKGROUND_COLOR)
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
      }
    val widgetView: View = remoteViews.apply(activity, frame)
    frame.addView(
      widgetView,
      FrameLayout.LayoutParams(widgetWidthPx, widgetHeightPx, Gravity.CENTER),
    )
    val root = FrameLayout(activity)
    root.addView(frame, FrameLayout.LayoutParams(totalWidthPx, totalHeightPx))
    activity.setContentView(root)
    frame.captureRoboImage("src/test/snapshots/$fileName")
  }

  private fun VisualScenario.toWidgetState(now: Instant): WidgetState =
    when (this) {
      VisualScenario.ACTIVE_20M ->
        // Add 10s buffer past 20m so truncation stays on 20m even if wall clock ticks slightly.
        WidgetState.Configured(
          listOf(
            WidgetItemState.Active(
              DataType.WEIGHT,
              lastRecordTime = now - 20.minutes - 10.seconds,
              originPackage = PACKAGE_FITBIT,
            )
          )
        )
      VisualScenario.ACTIVE_2H ->
        WidgetState.Configured(
          listOf(
            WidgetItemState.Active(DataType.WEIGHT, lastRecordTime = now - 2.hours - 10.seconds)
          )
        )
      VisualScenario.STALE_24H ->
        WidgetState.Configured(
          listOf(
            WidgetItemState.Active(
              DataType.WEIGHT,
              lastRecordTime = now - 24.hours - 10.seconds,
              originPackage = PACKAGE_STRAVA,
            )
          )
        )
      VisualScenario.SYNCING ->
        WidgetState.Configured(
          listOf(WidgetItemState.Syncing(DataType.WEIGHT, hasPermissions = true))
        )
      VisualScenario.UNAUTHORIZED ->
        WidgetState.Configured(listOf(WidgetItemState.Unauthorized(DataType.WEIGHT)))
      VisualScenario.NO_RECORDS ->
        WidgetState.Configured(listOf(WidgetItemState.NoRecords(DataType.WEIGHT)))
      VisualScenario.MULTI_3_2X1 ->
        WidgetState.Configured(
          listOf(
            WidgetItemState.Active(
              DataType.WEIGHT,
              lastRecordTime = now - 20.minutes - 10.seconds,
              originPackage = PACKAGE_FITBIT,
            ),
            WidgetItemState.Active(
              DataType.STEPS,
              lastRecordTime = now - 24.hours - 10.seconds,
              originPackage = PACKAGE_STRAVA,
            ),
            WidgetItemState.Unauthorized(DataType.SLEEP),
          )
        )
      VisualScenario.MULTI_5_3X1 ->
        WidgetState.Configured(
          listOf(
            WidgetItemState.Active(
              DataType.WEIGHT,
              lastRecordTime = now - 20.minutes - 10.seconds,
              originPackage = PACKAGE_FITBIT,
            ),
            WidgetItemState.Active(
              DataType.STEPS,
              lastRecordTime = now - 2.hours - 10.seconds,
              originPackage = PACKAGE_STRAVA,
            ),
            WidgetItemState.Active(
              DataType.HEART_RATE,
              lastRecordTime = now - 24.hours - 10.seconds,
              originPackage = PACKAGE_FITBIT,
            ),
            WidgetItemState.NoRecords(DataType.SLEEP),
            WidgetItemState.Syncing(DataType.HYDRATION, hasPermissions = true),
          )
        )
    }

  private fun createFakeAppIcon(backgroundColour: Int, foregroundColour: Int): BitmapDrawable {
    val sizePx = 64
    val bitmap: Bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColour)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foregroundColour }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 4.5f, paint)
    return BitmapDrawable(context.resources, bitmap)
  }

  private companion object {
    val FIXED_NOW: Instant = Instant.parse("2026-01-01T12:00:00Z")
    const val WIDGET_CELL_SIZE_DP = 88
    const val FRAME_PADDING_DP = 16
    val FRAME_BACKGROUND_COLOR: Int = Color.rgb(0x78, 0x78, 0x78)
    const val PACKAGE_FITBIT = "com.example.fitbit"
    const val PACKAGE_STRAVA = "com.example.strava"
  }
}

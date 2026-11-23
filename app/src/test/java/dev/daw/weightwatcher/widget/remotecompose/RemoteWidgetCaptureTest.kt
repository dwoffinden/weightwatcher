package dev.daw.weightwatcher.widget.remotecompose

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Parcel
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.SystemClock as RemoteSystemClock
import androidx.compose.remote.core.operations.Theme
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import androidx.compose.remote.player.core.platform.AndroidPaintContext
import androidx.compose.remote.player.core.platform.AndroidRemoteContext
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameters
import dev.daw.weightwatcher.DataType
import dev.daw.weightwatcher.hours
import dev.daw.weightwatcher.minutes
import dev.daw.weightwatcher.seconds
import dev.daw.weightwatcher.widget.WidgetItemState
import dev.daw.weightwatcher.widget.WidgetState
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestParameterInjector::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class RemoteWidgetCaptureTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  @Suppress("RestrictedApi")
  fun selectWidgetProfile_version6_returnsWidgetsV6() {
    assertThat(selectWidgetProfile(supportedVersion = 6L))
      .isSameInstanceAs(RcPlatformProfiles.WIDGETS_V6)
  }

  @Test
  @Suppress("RestrictedApi")
  fun selectWidgetProfile_version7_returnsWidgetsV7() {
    assertThat(selectWidgetProfile(supportedVersion = 7L))
      .isSameInstanceAs(RcPlatformProfiles.WIDGETS_V7)
  }

  @Test
  fun captureWidgetRemoteViews_allDataTypes_rendersAfterParcelRoundTripOnSdk36(
    @TestParameter dataType: DataType
  ) =
    runBlocking<Unit> {
      val now = Instant.ofEpochMilli(System.currentTimeMillis())
      val remoteViews: RemoteViews =
        captureWidgetRemoteViews(
          context = context,
          state = WidgetState.Configured(listOf(WidgetItemState.Active(dataType, now))),
          pendingIntentForAction = { WidgetRefreshReceiver.createPendingIntent(context) },
        )

      val unparceled: RemoteViews = parcelRoundTrip(remoteViews)
      val bitmap: Bitmap = renderRemoteViewsToBitmap(unparceled)

      val primaryColor: Int = context.getColor(android.R.color.system_primary_light)
      assertThat(bitmap.containsColor(primaryColor, yRange = 0 until WIDGET_SIZE_PX / 2)).isTrue()
    }

  @Test
  @Suppress("RestrictedApi")
  fun captureWidgetDocumentBytes_widgetsV7Profile_resolvesLightAndDarkThemeFromSingleDocument() =
    runBlocking<Unit> {
      val lastRecordTime = Instant.parse("2026-01-01T00:00:00Z")

      val bytes: ByteArray =
        captureWidgetDocumentBytes(
          context = context,
          state =
            WidgetState.Configured(listOf(WidgetItemState.Active(DataType.WEIGHT, lastRecordTime))),
          profile = RcPlatformProfiles.WIDGETS_V7,
        )

      val lightEvaluation: DocumentEvaluation =
        evaluateDocument(bytes = bytes, now = lastRecordTime + 20.minutes, theme = Theme.LIGHT)
      val darkEvaluation: DocumentEvaluation =
        evaluateDocument(bytes = bytes, now = lastRecordTime + 20.minutes, theme = Theme.DARK)
      val lightPrimary: Int = context.getColor(android.R.color.system_primary_light)
      val darkPrimary: Int = context.getColor(android.R.color.system_primary_dark)
      assertThat(lightEvaluation.renderedTexts).containsExactly("20m")
      assertThat(
          lightEvaluation.bitmap.containsColor(lightPrimary, yRange = 0 until WIDGET_SIZE_PX / 2)
        )
        .isTrue()
      assertThat(
          darkEvaluation.bitmap.containsColor(darkPrimary, yRange = 0 until WIDGET_SIZE_PX / 2)
        )
        .isTrue()
    }

  @Test
  fun captureWidgetDocumentBytes_syncingState_rendersSyncLabelWithoutPeriodicRepaint() =
    runBlocking<Unit> {
      val bytes: ByteArray =
        captureWidgetDocumentBytes(
          context = context,
          state =
            WidgetState.Configured(
              listOf(WidgetItemState.Syncing(DataType.WEIGHT, hasPermissions = true))
            ),
        )

      val evaluation: DocumentEvaluation =
        evaluateDocument(bytes = bytes, now = Instant.parse("2026-01-01T00:00:00Z"))

      assertThat(evaluation.renderedTexts).containsExactly("Sync...")
      assertThat(evaluation.needsRepaintMillis).isLessThan(0)
    }

  @Test
  fun captureWidgetDocumentBytes_unauthorizedState_rendersAuthLabel() =
    runBlocking<Unit> {
      val bytes: ByteArray =
        captureWidgetDocumentBytes(
          context = context,
          state = WidgetState.Configured(listOf(WidgetItemState.Unauthorized(DataType.WEIGHT))),
        )

      val evaluation: DocumentEvaluation =
        evaluateDocument(bytes = bytes, now = Instant.parse("2026-01-01T00:00:00Z"))

      assertThat(evaluation.renderedTexts).containsExactly("Auth")
    }

  @Test
  fun captureWidgetDocumentBytes_noRecordsState_rendersNoneLabel() =
    runBlocking<Unit> {
      val bytes: ByteArray =
        captureWidgetDocumentBytes(
          context = context,
          state = WidgetState.Configured(listOf(WidgetItemState.NoRecords(DataType.WEIGHT))),
        )

      val evaluation: DocumentEvaluation =
        evaluateDocument(bytes = bytes, now = Instant.parse("2026-01-01T00:00:00Z"))

      assertThat(evaluation.renderedTexts).containsExactly("None")
    }

  @Test
  fun captureWidgetDocumentBytes_multipleItems_rendersIndependentStalenessAndTints() =
    runBlocking<Unit> {
      val now = Instant.parse("2026-01-02T12:00:00Z")
      val bytes: ByteArray =
        captureWidgetDocumentBytes(
          context = context,
          state =
            WidgetState.Configured(
              listOf(
                WidgetItemState.Active(DataType.WEIGHT, lastRecordTime = now - 20.minutes),
                WidgetItemState.Active(DataType.STEPS, lastRecordTime = now - 24.hours),
                WidgetItemState.Unauthorized(DataType.SLEEP),
              )
            ),
        )

      val evaluation: DocumentEvaluation = evaluateDocument(bytes = bytes, now = now)

      val primaryColor: Int = context.getColor(android.R.color.system_primary_light)
      val errorColor: Int = context.getColor(android.R.color.system_error_light)
      assertThat(evaluation.renderedTexts).containsExactly("20m", "24h", "Auth").inOrder()
      assertThat(evaluation.bitmap.containsColor(primaryColor, yRange = 0 until WIDGET_SIZE_PX / 2))
        .isTrue()
      assertThat(evaluation.bitmap.containsColor(errorColor, yRange = 0 until WIDGET_SIZE_PX / 2))
        .isTrue()
    }

  @Test
  @TestParameters("{offsetIntoMinute: '0s', expectedRepaintMillis: 60002}")
  @TestParameters("{offsetIntoMinute: '15s', expectedRepaintMillis: 45002}")
  fun captureWidgetDocumentBytes_activeState_schedulesRepaintAlignedToNextMinute(
    offsetIntoMinute: Duration,
    expectedRepaintMillis: Int,
  ) =
    runBlocking<Unit> {
      val lastRecordTime = Instant.parse("2026-01-01T00:00:00Z")
      val bytes: ByteArray =
        captureWidgetDocumentBytes(
          context = context,
          state =
            WidgetState.Configured(listOf(WidgetItemState.Active(DataType.WEIGHT, lastRecordTime))),
        )

      val evaluation: DocumentEvaluation =
        evaluateDocument(bytes = bytes, now = lastRecordTime + 20.minutes + offsetIntoMinute)

      assertThat(evaluation.needsRepaintMillis).isEqualTo(expectedRepaintMillis)
    }

  @Test
  @TestParameters("{staleness: '-5m', expectedText: '-5m'}")
  @TestParameters("{staleness: '0m', expectedText: '0m'}")
  @TestParameters("{staleness: '20m', expectedText: '20m'}")
  @TestParameters("{staleness: '58m', expectedText: '58m'}")
  @TestParameters("{staleness: '59m', expectedText: '59m'}")
  @TestParameters("{staleness: '60m', expectedText: '1h'}")
  @TestParameters("{staleness: '89m', expectedText: '1h'}")
  @TestParameters("{staleness: '20h', expectedText: '20h'}")
  @TestParameters("{staleness: '71h', expectedText: '71h'}")
  @TestParameters("{staleness: '72h', expectedText: '3d'}")
  @TestParameters("{staleness: '95h', expectedText: '3d'}")
  @TestParameters("{staleness: '96h', expectedText: '4d'}")
  @TestParameters("{staleness: '700h', expectedText: '29d'}")
  @TestParameters("{staleness: '8759h', expectedText: '364d'}")
  @TestParameters("{staleness: '8760h', expectedText: '1y'}")
  @TestParameters("{staleness: '17520h', expectedText: '2y'}")
  fun captureWidgetDocumentBytes_singleDocument_updatesTextAcrossElapsedTime(
    staleness: Duration,
    expectedText: String,
  ) =
    runBlocking<Unit> {
      val lastRecordTime = Instant.parse("2026-01-01T00:00:00Z")
      val bytes: ByteArray =
        captureWidgetDocumentBytes(
          context = context,
          state =
            WidgetState.Configured(listOf(WidgetItemState.Active(DataType.WEIGHT, lastRecordTime))),
        )

      val evaluation: DocumentEvaluation =
        evaluateDocument(bytes = bytes, now = lastRecordTime + staleness)

      assertThat(evaluation.renderedTexts).containsExactly(expectedText)
    }

  @Test
  fun captureWidgetDocumentBytes_singleDocument_flipsIconTintAfterStaleThreshold() =
    runBlocking<Unit> {
      val lastRecordTime = Instant.parse("2026-01-01T00:00:00Z")
      val bytes: ByteArray =
        captureWidgetDocumentBytes(
          context = context,
          state =
            WidgetState.Configured(listOf(WidgetItemState.Active(DataType.WEIGHT, lastRecordTime))),
        )

      val atThreshold: DocumentEvaluation =
        evaluateDocument(bytes = bytes, now = lastRecordTime + 20.hours)
      val pastThreshold: DocumentEvaluation =
        evaluateDocument(bytes = bytes, now = lastRecordTime + 20.hours + 1.seconds)

      val primaryColor: Int = context.getColor(android.R.color.system_primary_light)
      val errorColor: Int = context.getColor(android.R.color.system_error_light)
      assertThat(
          atThreshold.bitmap.containsColor(primaryColor, yRange = 0 until WIDGET_SIZE_PX / 2)
        )
        .isTrue()
      assertThat(
          pastThreshold.bitmap.containsColor(errorColor, yRange = 0 until WIDGET_SIZE_PX / 2)
        )
        .isTrue()
    }

  private fun parcelRoundTrip(remoteViews: RemoteViews): RemoteViews {
    val parcel: Parcel = Parcel.obtain()
    try {
      remoteViews.writeToParcel(parcel, 0)
      parcel.setDataPosition(0)
      return RemoteViews.CREATOR.createFromParcel(parcel)
    } finally {
      parcel.recycle()
    }
  }

  private fun renderRemoteViewsToBitmap(remoteViews: RemoteViews): Bitmap {
    val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val container = FrameLayout(activity)
    activity.setContentView(container)
    val view: View = remoteViews.apply(activity, container)
    container.addView(view)
    val spec: Int = MeasureSpec.makeMeasureSpec(WIDGET_SIZE_PX, MeasureSpec.EXACTLY)
    container.measure(spec, spec)
    container.layout(0, 0, WIDGET_SIZE_PX, WIDGET_SIZE_PX)
    val bitmap: Bitmap =
      Bitmap.createBitmap(WIDGET_SIZE_PX, WIDGET_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    repeat(3) { container.draw(canvas) }
    bitmap.eraseColor(0)
    container.draw(canvas)
    return bitmap
  }

  private data class DocumentEvaluation(
    val renderedTexts: List<String>,
    val needsRepaintMillis: Int,
    val bitmap: Bitmap,
  )

  @Suppress("RestrictedApi")
  private fun evaluateDocument(
    bytes: ByteArray,
    now: Instant,
    theme: Int = Theme.LIGHT,
  ): DocumentEvaluation {
    val remoteClock = RemoteSystemClock(Clock.fixed(now, ZoneOffset.UTC))
    val bitmap: Bitmap =
      Bitmap.createBitmap(WIDGET_SIZE_PX, WIDGET_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val renderedTexts = mutableListOf<String>()
    val remoteContext =
      RecordingAndroidRemoteContext(
        appContext = context,
        clock = remoteClock,
        canvas = canvas,
        renderedTexts = renderedTexts,
      )
    remoteContext.setPaintTheme(theme)

    val coreDocument = CoreDocument(remoteClock)
    coreDocument.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
    coreDocument.initializeContext(remoteContext)
    coreDocument.applyDataOperations(remoteContext)
    // Settle initial multi-stage propagation (TimeAttribute -> FloatExpression -> TextFromFloat ->
    // TextLookupInt -> RemoteText relayout) before capturing the steady-state frame and repaint.
    repeat(5) {
      coreDocument.paint(remoteContext, theme)
      if (coreDocument.needsRepaint() != 1) return@repeat
    }
    renderedTexts.clear()
    bitmap.eraseColor(0)
    coreDocument.paint(remoteContext, theme)

    return DocumentEvaluation(
      renderedTexts = renderedTexts,
      needsRepaintMillis = coreDocument.needsRepaint(),
      bitmap = bitmap,
    )
  }

  @Suppress("RestrictedApi")
  private class RecordingAndroidRemoteContext(
    appContext: Context,
    clock: RemoteSystemClock,
    canvas: Canvas,
    renderedTexts: MutableList<String>,
  ) : AndroidRemoteContext(clock) {
    init {
      setAndroidContext(appContext)
      setDensity(appContext.resources.displayMetrics.density)
      setDocLoadTime()
      setAnimationEnabled(true)
      currentTime = clock.millis()
      useCanvas(canvas)
      mWidth = canvas.width.toFloat()
      mHeight = canvas.height.toFloat()
      mPaintContext = RecordingPaintContext(this, canvas, renderedTexts)
    }
  }

  @Suppress("RestrictedApi")
  private class RecordingPaintContext(
    context: AndroidRemoteContext,
    canvas: Canvas,
    private val renderedTexts: MutableList<String>,
  ) : AndroidPaintContext(context, canvas) {
    override fun drawTextRun(
      textId: Int,
      start: Int,
      end: Int,
      contextStart: Int,
      contextEnd: Int,
      x: Float,
      y: Float,
      rtl: Boolean,
    ) {
      getText(textId)?.let(renderedTexts::add)
      super.drawTextRun(textId, start, end, contextStart, contextEnd, x, y, rtl)
    }

    override fun layoutComplexText(
      textId: Int,
      start: Int,
      end: Int,
      alignment: Int,
      overflow: Int,
      maxLines: Int,
      maxWidth: Float,
      maxHeight: Float,
      letterSpacing: Float,
      lineHeightAdd: Float,
      lineHeightMultiplier: Float,
      lineBreakStrategy: Int,
      hyphenationFrequency: Int,
      justificationMode: Int,
      useUnderline: Boolean,
      strikethrough: Boolean,
      flags: Int,
    ): RcPlatformServices.ComputedTextLayout? {
      getText(textId)?.let(renderedTexts::add)
      return super.layoutComplexText(
        textId,
        start,
        end,
        alignment,
        overflow,
        maxLines,
        maxWidth,
        maxHeight,
        letterSpacing,
        lineHeightAdd,
        lineHeightMultiplier,
        lineBreakStrategy,
        hyphenationFrequency,
        justificationMode,
        useUnderline,
        strikethrough,
        flags,
      )
    }
  }

  private companion object {
    const val WIDGET_SIZE_PX = 200

    fun Bitmap.containsColor(targetColor: Int, yRange: IntRange): Boolean {
      for (y in yRange) {
        for (x in 0 until width) {
          if (pixelsMatchRgb(getPixel(x, y), targetColor)) {
            return true
          }
        }
      }
      return false
    }

    fun pixelsMatchRgb(pixel: Int, targetColor: Int): Boolean {
      if ((pixel ushr 24) < 0xF0) {
        return false
      }
      val dr: Int = abs(((pixel shr 16) and 0xFF) - ((targetColor shr 16) and 0xFF))
      val dg: Int = abs(((pixel shr 8) and 0xFF) - ((targetColor shr 8) and 0xFF))
      val db: Int = abs((pixel and 0xFF) - (targetColor and 0xFF))
      return dr <= 2 && dg <= 2 && db <= 2
    }
  }
}

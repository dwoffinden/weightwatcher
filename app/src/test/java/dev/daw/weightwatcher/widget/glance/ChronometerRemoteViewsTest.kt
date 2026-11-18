package dev.daw.weightwatcher.widget.glance

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameters
import dev.daw.weightwatcher.R
import dev.daw.weightwatcher.WeightWorker
import dev.daw.weightwatcher.initializeWorkManager
import dev.daw.weightwatcher.minutes
import dev.daw.weightwatcher.placeWidget
import java.time.Duration
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestParameterInjector::class)
@Config(sdk = [34], application = Application::class)
class ChronometerRemoteViewsTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    initializeWorkManager(context)
  }

  @Test
  @TestParameters("{staleness: '20m', elapsedRealtime: '2h', expectedBase: '100m'}")
  @TestParameters("{staleness: '0m', elapsedRealtime: '45m', expectedBase: '45m'}")
  @TestParameters("{staleness: '-5m', elapsedRealtime: '1h', expectedBase: '65m'}")
  fun createChronometerRemoteViews_setsBaseFromElapsedRealtimeMinusStaleness(
    staleness: Duration,
    elapsedRealtime: Duration,
    expectedBase: Duration,
  ) {
    val remoteViews: RemoteViews =
      createChronometerRemoteViews(context, staleness, elapsedRealtime = elapsedRealtime)

    val inflated: View = remoteViews.apply(context, FrameLayout(context))

    val chronometer: Chronometer = inflated.findViewById(R.id.widget_chronometer)
    assertThat(chronometer.base).isEqualTo(expectedBase.toMillis())
  }

  @Test
  fun createChronometerRemoteViews_advancingElapsedRealtime_updatesDisplayedText() {
    val startElapsed: Duration = Duration.ofMillis(SystemClock.elapsedRealtime())
    val remoteViews: RemoteViews =
      createChronometerRemoteViews(context, staleness = 20.minutes, elapsedRealtime = startElapsed)
    val chronometer = assertIs<Chronometer>(remoteViews.apply(context, FrameLayout(context)))
    assertThat(chronometer.text.toString()).isEqualTo("20:00")

    ShadowSystemClock.advanceBy(5.minutes)
    chronometer.base = chronometer.base // Triggers Chronometer.updateText(elapsedRealtime()).

    assertThat(chronometer.text.toString()).isEqualTo("25:00")
  }

  @Test
  fun refreshCallback_onAction_enqueuesOneTimeSyncWork() =
    runBlocking<Unit> {
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(placeWidget(context))

      RefreshCallback().onAction(context, glanceId, actionParametersOf())

      val workInfos: List<WorkInfo> =
        WorkManager.getInstance(context)
          .getWorkInfosForUniqueWork(WeightWorker.ONE_TIME_WORK_NAME)
          .get()
      assertThat(workInfos).isNotEmpty()
    }
}

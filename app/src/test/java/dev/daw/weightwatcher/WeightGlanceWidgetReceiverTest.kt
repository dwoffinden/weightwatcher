package dev.daw.weightwatcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeightGlanceWidgetReceiverTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    initializeWorkManager(context)
  }

  @Test
  fun glanceAppWidget_receiverCreatedByReflection_isAvailable() {
    // Glance creates receivers this way to map them to their widgets, without injecting them.
    val receiver: WeightGlanceWidgetReceiver =
      WeightGlanceWidgetReceiver::class.java.getDeclaredConstructor().newInstance()

    assertThat(receiver.glanceAppWidget).isInstanceOf(WeightGlanceWidget::class.java)
  }

  @Test
  fun initialLayout_inflatesAsRemoteViews() {
    val remoteViews = RemoteViews(context.packageName, R.layout.widget_initial)

    val view: View = remoteViews.apply(context, FrameLayout(context))

    assertThat(view.id).isEqualTo(android.R.id.background)
  }

  @Test
  fun onReceive_myPackageReplacedWithPlacedWidgets_enqueuesSyncWork() {
    placeWidget(context)
    val receiver = WeightGlanceWidgetReceiver()

    receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

    val workInfos: List<WorkInfo> =
      WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(WeightWorker.ONE_TIME_WORK_NAME)
        .get()
    assertThat(workInfos).isNotEmpty()
  }

  @Test
  fun onReceive_myPackageReplacedWithNoWidgets_doesNotEnqueueWork() {
    val receiver = WeightGlanceWidgetReceiver()

    receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

    val workInfos: List<WorkInfo> =
      WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(WeightWorker.ONE_TIME_WORK_NAME)
        .get()
    assertThat(workInfos).isEmpty()
  }
}

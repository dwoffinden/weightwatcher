package dev.daw.weightwatcher

import android.app.Application
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeightGlanceWidgetReceiverTest {

  @Test
  fun glanceAppWidget_receiverCreatedByReflection_isAvailable() {
    // Glance creates receivers this way to map them to their widgets, without injecting them.
    val receiver: WeightGlanceWidgetReceiver =
      WeightGlanceWidgetReceiver::class.java.getDeclaredConstructor().newInstance()

    assertThat(receiver.glanceAppWidget).isInstanceOf(WeightGlanceWidget::class.java)
  }
}

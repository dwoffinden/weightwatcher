package dev.daw.weightwatcher.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class AppBadgeBitmapTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun loadAppBadgeBitmap_emptyPackage_returnsNull() {
    assertThat(loadAppBadgeBitmap(context, "")).isNull()
  }

  @Test
  fun loadAppBadgeBitmap_unknownPackage_returnsNull() {
    assertThat(loadAppBadgeBitmap(context, "com.example.not.installed")).isNull()
  }

  @Test
  fun loadAppBadgeBitmap_installedPackage_returnsCircularBitmap() {
    val iconColour: Int = Color.rgb(0x00, 0xB0, 0xB9)
    shadowOf(context.packageManager)
      .setApplicationIcon("com.example.fitbit", ColorDrawable(iconColour))

    val bitmap: Bitmap? = loadAppBadgeBitmap(context, "com.example.fitbit", sizePx = 48)

    assertThat(bitmap).isNotNull()
    assertThat(bitmap!!.width).isEqualTo(48)
    assertThat(bitmap.height).isEqualTo(48)
    assertThat(bitmap.getPixel(24, 24)).isEqualTo(iconColour)
    assertThat(Color.alpha(bitmap.getPixel(0, 0))).isEqualTo(0)
    assertThat(Color.alpha(bitmap.getPixel(47, 47))).isEqualTo(0)
  }

  @Test
  fun loadAppBadgeBitmap_repeatedCall_returnsCachedInstance() {
    shadowOf(context.packageManager)
      .setApplicationIcon("com.example.cached", ColorDrawable(Color.MAGENTA))

    val first: Bitmap? = loadAppBadgeBitmap(context, "com.example.cached", sizePx = 48)
    val second: Bitmap? = loadAppBadgeBitmap(context, "com.example.cached", sizePx = 48)

    assertThat(first).isNotNull()
    assertThat(second).isSameInstanceAs(first)
  }
}

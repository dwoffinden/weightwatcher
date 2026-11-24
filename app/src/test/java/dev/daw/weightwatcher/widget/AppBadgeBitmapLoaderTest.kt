package dev.daw.weightwatcher.widget

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.health.connect.client.HealthConnectClient
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
class AppBadgeBitmapLoaderTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val loader = AppBadgeBitmapLoader(context)

  @Test
  fun load_emptyPackage_returnsMissing() {
    assertThat(loader.load("")).isEqualTo(AppIcon.Missing)
  }

  @Test
  fun load_unknownPackage_returnsMissingAndDoesNotCacheMiss() {
    val packageName = "com.example.installed.later"
    assertThat(loader.load(packageName)).isEqualTo(AppIcon.Missing)
    val iconColour: Int = Color.rgb(0x1E, 0x88, 0xE5)
    shadowOf(context.packageManager).setApplicationIcon(packageName, ColorDrawable(iconColour))

    val loaded: AppIcon = loader.load(packageName)

    assertThat(loaded).isInstanceOf(AppIcon.Loaded::class.java)
    assertThat((loaded as AppIcon.Loaded).resolvedPackage).isEqualTo(packageName)
    assertThat(loaded.bitmap.getPixel(24, 24)).isEqualTo(iconColour)
  }

  @Test
  fun load_installedPackage_returnsCircularBitmap() {
    val iconColour: Int = Color.rgb(0x00, 0xB0, 0xB9)
    shadowOf(context.packageManager)
      .setApplicationIcon("com.example.fitbit", ColorDrawable(iconColour))

    val icon: AppIcon = loader.load("com.example.fitbit")

    assertThat(icon).isInstanceOf(AppIcon.Loaded::class.java)
    val loaded = icon as AppIcon.Loaded
    assertThat(loaded.resolvedPackage).isEqualTo("com.example.fitbit")
    assertThat(loaded.bitmap.width).isEqualTo(48)
    assertThat(loaded.bitmap.height).isEqualTo(48)
    assertThat(loaded.bitmap.getPixel(24, 24)).isEqualTo(iconColour)
    assertThat(Color.alpha(loaded.bitmap.getPixel(0, 0))).isEqualTo(0)
    assertThat(Color.alpha(loaded.bitmap.getPixel(47, 47))).isEqualTo(0)
  }

  @Test
  fun load_repeatedCall_returnsCachedInstance() {
    shadowOf(context.packageManager)
      .setApplicationIcon("com.example.cached", ColorDrawable(Color.MAGENTA))

    val first: AppIcon = loader.load("com.example.cached")
    val second: AppIcon = loader.load("com.example.cached")

    assertThat(first).isInstanceOf(AppIcon.Loaded::class.java)
    assertThat(second).isSameInstanceAs(first)
  }

  @Test
  fun load_nativeHealthConnectSpn_fallsBackToControllerIconAndDeduplicates() {
    val controllerPackage = "com.google.android.healthconnect.controller"
    val controllerActivity =
      ComponentName(controllerPackage, "$controllerPackage.TrampolineActivity")
    val healthConnectColour: Int = Color.rgb(0x42, 0x85, 0xF4)
    val packageManager = shadowOf(context.packageManager)
    packageManager.addActivityIfNotPresent(controllerActivity)
    packageManager.addIntentFilterForActivity(
      controllerActivity,
      IntentFilter(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS),
    )
    packageManager.setApplicationIcon(controllerPackage, ColorDrawable(healthConnectColour))

    val phoneIcon: AppIcon =
      loader.load("com.android.healthconnect.phone.jfd7c4cd991103e46a8e25c5538dae87d")
    val watchSpnIcon: AppIcon = loader.load("com.android.healthconnect.watch.j0123456789abcdef")

    assertThat(phoneIcon).isInstanceOf(AppIcon.Loaded::class.java)
    val loadedPhone = phoneIcon as AppIcon.Loaded
    assertThat(loadedPhone.resolvedPackage).isEqualTo(controllerPackage)
    assertThat(loadedPhone.bitmap.getPixel(24, 24)).isEqualTo(healthConnectColour)
    assertThat(watchSpnIcon).isEqualTo(phoneIcon)
    assertThat(watchSpnIcon).isSameInstanceAs(phoneIcon)
  }

  @Test
  fun load_uninstalledNonSpnHealthConnectPackage_returnsMissing() {
    val controllerPackage = "com.google.android.healthconnect.controller"
    val controllerActivity =
      ComponentName(controllerPackage, "$controllerPackage.TrampolineActivity")
    val packageManager = shadowOf(context.packageManager)
    packageManager.addActivityIfNotPresent(controllerActivity)
    packageManager.addIntentFilterForActivity(
      controllerActivity,
      IntentFilter(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS),
    )
    packageManager.setApplicationIcon(controllerPackage, ColorDrawable(Color.BLUE))

    val result: AppIcon = loader.load("com.android.healthconnect.testapps.uninstalled")

    assertThat(result).isEqualTo(AppIcon.Missing)
  }

  @Test
  fun load_nativeHealthConnectSpnWithoutController_returnsMissing() {
    val result: AppIcon =
      loader.load("com.android.healthconnect.phone.jfd7c4cd991103e46a8e25c5538dae87d")

    assertThat(result).isEqualTo(AppIcon.Missing)
  }
}

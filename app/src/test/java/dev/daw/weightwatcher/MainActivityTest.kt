package dev.daw.weightwatcher

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(HealthConnectModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class MainActivityTest {

  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
  @get:Rule(order = 1) val composeTestRule = createEmptyComposeRule()

  private val permissionController = FakePermissionController(grantAll = false)

  @BindValue @HealthConnectSdkStatus var sdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE

  @BindValue
  val healthConnectClient: HealthConnectClient =
    FakeHealthConnectClient(permissionController = permissionController)

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (!WorkManager.isInitialized()) {
      WorkManager.initialize(context, Configuration.Builder().build())
    }
  }

  @Test
  fun sdkUnavailable_showsUnsupportedMessage() {
    sdkStatus = HealthConnectClient.SDK_UNAVAILABLE

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("WeightWatcher Widget Setup").assertIsDisplayed()
      composeTestRule
        .onNodeWithText("Health Connect is not supported on this device.")
        .assertIsDisplayed()
    }
  }

  @Test
  fun missingPermissions_showsGrantPermissionsPrompt() {
    sdkStatus = HealthConnectClient.SDK_AVAILABLE

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule
        .onNodeWithText("Need Health Connect read permissions for weight.")
        .assertIsDisplayed()
      composeTestRule.onNodeWithText("Grant Permissions").assertIsDisplayed()
      composeTestRule.onNodeWithText("Open Health Connect").assertIsDisplayed()
    }
  }

  @Test
  fun permissionsGranted_showsGrantedMessage() {
    sdkStatus = HealthConnectClient.SDK_AVAILABLE
    permissionController.grantPermissions(SyncManager.REQUIRED_PERMISSIONS)

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("Permissions granted! 😊").assertIsDisplayed()
      composeTestRule
        .onNodeWithText("You can now add the widget to your home screen.")
        .assertIsDisplayed()
      composeTestRule.onNodeWithText("Open Health Connect").assertIsDisplayed()
    }
  }
}

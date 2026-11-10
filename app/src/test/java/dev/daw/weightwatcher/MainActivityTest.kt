package dev.daw.weightwatcher

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
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

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val permissionController = FakePermissionController(grantAll = false)

  @BindValue @HealthConnectSdkStatus var sdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE

  @BindValue
  val healthConnectClient: HealthConnectClient =
    FakeHealthConnectClient(permissionController = permissionController)

  @Before
  fun setUp() {
    initializeWorkManager(context)
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
  fun permissionsGranted_showsPinPrompt() {
    sdkStatus = HealthConnectClient.SDK_AVAILABLE
    permissionController.grantPermissions(DataType.WEIGHT.requiredPermissions)

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("Permissions granted! 😊").assertIsDisplayed()
      composeTestRule
        .onNodeWithText("You can now add the widget to your home screen.")
        .assertIsDisplayed()
      composeTestRule.onNodeWithText("Open Health Connect").assertIsDisplayed()
    }
  }

  @Test
  fun selectingTypeWithoutPermission_promptsForThatTypesPermission() {
    sdkStatus = HealthConnectClient.SDK_AVAILABLE
    permissionController.grantPermissions(DataType.WEIGHT.requiredPermissions)

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("Steps").performClick()

      composeTestRule
        .onNodeWithText("Need Health Connect read permissions for steps.")
        .assertIsDisplayed()
    }
  }

  @Test
  fun configureMode_savingType_storesItOnWidgetAndReturnsOk() {
    sdkStatus = HealthConnectClient.SDK_AVAILABLE
    permissionController.grantPermissions(DataType.STEPS.requiredPermissions)
    val appWidgetId: Int = placeWidget(context)
    val intent =
      Intent(context, MainActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    ActivityScenario.launchActivityForResult<MainActivity>(intent).use { scenario ->
      lateinit var activity: MainActivity
      scenario.onActivity { activity = it }
      composeTestRule.onNodeWithText("Steps").performClick()
      composeTestRule.onNodeWithText("Save Steps Widget").performClick()

      // Saving is asynchronous.
      composeTestRule.waitUntil(timeoutMillis = 5_000) { activity.isFinishing }
      val result: Instrumentation.ActivityResult = scenario.result
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
      val widgetDataType: DataType? = runBlocking {
        WeightGlanceWidget.getWidgetDataType(context, glanceId)
      }
      assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
      assertThat(widgetDataType).isEqualTo(DataType.STEPS)
    }
  }
}

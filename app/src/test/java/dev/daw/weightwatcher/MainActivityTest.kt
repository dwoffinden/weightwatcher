package dev.daw.weightwatcher

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableSet
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
@Config(sdk = [34], qualifiers = "w412dp-h915dp", application = HiltTestApplication::class)
class MainActivityTest {

  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
  @get:Rule(order = 1) val composeTestRule = createEmptyComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val permissionController = FakePermissionController(grantAll = false)

  private val fakeHealthConnectAccess =
    FakeHealthConnectAccess(
      FakeHealthConnectClient(permissionController = permissionController),
      sdkStatus = HealthConnectClient.SDK_UNAVAILABLE,
    )

  @BindValue val healthConnectAccess: HealthConnectAccess = fakeHealthConnectAccess

  @Before
  fun setUp() {
    initializeWorkManager(context)
  }

  @Test
  fun sdkUnavailable_showsUnsupportedMessage() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_UNAVAILABLE

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("WeightWatcher Widget Setup").assertIsDisplayed()
      composeTestRule
        .onNodeWithText("Health Connect is not supported on this device.")
        .assertIsDisplayed()
    }
  }

  @Test
  fun missingPermissions_showsGrantPermissionsPromptWithoutScrolling() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule
        .onNodeWithText("Need Health Connect read permissions for weight.")
        .assertIsDisplayed()
      composeTestRule.onNodeWithText("Grant Permissions").assertIsDisplayed()
      composeTestRule.onNodeWithText("Open Health Connect").assertIsDisplayed()
    }
  }

  @Test
  fun permissionsGranted_showsPinPromptWithoutScrolling() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE
    permissionController.grantPermissions(SyncManager.requiredPermissionsFor(DataType.WEIGHT))

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("Permissions granted! 😊").assertIsDisplayed()
      composeTestRule
        .onNodeWithText("You can now add the widget to your home screen.")
        .assertIsDisplayed()
      composeTestRule.onNodeWithText("Open Health Connect").assertIsDisplayed()
    }
  }

  @Test
  fun selectingSecondTypeWithoutPermission_promptsForMissingTypeAndShowsOrderPreview() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE
    permissionController.grantPermissions(SyncManager.requiredPermissionsFor(DataType.WEIGHT))

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("Steps").performScrollTo().performClick()

      composeTestRule.onNodeWithText("1. Weight").assertIsDisplayed()
      composeTestRule.onNodeWithText("2. Steps").assertIsDisplayed()
      composeTestRule
        .onNodeWithText("Need Health Connect read permissions for steps.")
        .assertIsDisplayed()
    }
  }

  @Test
  fun selectingMultiplePermittedTypes_showsResizeTip() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE
    permissionController.grantPermissions(
      SyncManager.requiredPermissionsFor(DataType.WEIGHT) +
        SyncManager.requiredPermissionsFor(DataType.STEPS) +
        SyncManager.requiredPermissionsFor(DataType.SLEEP)
    )

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("Steps").performScrollTo().performClick()
      composeTestRule.onNodeWithText("Sleep").performScrollTo().performClick()

      composeTestRule
        .onNodeWithText("Tip: resize to 2x1 on your home screen for 3 types.")
        .assertIsDisplayed()
    }
  }

  @Test
  @Config(qualifiers = "w360dp-h600dp")
  fun mediumHeightScreen_pinsButtonsWhileScrollingDataTypes() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("Grant Permissions").assertIsDisplayed()
      composeTestRule.onNodeWithText("Open Health Connect").assertIsDisplayed()

      composeTestRule.onNodeWithText("Weight").performScrollTo().assertIsDisplayed()

      composeTestRule.onNodeWithText("Grant Permissions").assertIsDisplayed()
      composeTestRule.onNodeWithText("Open Health Connect").assertIsDisplayed()
    }
  }

  @Test
  @Config(qualifiers = "w640dp-h360dp")
  fun shortScreen_scrollsEntireContent() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("Active Calories").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithText("Open Health Connect").performScrollTo().assertIsDisplayed()
    }
  }

  @Test
  fun singleSelection_alwaysShowsWidgetOrderSectionWithResizeHint() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithText("Widget order (1)").assertIsDisplayed()
      composeTestRule.onNodeWithText("1. Weight").assertIsDisplayed()
      composeTestRule
        .onNodeWithText("Tap below to add, tap a slot to remove · resize widget wider to fit more")
        .assertIsDisplayed()
    }
  }

  @Test
  fun configureMode_alreadyWideEnough_hidesResizeTip() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE
    permissionController.grantPermissions(
      SyncManager.requiredPermissionsFor(DataType.WEIGHT) +
        SyncManager.requiredPermissionsFor(DataType.STEPS) +
        SyncManager.requiredPermissionsFor(DataType.SLEEP)
    )
    val appWidgetId: Int = placeWidget(context)
    val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) }
    AppWidgetManager.getInstance(context).updateAppWidgetOptions(appWidgetId, options)
    val intent =
      Intent(context, MainActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    ActivityScenario.launchActivityForResult<MainActivity>(intent).use {
      composeTestRule.onNodeWithText("Steps").performScrollTo().performClick()
      composeTestRule.onNodeWithText("Sleep").performScrollTo().performClick()

      composeTestRule.onNodeWithText("Save Widget").assertIsDisplayed()
      composeTestRule
        .onNodeWithText("Tip: resize to 2x1 on your home screen for 3 types.")
        .assertDoesNotExist()
    }
  }

  @Test
  fun configureMode_narrowWidth_capsAtTwoAndPromptsResize() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE
    val appWidgetId: Int = placeWidget(context)
    val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 73) }
    AppWidgetManager.getInstance(context).updateAppWidgetOptions(appWidgetId, options)
    val intent =
      Intent(context, MainActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    ActivityScenario.launchActivityForResult<MainActivity>(intent).use {
      composeTestRule.onNodeWithText("Steps").performScrollTo().performClick()

      composeTestRule.onNodeWithText("Widget order (2/2)").assertIsDisplayed()
      composeTestRule.onNodeWithText("Widget full, resize wider for more slots").assertIsDisplayed()
      composeTestRule.onNodeWithText("Sleep").assertIsNotEnabled()
    }
  }

  @Test
  fun configureMode_twoCellWidth_capsAtFourAndPromptsResize() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE
    val appWidgetId: Int = placeWidget(context)
    val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 158) }
    AppWidgetManager.getInstance(context).updateAppWidgetOptions(appWidgetId, options)
    val intent =
      Intent(context, MainActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    ActivityScenario.launchActivityForResult<MainActivity>(intent).use {
      val additionalTypes: List<DataType> =
        DataType.entries.filter { it != DataType.WEIGHT }.take(3)
      for (dataType in additionalTypes) {
        composeTestRule.onNodeWithText(dataType.displayName).performScrollTo().performClick()
      }
      val extraType: DataType =
        DataType.entries.first { it != DataType.WEIGHT && it !in additionalTypes }

      composeTestRule.onNodeWithText("Widget order (4/4)").assertIsDisplayed()
      composeTestRule.onNodeWithText("Widget full, resize wider for more slots").assertIsDisplayed()
      composeTestRule.onNodeWithText(extraType.displayName).assertIsNotEnabled()
    }
  }

  @Test
  fun configureMode_threeCellWidth_capsAtSeven() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE
    val appWidgetId: Int = placeWidget(context)
    val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 245) }
    AppWidgetManager.getInstance(context).updateAppWidgetOptions(appWidgetId, options)
    val intent =
      Intent(context, MainActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    ActivityScenario.launchActivityForResult<MainActivity>(intent).use {
      val additionalTypes: List<DataType> =
        DataType.entries.filter { it != DataType.WEIGHT }.take(6)
      for (dataType in additionalTypes) {
        composeTestRule.onNodeWithText(dataType.displayName).performScrollTo().performClick()
      }
      val extraType: DataType =
        DataType.entries.first { it != DataType.WEIGHT && it !in additionalTypes }

      composeTestRule.onNodeWithText("Widget order (7/7)").assertIsDisplayed()
      composeTestRule.onNodeWithText("Widget full, resize wider for more slots").assertIsDisplayed()
      composeTestRule.onNodeWithText(extraType.displayName).assertIsNotEnabled()
    }
  }

  @Test
  fun configureMode_fourCellWidth_allowsNineTypes() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE
    val appWidgetId: Int = placeWidget(context)
    val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 330) }
    AppWidgetManager.getInstance(context).updateAppWidgetOptions(appWidgetId, options)
    val intent =
      Intent(context, MainActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    ActivityScenario.launchActivityForResult<MainActivity>(intent).use {
      val additionalTypes: List<DataType> =
        DataType.entries.filter { it != DataType.WEIGHT }.take(8)
      for (dataType in additionalTypes) {
        composeTestRule.onNodeWithText(dataType.displayName).performScrollTo().performClick()
      }
      val extraType: DataType =
        DataType.entries.first { it != DataType.WEIGHT && it !in additionalTypes }

      composeTestRule.onNodeWithText("Widget order (9/9)").assertIsDisplayed()
      composeTestRule.onNodeWithText("Widget full, resize wider for more slots").assertIsDisplayed()
      composeTestRule.onNodeWithText(extraType.displayName).assertIsNotEnabled()
    }
  }

  @Test
  fun configureMode_savingMultiplePermittedTypes_storesOrderedListAndReturnsOk() {
    fakeHealthConnectAccess.sdkStatus = HealthConnectClient.SDK_AVAILABLE
    permissionController.grantPermissions(
      SyncManager.requiredPermissionsFor(DataType.STEPS) +
        SyncManager.requiredPermissionsFor(DataType.SLEEP)
    )
    val appWidgetId: Int = placeWidget(context)
    val intent =
      Intent(context, MainActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    ActivityScenario.launchActivityForResult<MainActivity>(intent).use { scenario ->
      lateinit var activity: MainActivity
      scenario.onActivity { activity = it }
      // Deselect default Weight, then pick Sleep then Steps to verify selection order is preserved.
      composeTestRule.onNodeWithText("Weight").performScrollTo().performClick()
      composeTestRule.onNodeWithText("Sleep").performScrollTo().performClick()
      composeTestRule.onNodeWithText("Steps").performScrollTo().performClick()
      composeTestRule.onNodeWithText("Save Widget").performClick()

      // Saving is asynchronous.
      composeTestRule.waitUntil(timeoutMillis = 5_000) { activity.isFinishing }
      val result: Instrumentation.ActivityResult = scenario.result
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
      val widgetDataTypes: ImmutableSet<DataType> = runBlocking {
        WeightGlanceWidget.getWidgetDataTypes(context, glanceId)
      }
      assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
      assertThat(widgetDataTypes).containsExactly(DataType.SLEEP, DataType.STEPS).inOrder()
    }
  }
}

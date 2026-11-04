package dev.daw.weightwatcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun appLaunchesAndShowsSetupScreen() {
    composeTestRule.onNodeWithText("WeightWatcher Widget Setup").assertIsDisplayed()
    composeTestRule
      .onNodeWithText("Health Connect is not supported on this device.")
      .assertIsDisplayed()
  }
}

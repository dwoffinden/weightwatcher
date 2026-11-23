package dev.daw.weightwatcher

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun WeightWatcherTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val context: Context = LocalContext.current
  // Compose Material 3's dynamic*ColorScheme omits the API 34 system_error_* roles and falls back
  // to static M3 2021 error colors, so fill them in from the platform tokens to match the widgets.
  val colorScheme: ColorScheme =
    if (darkTheme) {
      dynamicDarkColorScheme(context)
        .copy(
          error = Color(context.getColor(android.R.color.system_error_dark)),
          onError = Color(context.getColor(android.R.color.system_on_error_dark)),
          errorContainer = Color(context.getColor(android.R.color.system_error_container_dark)),
          onErrorContainer =
            Color(context.getColor(android.R.color.system_on_error_container_dark)),
        )
    } else {
      dynamicLightColorScheme(context)
        .copy(
          error = Color(context.getColor(android.R.color.system_error_light)),
          onError = Color(context.getColor(android.R.color.system_on_error_light)),
          errorContainer = Color(context.getColor(android.R.color.system_error_container_light)),
          onErrorContainer =
            Color(context.getColor(android.R.color.system_on_error_container_light)),
        )
    }

  MaterialTheme(colorScheme = colorScheme, content = content)
}

package dev.daw.weightwatcher

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    Log.i("MainActivity", "onCreate called")
    setContent { WeightWatcherApp() }
  }

  @Composable
  fun WeightWatcherApp() {
    val sdkStatus = remember { HealthConnectClient.getSdkStatus(this@MainActivity) }
    val healthConnectClient =
        remember(sdkStatus) {
          if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(this@MainActivity)
          } else null
        }
    Log.i("MainActivity", "Health Connect SDK Status: $sdkStatus")
    var hasPermissions by remember { mutableStateOf(false) }
    val syncManager = remember { SyncManager(this) }
    val lastWeightTime by syncManager.lastWeightTime.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val requestPermissionActivityContract =
        PermissionController.createRequestPermissionResultContract()
    val requestPermissions =
        rememberLauncherForActivityResult(requestPermissionActivityContract) { granted ->
          Log.i("MainActivity", "Permissions request result: $granted")
          hasPermissions = granted.containsAll(SyncManager.REQUIRED_PERMISSIONS)
          if (hasPermissions) {
            Log.i("MainActivity", "All permissions granted! Enqueuing sync...")
            // Immediate visual update to fix widget click behavior
            coroutineScope.launch { WeightGlanceWidget.resetAll(this@MainActivity) }
            WeightWorker.enqueueOneTimeWork(this@MainActivity)
          }
        }

    LaunchedEffect(Unit) {
      if (healthConnectClient != null) {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        hasPermissions = granted.containsAll(SyncManager.REQUIRED_PERMISSIONS)
        Log.i("MainActivity", "Initial permissions check: $hasPermissions")
        if (hasPermissions) {
          // Enqueue worker
          WeightWorker.enqueuePeriodicWork(this@MainActivity)
        }
      }
    }

    // SharedPreferences listener logic removed in favor of SyncManager's lastWeightTime Flow

    WeightWatcherTheme {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
          Text("WeightWatcher Widget Setup", style = MaterialTheme.typography.headlineMedium)
          Spacer(modifier = Modifier.height(32.dp))

          if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            val errorMessage =
                when (sdkStatus) {
                  HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                      "Health Connect is not installed. Please install it from the Play Store."
                  HealthConnectClient.SDK_UNAVAILABLE ->
                      "Health Connect is not supported on this device."
                  else -> "Health Connect status: $sdkStatus"
                }
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* Open Play Store? */ }) { Text("Check Health Connect") }
          } else if (hasPermissions) {
            Text("Permissions granted! 😊")
            Spacer(modifier = Modifier.height(16.dp))

            lastWeightTime?.let { time ->
              val formatter =
                  DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                      .withZone(ZoneId.systemDefault())
              Text(
                  "Last weight log found: ${formatter.format(time)}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.secondary,
              )
              Spacer(modifier = Modifier.height(16.dp))
            }

            Text("You can now add the widget to your home screen.")
            Spacer(modifier = Modifier.height(24.dp))

            val appWidgetManager = AppWidgetManager.getInstance(this@MainActivity)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
              Button(
                  onClick = {
                    coroutineScope.launch {
                      val manager = GlanceAppWidgetManager(this@MainActivity)
                      manager.requestPinGlanceAppWidget(
                          receiver = WeightGlanceWidgetReceiver::class.java,
                          preview = WeightGlanceWidget(),
                          previewState = WeightGlanceWidget.createMetadata(lastWeightTime),
                      )
                    }
                  },
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
              ) {
                Text("Pin Widget to Home Screen")
              }
            }
          } else {
            Text("Need Health Connect read permissions for weight.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                  Log.i(
                      "MainActivity",
                      "Grant Permissions button clicked. Requesting: ${SyncManager.REQUIRED_PERMISSIONS}",
                  )
                  requestPermissions.launch(SyncManager.REQUIRED_PERMISSIONS)
                }
            ) {
              Text("Grant Permissions")
            }
          }
        }
      }
    }
  }
}

class PermissionsRationaleActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    Log.i("PermissionsRationaleActivity", "onCreate called")
    setContent {
      WeightWatcherTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Box(
              modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
              contentAlignment = Alignment.Center,
          ) {
            Text(
                "This app needs permission to read weight from Health Connect to display staleness on the widget."
            )
          }
        }
      }
    }
  }
}

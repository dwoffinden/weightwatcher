package dev.daw.weightwatcher

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.i("MainActivity", "onCreate called")
    setContent { WeightWatcherApp() }
  }

  @Composable
  fun WeightWatcherApp() {
    val healthConnectClient = HealthConnectClient.getOrCreate(this)
    val sdkStatus = HealthConnectClient.getSdkStatus(this)
    Log.i("MainActivity", "Health Connect SDK Status: $sdkStatus")
    var hasPermissions by remember { mutableStateOf(false) }
    var lastWeightTime by remember { mutableStateOf<Instant?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val requestPermissionActivityContract =
        PermissionController.createRequestPermissionResultContract()
    val requestPermissions =
        rememberLauncherForActivityResult(requestPermissionActivityContract) { granted ->
          Log.i("MainActivity", "Permissions request result: $granted")
          hasPermissions = granted.containsAll(PERMISSIONS)
          if (hasPermissions) {
            Log.i("MainActivity", "All permissions granted! Enqueuing sync...")
            // Immediate visual update to fix widget click behavior
            WeightWidgetProvider.updateAppWidget(this@MainActivity, null, true)
            WeightWorker.enqueueOneTimeWork(this@MainActivity)
          }
        }

    LaunchedEffect(Unit) {
      val granted = healthConnectClient.permissionController.getGrantedPermissions()
      hasPermissions = granted.containsAll(PERMISSIONS)
      Log.i("MainActivity", "Initial permissions check: $hasPermissions")
      if (hasPermissions) {
        // Enqueue worker
        WeightWorker.enqueuePeriodicWork(this@MainActivity)
      }
    }

    DisposableEffect(Unit) {
      val prefs = getSharedPreferences("weight_prefs", Context.MODE_PRIVATE)
      val listener =
          SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "last_weight_time") {
              val lastTimeStr = p.getString("last_weight_time", null)
              lastWeightTime = if (lastTimeStr != null) Instant.parse(lastTimeStr) else null
            }
          }

      // Initial value
      val initialValue = prefs.getString("last_weight_time", null)
      lastWeightTime = if (initialValue != null) Instant.parse(initialValue) else null

      prefs.registerOnSharedPreferenceChangeListener(listener)
      onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    MaterialTheme {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
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
                    val componentName =
                        ComponentName(this@MainActivity, WeightWidgetProvider::class.java)
                    val staleness =
                        lastWeightTime?.let { time ->
                          Duration.between(time, Instant.now()).toHours()
                        }
                    val preview =
                        WeightWidgetProvider.createRemoteViews(
                            this@MainActivity,
                            staleness,
                            true,
                        )
                    val extras =
                        Bundle().apply {
                          putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview)
                        }
                    appWidgetManager.requestPinAppWidget(componentName, extras, null)
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
                      "Grant Permissions button clicked. Requesting: $PERMISSIONS",
                  )
                  requestPermissions.launch(PERMISSIONS)
                }
            ) {
              Text("Grant Permissions")
            }
          }
        }
      }
    }
  }

  companion object {
    val PERMISSIONS =
        setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
        )
  }
}

class PermissionsRationaleActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.i("PermissionsRationaleActivity", "onCreate called")
    setContent {
      MaterialTheme {
        Text(
            "This app needs permission to read weight from Health Connect to display staleness on the widget."
        )
      }
    }
  }
}

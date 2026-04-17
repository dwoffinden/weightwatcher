package dev.daw.weightwatcher

import android.os.Bundle
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { WeightWatcherApp() }
  }

  @Composable
  fun WeightWatcherApp() {
    val healthConnectClient = HealthConnectClient.getOrCreate(this)
    var hasPermissions by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val permissions = setOf(HealthPermission.getReadPermission(WeightRecord::class))

    val requestPermissionActivityContract =
        PermissionController.createRequestPermissionResultContract()
    val requestPermissions =
        rememberLauncherForActivityResult(requestPermissionActivityContract) { granted ->
          hasPermissions = granted.containsAll(permissions)
        }

    LaunchedEffect(Unit) {
      val granted = healthConnectClient.permissionController.getGrantedPermissions()
      hasPermissions = granted.containsAll(permissions)
      if (hasPermissions) {
        // Enqueue worker
        WeightWorker.enqueuePeriodicWork(this@MainActivity)
      }
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

          if (hasPermissions) {
            Text("Permissions granted! 😊")
            Spacer(modifier = Modifier.height(16.dp))
            Text("You can now add the widget to your home screen.")
          } else {
            Text("Need Health Connect read permissions for weight.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { requestPermissions.launch(permissions) }) {
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
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Text(
            "This app needs permission to read weight from Health Connect to display staleness on the widget."
        )
      }
    }
  }
}

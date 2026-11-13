package dev.daw.weightwatcher

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  companion object {
    /**
     * Minimum window height for pinning the action footer at the bottom while scrolling data types
     * separately; shorter windows (such as phones in landscape) scroll the whole screen instead.
     */
    private val MIN_PINNED_FOOTER_HEIGHT: Dp = 560.dp
    private val CONTENT_PADDING: Dp = 16.dp
    private val TOP_SCREEN_PADDING: Dp = 64.dp
    private val BOTTOM_SCREEN_PADDING: Dp = 32.dp
    private val SCROLL_FADE_HEIGHT: Dp = 32.dp
  }

  @field:Inject lateinit var healthConnectAccess: HealthConnectAccess
  @field:Inject lateinit var syncManager: SyncManager

  private val glanceAppWidget = WeightGlanceWidget()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    Log.i("MainActivity", "onCreate called")

    val configureWidgetId: Int =
      intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    if (configureWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
      setResult(
        RESULT_CANCELED,
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configureWidgetId),
      )
    }

    val initialDataType: DataType = intent.getDataType() ?: DataType.WEIGHT

    setContent {
      WeightWatcherApp(configureWidgetId = configureWidgetId, initialDataType = initialDataType)
    }
  }

  @Composable
  fun WeightWatcherApp(
    configureWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    initialDataType: DataType = DataType.WEIGHT,
  ) {
    val isConfigureMode: Boolean = configureWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
    var selectedDataType: DataType by remember { mutableStateOf(initialDataType) }
    var grantedPermissions: Set<String> by remember { mutableStateOf(emptySet()) }
    val hasPermissions: Boolean =
      grantedPermissions.containsAll(SyncManager.requiredPermissionsFor(selectedDataType))
    val lastRecordTime: Instant? by
      remember(selectedDataType) { syncManager.lastRecordTime(selectedDataType) }
        .collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    // Null until loaded.
    var sdkStatus: Int? by remember { mutableStateOf(null) }

    fun saveConfiguredWidgetAndFinish(dataType: DataType) {
      coroutineScope.launch {
        glanceAppWidget.configure(this@MainActivity, configureWidgetId, dataType)
        setResult(
          RESULT_OK,
          Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configureWidgetId),
        )
        finish()
      }
    }

    val requestPermissionActivityContract =
      PermissionController.createRequestPermissionResultContract()
    val requestPermissions =
      rememberLauncherForActivityResult(requestPermissionActivityContract) { granted ->
        Log.i("MainActivity", "Permissions request result: $granted")
        val updatedPermissions: Set<String> = grantedPermissions + granted
        grantedPermissions = updatedPermissions
        if (updatedPermissions.containsAll(SyncManager.requiredPermissionsFor(selectedDataType))) {
          Log.i("MainActivity", "Permissions granted for $selectedDataType!")
          if (isConfigureMode) {
            saveConfiguredWidgetAndFinish(selectedDataType)
          }
        }
      }

    fun onDataTypeSelected(dataType: DataType) {
      selectedDataType = dataType
      if (
        isConfigureMode &&
          grantedPermissions.containsAll(SyncManager.requiredPermissionsFor(dataType))
      ) {
        saveConfiguredWidgetAndFinish(dataType)
      }
    }

    fun onPinWidget() {
      val chosenType: DataType = selectedDataType
      coroutineScope.launch {
        val manager = GlanceAppWidgetManager(this@MainActivity)
        manager.requestPinGlanceAppWidget(
          receiver = WeightGlanceWidgetReceiver::class.java,
          preview = glanceAppWidget,
          previewState = WeightGlanceWidget.previewPreferences(chosenType),
          successCallback = WidgetPinReceiver.createSuccessCallback(this@MainActivity, chosenType),
        )
      }
    }

    fun onGrantPermissions() {
      val permissionsToRequest: Set<String> = SyncManager.requiredPermissionsFor(selectedDataType)
      Log.i("MainActivity", "Grant Permissions button clicked. Requesting: $permissionsToRequest")
      requestPermissions.launch(permissionsToRequest)
    }

    fun onOpenHealthConnect() {
      startActivity(HealthConnectClient.getHealthConnectManageDataIntent(this@MainActivity))
    }

    LaunchedEffect(Unit) {
      val status: Int = healthConnectAccess.sdkStatus()
      Log.i("MainActivity", "Health Connect SDK Status: $status")
      sdkStatus = status
    }

    LaunchedEffect(configureWidgetId) {
      if (isConfigureMode) {
        val manager = GlanceAppWidgetManager(this@MainActivity)
        val glanceId = runCatching { manager.getGlanceIdBy(configureWidgetId) }.getOrNull()
        val widgetDataType: DataType? = glanceId?.let {
          WeightGlanceWidget.getWidgetDataType(this@MainActivity, it)
        }
        if (widgetDataType != null) {
          selectedDataType = widgetDataType
        }
      }
    }

    LaunchedEffect(sdkStatus) {
      if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
        grantedPermissions =
          healthConnectAccess.client().permissionController.getGrantedPermissions()
      }
    }

    WeightWatcherTheme {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
          val currentSdkStatus: Int? = sdkStatus
          if (currentSdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            Column(
              modifier =
                Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .safeDrawingPadding()
                  .padding(
                    start = CONTENT_PADDING,
                    top = TOP_SCREEN_PADDING,
                    end = CONTENT_PADDING,
                    bottom = BOTTOM_SCREEN_PADDING,
                  ),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Text("WeightWatcher Widget Setup", style = MaterialTheme.typography.headlineMedium)
              Spacer(modifier = Modifier.height(24.dp))
              if (currentSdkStatus == null) {
                CircularProgressIndicator()
              } else {
                val errorMessage: String =
                  when (currentSdkStatus) {
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                      "Health Connect is not installed. Please install it from the Play Store."
                    HealthConnectClient.SDK_UNAVAILABLE ->
                      "Health Connect is not supported on this device."
                    else -> "Health Connect status: $currentSdkStatus"
                  }
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
              }
            }
          } else {
            val isPinSupported: Boolean =
              AppWidgetManager.getInstance(this@MainActivity).isRequestPinAppWidgetSupported
            if (maxHeight >= MIN_PINNED_FOOTER_HEIGHT) {
              val scrollState = rememberScrollState()
              val backgroundColor: Color = MaterialTheme.colorScheme.background
              Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                  Column(
                    modifier =
                      Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .windowInsetsPadding(
                          WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                          )
                        )
                        .padding(
                          start = CONTENT_PADDING,
                          top = TOP_SCREEN_PADDING,
                          end = CONTENT_PADDING,
                          bottom = CONTENT_PADDING,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                  ) {
                    DataTypeSelectionSection(
                      selectedDataType = selectedDataType,
                      onSelectDataType = ::onDataTypeSelected,
                    )
                  }
                  if (scrollState.canScrollForward) {
                    Box(
                      modifier =
                        Modifier.align(Alignment.BottomCenter)
                          .fillMaxWidth()
                          .height(SCROLL_FADE_HEIGHT)
                          .background(
                            Brush.verticalGradient(
                              colors = listOf(backgroundColor.copy(alpha = 0f), backgroundColor)
                            )
                          )
                    )
                  }
                }
                Column(
                  modifier =
                    Modifier.fillMaxWidth()
                      .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                          WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                      )
                      .padding(
                        start = CONTENT_PADDING,
                        top = 20.dp,
                        end = CONTENT_PADDING,
                        bottom = BOTTOM_SCREEN_PADDING,
                      ),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  SetupActionsSection(
                    selectedDataType = selectedDataType,
                    hasPermissions = hasPermissions,
                    lastRecordTime = lastRecordTime,
                    isConfigureMode = isConfigureMode,
                    isPinSupported = isPinSupported,
                    onPinWidget = ::onPinWidget,
                    onGrantPermissions = ::onGrantPermissions,
                    onOpenHealthConnect = ::onOpenHealthConnect,
                  )
                }
              }
            } else {
              Column(
                modifier =
                  Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .safeDrawingPadding()
                    .padding(
                      start = CONTENT_PADDING,
                      top = TOP_SCREEN_PADDING,
                      end = CONTENT_PADDING,
                      bottom = BOTTOM_SCREEN_PADDING,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                DataTypeSelectionSection(
                  selectedDataType = selectedDataType,
                  onSelectDataType = ::onDataTypeSelected,
                )
                Spacer(modifier = Modifier.height(24.dp))
                SetupActionsSection(
                  selectedDataType = selectedDataType,
                  hasPermissions = hasPermissions,
                  lastRecordTime = lastRecordTime,
                  isConfigureMode = isConfigureMode,
                  isPinSupported = isPinSupported,
                  onPinWidget = ::onPinWidget,
                  onGrantPermissions = ::onGrantPermissions,
                  onOpenHealthConnect = ::onOpenHealthConnect,
                )
              }
            }
          }
        }
      }
    }
  }

  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  private fun DataTypeSelectionSection(
    selectedDataType: DataType,
    onSelectDataType: (DataType) -> Unit,
  ) {
    Text("WeightWatcher Widget Setup", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(24.dp))
    Text("Select data type to track:", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(12.dp))
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      for (dataType in DataType.entries) {
        FilterChip(
          selected = dataType == selectedDataType,
          onClick = { onSelectDataType(dataType) },
          label = { Text(dataType.displayName) },
          leadingIcon = {
            Icon(
              painter = painterResource(dataType.iconRes),
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          },
        )
      }
    }
  }

  @Composable
  private fun SetupActionsSection(
    selectedDataType: DataType,
    hasPermissions: Boolean,
    lastRecordTime: Instant?,
    isConfigureMode: Boolean,
    isPinSupported: Boolean,
    onPinWidget: () -> Unit,
    onGrantPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
  ) {
    if (hasPermissions) {
      Text("Permissions granted! 😊")
      if (lastRecordTime != null) {
        Spacer(modifier = Modifier.height(8.dp))
        val formatter = remember {
          DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
        }
        Text(
          "Last ${selectedDataType.nameInSentence} log found: ${formatter.format(lastRecordTime)}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
      if (!isConfigureMode) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("You can now add the widget to your home screen.")
        if (isPinSupported) {
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = onPinWidget,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
          ) {
            Text("Pin ${selectedDataType.displayName} Widget to Home Screen")
          }
        }
      }
    } else {
      Text("Need Health Connect read permissions for ${selectedDataType.nameInSentence}.")
      Spacer(modifier = Modifier.height(12.dp))
      Button(onClick = onGrantPermissions) { Text("Grant Permissions") }
    }

    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
      onClick = onOpenHealthConnect,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
    ) {
      Text("Open Health Connect")
    }
  }
}

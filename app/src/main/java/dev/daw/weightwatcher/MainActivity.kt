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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
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
import com.google.common.collect.ImmutableSet
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.flowOf
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
    private const val WIDGET_HORIZONTAL_PADDING_DP = 8
    private const val MIN_SLOT_WIDTH_DP = 30
    private const val MIN_WIDGET_SLOTS = 2
    private const val TYPICAL_CELL_PITCH_DP = 86
    private const val TYPICAL_CELL_GAP_DP = 13

    internal fun maxTypesForWidthDp(widthDp: Int): Int =
      ((widthDp - WIDGET_HORIZONTAL_PADDING_DP) / MIN_SLOT_WIDTH_DP).coerceIn(
        MIN_WIDGET_SLOTS,
        DataType.entries.size,
      )

    internal fun suggestedWidthCells(typeCount: Int): Int {
      val requiredWidthDp: Int =
        typeCount.coerceAtLeast(1) * MIN_SLOT_WIDTH_DP + WIDGET_HORIZONTAL_PADDING_DP
      return ((requiredWidthDp + TYPICAL_CELL_GAP_DP + TYPICAL_CELL_PITCH_DP - 1) /
          TYPICAL_CELL_PITCH_DP)
        .coerceAtLeast(1)
    }
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

    val initialDataTypes: ImmutableSet<DataType> =
      intent.getWidgetDataTypes().ifEmpty { ImmutableSet.of(DataType.WEIGHT) }

    setContent {
      WeightWatcherApp(configureWidgetId = configureWidgetId, initialDataTypes = initialDataTypes)
    }
  }

  @Composable
  fun WeightWatcherApp(
    configureWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    initialDataTypes: ImmutableSet<DataType> = ImmutableSet.of(DataType.WEIGHT),
  ) {
    val isConfigureMode: Boolean = configureWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
    val appWidgetManager = AppWidgetManager.getInstance(this@MainActivity)
    val maxDataTypes: Int? =
      if (isConfigureMode) {
        val widthDp: Int =
          appWidgetManager
            .getAppWidgetOptions(configureWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        if (widthDp > 0) maxTypesForWidthDp(widthDp) else null
      } else {
        null
      }
    var selectedDataTypes: ImmutableSet<DataType> by remember { mutableStateOf(initialDataTypes) }
    var grantedPermissions: Set<String> by remember { mutableStateOf(emptySet()) }
    val missingPermissionTypes: List<DataType> = selectedDataTypes.filter { dataType ->
      !grantedPermissions.containsAll(SyncManager.requiredPermissionsFor(dataType))
    }
    val hasPermissions: Boolean = selectedDataTypes.isNotEmpty() && missingPermissionTypes.isEmpty()
    val singleSelectedType: DataType? = selectedDataTypes.singleOrNull()
    val lastRecordTime: Instant? by
      remember(singleSelectedType) {
          if (singleSelectedType != null) {
            syncManager.lastRecordTime(singleSelectedType)
          } else {
            flowOf(null)
          }
        }
        .collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    // Null until loaded.
    var sdkStatus: Int? by remember { mutableStateOf(null) }

    fun saveConfiguredWidgetAndFinish(dataTypes: ImmutableSet<DataType>) {
      if (dataTypes.isEmpty()) {
        return
      }
      coroutineScope.launch {
        glanceAppWidget.configure(this@MainActivity, configureWidgetId, dataTypes)
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
        grantedPermissions = grantedPermissions + granted
      }

    fun onToggleDataType(dataType: DataType) {
      selectedDataTypes =
        if (dataType in selectedDataTypes) {
          ImmutableSet.copyOf(selectedDataTypes - dataType)
        } else if (maxDataTypes == null || selectedDataTypes.size < maxDataTypes) {
          ImmutableSet.copyOf(selectedDataTypes + dataType)
        } else {
          selectedDataTypes
        }
    }

    fun onRemoveSlot(dataType: DataType) {
      selectedDataTypes = ImmutableSet.copyOf(selectedDataTypes - dataType)
    }

    fun onPinWidget() {
      val chosenTypes: ImmutableSet<DataType> = selectedDataTypes
      if (chosenTypes.isEmpty()) {
        return
      }
      coroutineScope.launch {
        val manager = GlanceAppWidgetManager(this@MainActivity)
        manager.requestPinGlanceAppWidget(
          receiver = WeightGlanceWidgetReceiver::class.java,
          preview = glanceAppWidget,
          previewState = WeightGlanceWidget.previewPreferences(chosenTypes),
          successCallback = WidgetPinReceiver.createSuccessCallback(this@MainActivity, chosenTypes),
        )
      }
    }

    fun onGrantPermissions() {
      val permissionsToRequest: Set<String> =
        missingPermissionTypes.flatMapTo(mutableSetOf()) { SyncManager.requiredPermissionsFor(it) }
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
        val widgetDataTypes: ImmutableSet<DataType> =
          if (glanceId != null) {
            WeightGlanceWidget.getWidgetDataTypes(this@MainActivity, glanceId)
          } else {
            ImmutableSet.of()
          }
        if (widgetDataTypes.isNotEmpty()) {
          selectedDataTypes = widgetDataTypes
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
            val isPinSupported: Boolean = appWidgetManager.isRequestPinAppWidgetSupported
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
                      selectedDataTypes = selectedDataTypes,
                      maxDataTypes = maxDataTypes,
                      onToggleDataType = ::onToggleDataType,
                      onRemoveSlot = ::onRemoveSlot,
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
                    selectedDataTypes = selectedDataTypes,
                    missingPermissionTypes = missingPermissionTypes,
                    hasPermissions = hasPermissions,
                    lastRecordTime = lastRecordTime,
                    isConfigureMode = isConfigureMode,
                    maxDataTypes = maxDataTypes,
                    isPinSupported = isPinSupported,
                    onSaveWidget = { saveConfiguredWidgetAndFinish(selectedDataTypes) },
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
                  selectedDataTypes = selectedDataTypes,
                  maxDataTypes = maxDataTypes,
                  onToggleDataType = ::onToggleDataType,
                  onRemoveSlot = ::onRemoveSlot,
                )
                Spacer(modifier = Modifier.height(24.dp))
                SetupActionsSection(
                  selectedDataTypes = selectedDataTypes,
                  missingPermissionTypes = missingPermissionTypes,
                  hasPermissions = hasPermissions,
                  lastRecordTime = lastRecordTime,
                  isConfigureMode = isConfigureMode,
                  maxDataTypes = maxDataTypes,
                  isPinSupported = isPinSupported,
                  onSaveWidget = { saveConfiguredWidgetAndFinish(selectedDataTypes) },
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
    selectedDataTypes: ImmutableSet<DataType>,
    maxDataTypes: Int?,
    onToggleDataType: (DataType) -> Unit,
    onRemoveSlot: (DataType) -> Unit,
  ) {
    val isAtCap: Boolean = maxDataTypes != null && selectedDataTypes.size >= maxDataTypes
    Text("WeightWatcher Widget Setup", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    WidgetSlotOrderPreview(
      selectedDataTypes = selectedDataTypes,
      maxDataTypes = maxDataTypes,
      onRemoveSlot = onRemoveSlot,
    )
    Spacer(modifier = Modifier.height(16.dp))
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      for (dataType in DataType.entries) {
        val isSelected: Boolean = dataType in selectedDataTypes
        FilterChip(
          selected = isSelected,
          enabled = isSelected || !isAtCap,
          onClick = { onToggleDataType(dataType) },
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

  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  private fun WidgetSlotOrderPreview(
    selectedDataTypes: ImmutableSet<DataType>,
    maxDataTypes: Int?,
    onRemoveSlot: (DataType) -> Unit,
  ) {
    val headerText: String =
      if (maxDataTypes != null) {
        "Widget order (${selectedDataTypes.size}/$maxDataTypes)"
      } else {
        "Widget order (${selectedDataTypes.size})"
      }
    val helperText: String =
      when {
        selectedDataTypes.isEmpty() ->
          "Tap data types below to add · resize widget wider to fit more"
        selectedDataTypes.size >= DataType.entries.size ->
          "All data types selected · tap a slot to remove"
        maxDataTypes != null && selectedDataTypes.size >= maxDataTypes ->
          "Widget full, resize wider for more slots"
        else -> "Tap below to add, tap a slot to remove · resize widget wider to fit more"
      }
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(headerText, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          helperText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selectedDataTypes.isNotEmpty()) {
          Spacer(modifier = Modifier.height(8.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            for ((index, dataType) in selectedDataTypes.withIndex()) {
              InputChip(
                selected = true,
                onClick = { onRemoveSlot(dataType) },
                label = { Text("${index + 1}. ${dataType.displayName}") },
                leadingIcon = {
                  Icon(
                    painter = painterResource(dataType.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
              )
            }
          }
        }
      }
    }
  }

  @Composable
  private fun SetupActionsSection(
    selectedDataTypes: ImmutableSet<DataType>,
    missingPermissionTypes: List<DataType>,
    hasPermissions: Boolean,
    lastRecordTime: Instant?,
    isConfigureMode: Boolean,
    maxDataTypes: Int?,
    isPinSupported: Boolean,
    onSaveWidget: () -> Unit,
    onPinWidget: () -> Unit,
    onGrantPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
  ) {
    if (selectedDataTypes.isEmpty()) {
      Text("Select at least one data type.")
    } else if (hasPermissions) {
      Text("Permissions granted! 😊")
      val singleType: DataType? = selectedDataTypes.singleOrNull()
      if (singleType != null && lastRecordTime != null) {
        Spacer(modifier = Modifier.height(8.dp))
        val formatter = remember {
          DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
        }
        Text(
          "Last ${singleType.nameInSentence} log found: ${formatter.format(lastRecordTime)}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
      val suggestedCells: Int = suggestedWidthCells(selectedDataTypes.size)
      if (suggestedCells > 1 && (maxDataTypes == null || selectedDataTypes.size > maxDataTypes)) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "Tip: resize to ${suggestedCells}x1 on your home screen for ${selectedDataTypes.size} types.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
      if (isConfigureMode) {
        Spacer(modifier = Modifier.height(16.dp))
        Button(
          onClick = onSaveWidget,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        ) {
          Text("Save Widget")
        }
      } else {
        if (selectedDataTypes.size == 1) {
          Spacer(modifier = Modifier.height(8.dp))
          Text("You can now add the widget to your home screen.")
        }
        if (isPinSupported) {
          Spacer(modifier = Modifier.height(16.dp))
          val pinLabel: String =
            if (singleType != null) {
              "Pin ${singleType.displayName} Widget to Home Screen"
            } else {
              "Pin ${selectedDataTypes.size}-Type Widget to Home Screen"
            }
          Button(
            onClick = onPinWidget,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
          ) {
            Text(pinLabel)
          }
        }
      }
    } else {
      val missingNames: String = missingPermissionTypes.joinToString(", ") { it.nameInSentence }
      Text("Need Health Connect read permissions for $missingNames.")
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

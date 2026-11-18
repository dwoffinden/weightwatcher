package dev.daw.weightwatcher.widget.remotecompose

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.capture.RemoteImageVector
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.currentProfileSupportsThemedColors
import androidx.compose.remote.creation.compose.capture.vectorResource
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.drawWithContent
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.visibility
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteInt
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.createThemedRemoteColor
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.ri
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import androidx.compose.remote.foundation.icon.RemoteBasicIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.daw.weightwatcher.WeightGlanceWidget
import dev.daw.weightwatcher.widget.WidgetState
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CLICK_ACTION_ID = 1
private const val WIDGETS_V7_API_LEVEL = 7L

/**
 * Selects the RemoteCompose widget [Profile] matching the OS player's wire version: `WIDGETS_V6` on
 * Android 16 (API 36, max level 6) and `WIDGETS_V7` on Android 17+ (API 37+, max level 7, adding
 * `ColorTheme` opcode 196 for dynamic light/dark system colours in a single document).
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
// RcPlatformProfiles is @RestrictTo(LIBRARY_GROUP) in androidx.compose.remote:1.0.0-alpha23.
@Suppress("RestrictedApi")
internal fun selectWidgetProfile(
  supportedVersion: Long = RemoteViews.DrawInstructions.getSupportedVersion()
): Profile =
  if (supportedVersion >= WIDGETS_V7_API_LEVEL) {
    RcPlatformProfiles.WIDGETS_V7
  } else {
    RcPlatformProfiles.WIDGETS_V6
  }

/**
 * Captures a standalone top-level `RemoteViews(DrawInstructions)` widget for Android 16+ (API 36+).
 *
 * Kept at the top level rather than nested inside Glance's `AndroidRemoteViews` because Android 17+
 * (`FLAG_ENFORCE_REMOTE_COMPOSE_LIMITS`) forbids mixing classic XML `RemoteViews` and
 * `DrawInstructions` in the same hierarchy.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
internal suspend fun captureWidgetRemoteViews(
  context: Context,
  state: WidgetState,
  onClickPendingIntent: PendingIntent,
  profile: Profile = selectWidgetProfile(),
): RemoteViews {
  val bytes: ByteArray =
    captureWidgetDocumentBytes(context = context, state = state, profile = profile)
  val drawInstructions = RemoteViews.DrawInstructions.Builder(listOf(bytes)).build()
  return RemoteViews(drawInstructions).apply {
    @SuppressLint("ResourceType") setOnClickPendingIntent(CLICK_ACTION_ID, onClickPendingIntent)
  }
}

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
internal suspend fun captureWidgetDocumentBytes(
  context: Context,
  state: WidgetState,
  profile: Profile = selectWidgetProfile(),
): ByteArray =
  withContext(Dispatchers.Main.immediate) {
    captureSingleRemoteDocument(context = context, profile = profile) {
        RemoteWidgetCard(state = state)
      }
      .bytes
  }

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
// hostAction(id) is @RestrictTo(LIBRARY_GROUP) in androidx.compose.remote:1.0.0-alpha23.
@Suppress("RestrictedApi")
@Composable
@RemoteComposable
private fun RemoteWidgetCard(state: WidgetState) {
  // Include a zero-valued FLOAT_TIME_IN_MIN term when active so RemoteComposeState.getOpsToUpdate()
  // schedules a 1-minute Choreographer repaint while visible (ID_EPOCH_SECOND and TIME_FROM_NOW_HR
  // alone report -1, and Android 16's TimeVariables.updateTime only loads ID_TIME_IN_MIN as a
  // float, not an int).
  val minuteHeartbeat: RemoteFloat =
    if (state is WidgetState.Active) {
      val minuteOfDay = RemoteFloat(RemoteContext.FLOAT_TIME_IN_MIN)
      minuteOfDay - minuteOfDay
    } else {
      0f.rf
    }
  val surfaceContainerColor: RemoteColor =
    resolveWidgetColor(
        lightColorRes = android.R.color.system_surface_container_light,
        darkColorRes = android.R.color.system_surface_container_dark,
      )
      .copy(alpha = 0.8f.rf + minuteHeartbeat)
  val primaryColor: RemoteColor =
    resolveWidgetColor(
      lightColorRes = android.R.color.system_primary_light,
      darkColorRes = android.R.color.system_primary_dark,
    )
  val errorColor: RemoteColor =
    resolveWidgetColor(
      lightColorRes = android.R.color.system_error_light,
      darkColorRes = android.R.color.system_error_dark,
    )
  val onSurfaceColor: RemoteColor =
    resolveWidgetColor(
      lightColorRes = android.R.color.system_on_surface_light,
      darkColorRes = android.R.color.system_on_surface_dark,
    )
  val onSurfaceVariantColor: RemoteColor =
    resolveWidgetColor(
      lightColorRes = android.R.color.system_on_surface_variant_light,
      darkColorRes = android.R.color.system_on_surface_variant_dark,
    )
  val cornerRadius = dimensionResource(android.R.dimen.system_app_widget_background_radius).rdp
  val shape = RemoteRoundedCornerShape(cornerRadius)
  val iconTint: RemoteColor
  val activeLabels: List<Pair<RemoteString, RemoteInt>>?
  val staticLabelText: RemoteString?
  when (state) {
    is WidgetState.Unconfigured,
    is WidgetState.Syncing -> {
      iconTint = onSurfaceVariantColor
      activeLabels = null
      staticLabelText = "Sync...".rs
    }
    is WidgetState.Unauthorized -> {
      iconTint = errorColor
      activeLabels = null
      staticLabelText = "Auth".rs
    }
    is WidgetState.NoRecords -> {
      iconTint = onSurfaceVariantColor
      activeLabels = null
      staticLabelText = "None".rs
    }
    is WidgetState.Active -> {
      val elapsed = RemoteDuration.since(state.lastRecordTime)
      iconTint =
        (elapsed isGreaterThan WeightGlanceWidget.STALE_THRESHOLD).select(
          ifTrue = errorColor,
          ifFalse = primaryColor,
        )
      activeLabels = buildActiveStalenessLabels(elapsed)
      staticLabelText = null
    }
  }
  // Wrap the clipped card inside an unclipped root box so root-level clipping works across players.
  RemoteBox(modifier = RemoteModifier.fillMaxSize()) {
    RemoteColumn(
      modifier =
        RemoteModifier.fillMaxSize()
          .drawWithContent {
            val radiusPx = cornerRadius.toPx()
            drawRoundRect(
              paint =
                RemotePaint {
                  isAntiAlias = true
                  style = PaintingStyle.Fill
                  color = surfaceContainerColor
                },
              topLeft = RemoteOffset(0f.rf, 0f.rf),
              size = size,
              cornerRadius = RemoteOffset(radiusPx, radiusPx),
            )
            usePaint(RemotePaint()) {}
            drawContent()
          }
          .clip(shape)
          .clickable(hostAction(id = CLICK_ACTION_ID))
          .semantics(mergeDescendants = true) {}
          .padding(8.rdp),
      verticalArrangement = RemoteArrangement.Center,
      horizontalAlignment = RemoteAlignment.CenterHorizontally,
    ) {
      if (activeLabels != null) {
        // Materialise dynamic expression ops (TimeAttribute, FloatExpression, IntegerExpression,
        // ColorExpression, TextFromFloat, TextMerge) inside a childless RemoteCanvas
        // (CanvasContent)
        // so Android 16's LayoutComponent.inflate() retains them in the Container tree and runs
        // them during initializeContext before TextLayout.computeWrapSize.
        RemoteCanvas(modifier = RemoteModifier.size(0.rdp)) {
          iconTint.id
          for ((label, visibility) in activeLabels) {
            label.id
            visibility.id
          }
        }
      }
      RemoteBasicIcon(
        imageVector = RemoteImageVector.vectorResource(state.iconRes),
        contentDescription = state.contentDescription.rs,
        modifier = RemoteModifier.size(32.rdp),
        tint = iconTint,
      )
      RemoteBox(modifier = RemoteModifier.height(4.rdp))
      if (activeLabels != null) {
        RemoteBox(modifier = RemoteModifier.fillMaxWidth()) {
          for ((label, visibility) in activeLabels) {
            WidgetLabelText(
              text = label,
              color = onSurfaceColor,
              modifier = RemoteModifier.fillMaxWidth().visibility(visibility),
            )
          }
        }
      } else if (staticLabelText != null) {
        WidgetLabelText(text = staticLabelText, color = onSurfaceColor)
      }
    }
  }
}

/**
 * Builds all 4 unit-suffixed staleness labels (`Xm`/`Xh`/`Xd`/`Xy`) unconditionally alongside
 * visibility flags so only the active unit is shown.
 *
 * Choosing between dynamic strings with `RemoteBoolean.select(RemoteString, RemoteString)` (which
 * compiles to `TextLookupInt`) fails on Android 16 because the player does not re-evaluate the
 * lookup when the underlying dynamic strings change, and can leave the text unresolved on initial
 * layout. Rendering all 4 `RemoteText`s and toggling their visibility works reliably across Android
 * 16 and 17+.
 */
private fun buildActiveStalenessLabels(
  elapsed: RemoteDuration
): List<Pair<RemoteString, RemoteInt>> {
  val integerFormat = DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ROOT))
  val isMinutes: RemoteBoolean = elapsed isLessThan WeightGlanceWidget.HOURS_THRESHOLD
  val isUnderDays: RemoteBoolean = elapsed isLessThan WeightGlanceWidget.DAYS_THRESHOLD
  val isUnderYears: RemoteBoolean = elapsed isLessThan WeightGlanceWidget.YEARS_THRESHOLD

  return listOf(
    (elapsed.toWholeMinutes().toRemoteString(integerFormat) + "m".rs) to visibilityFor(isMinutes),
    (elapsed.toWholeHours().toRemoteString(integerFormat) + "h".rs) to
      visibilityFor(isMinutes.not().and(isUnderDays)),
    (elapsed.toWholeDays().toRemoteString(integerFormat) + "d".rs) to
      visibilityFor(isUnderDays.not().and(isUnderYears)),
    (elapsed.toWholeYears(WeightGlanceWidget.YEARS_THRESHOLD).toRemoteString(integerFormat) +
      "y".rs) to visibilityFor(isUnderYears.not()),
  )
}

// Component.Visibility is @RestrictTo(LIBRARY_GROUP) in androidx.compose.remote:1.0.0-alpha23.
@Suppress("RestrictedApi")
private fun visibilityFor(visible: RemoteBoolean): RemoteInt =
  visible.select(Component.Visibility.VISIBLE.ri, Component.Visibility.GONE.ri)

@Composable
@RemoteComposable
private fun WidgetLabelText(
  text: RemoteString,
  color: RemoteColor,
  modifier: RemoteModifier = RemoteModifier.fillMaxWidth(),
) {
  RemoteText(
    text = text,
    modifier = modifier,
    color = color,
    fontSize = 14.rsp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
    overflow = TextOverflow.Visible,
    maxLines = 1,
  )
}

@Composable
@RemoteComposable
private fun resolveWidgetColor(
  @ColorRes lightColorRes: Int,
  @ColorRes darkColorRes: Int,
): RemoteColor {
  val lightColor: Color = colorResource(lightColorRes)
  val darkColor: Color = colorResource(darkColorRes)
  return if (currentProfileSupportsThemedColors()) {
    RemoteColor.createThemedRemoteColor(
      lightColorRes = lightColorRes,
      darkColorRes = darkColorRes,
      lightFallback = lightColor,
      darkFallback = darkColor,
    )
  } else {
    (if (isSystemInDarkTheme()) darkColor else lightColor).rc
  }
}

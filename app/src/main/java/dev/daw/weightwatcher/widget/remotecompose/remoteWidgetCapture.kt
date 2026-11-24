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
import androidx.compose.remote.creation.compose.layout.RemoteImage
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.drawWithContent
import androidx.compose.remote.creation.compose.modifier.fillMaxHeight
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
import androidx.compose.remote.creation.compose.state.RemoteDp
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteImageBitmap
import androidx.compose.remote.creation.compose.state.RemoteInt
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.createThemedRemoteColor
import androidx.compose.remote.creation.compose.state.rb
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.daw.weightwatcher.WeightGlanceWidget
import dev.daw.weightwatcher.widget.AppBadgeBitmapLoader
import dev.daw.weightwatcher.widget.AppIcon
import dev.daw.weightwatcher.widget.WidgetClickAction
import dev.daw.weightwatcher.widget.WidgetItemState
import dev.daw.weightwatcher.widget.WidgetState
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FIRST_CLICK_ACTION_ID = 1
private const val WIDGETS_V7_API_LEVEL = 7L
private const val BADGE_RING_PADDING_DP = 1

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
  pendingIntentForAction: (WidgetClickAction) -> PendingIntent,
  profile: Profile = selectWidgetProfile(),
  badgeLoader: AppBadgeBitmapLoader = AppBadgeBitmapLoader(context),
): RemoteViews {
  val bytes: ByteArray =
    captureWidgetDocumentBytes(
      context = context,
      state = state,
      profile = profile,
      badgeLoader = badgeLoader,
    )
  val drawInstructions = RemoteViews.DrawInstructions.Builder(listOf(bytes)).build()
  return RemoteViews(drawInstructions).apply {
    when (state) {
      is WidgetState.Unconfigured -> {
        @SuppressLint("ResourceType")
        setOnClickPendingIntent(FIRST_CLICK_ACTION_ID, pendingIntentForAction(state.clickAction))
      }
      is WidgetState.Configured -> {
        for ((index, item) in state.items.withIndex()) {
          @SuppressLint("ResourceType")
          setOnClickPendingIntent(
            FIRST_CLICK_ACTION_ID + index,
            pendingIntentForAction(item.clickAction),
          )
        }
      }
    }
  }
}

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
internal suspend fun captureWidgetDocumentBytes(
  context: Context,
  state: WidgetState,
  profile: Profile = selectWidgetProfile(),
  badgeLoader: AppBadgeBitmapLoader = AppBadgeBitmapLoader(context),
): ByteArray =
  withContext(Dispatchers.Main.immediate) {
    captureSingleRemoteDocument(context = context, profile = profile) {
        RemoteWidgetCard(state = state, badgeLoader = badgeLoader)
      }
      .bytes
  }

private class RemoteTileVisuals(
  val iconRes: Int,
  val contentDescription: String,
  val iconTint: RemoteColor,
  val activeLabels: List<Pair<RemoteString, RemoteInt>>?,
  val staticLabelText: RemoteString?,
  val badgeBitmap: RemoteImageBitmap? = null,
)

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
// hostAction(id) is @RestrictTo(LIBRARY_GROUP) in androidx.compose.remote:1.0.0-alpha23.
@Suppress("RestrictedApi")
@Composable
@RemoteComposable
private fun RemoteWidgetCard(state: WidgetState, badgeLoader: AppBadgeBitmapLoader) {
  val hasActiveTile: Boolean =
    state is WidgetState.Configured && state.items.any { it is WidgetItemState.Active }
  // Include a zero-valued FLOAT_TIME_IN_MIN term when any tile is active so
  // RemoteComposeState.getOpsToUpdate() schedules a 1-minute Choreographer repaint while visible
  // (ID_EPOCH_SECOND alone reports -1, and Android 16's TimeVariables.updateTime only loads
  // ID_TIME_IN_MIN as a float, not an int).
  val minuteHeartbeat: RemoteFloat =
    if (hasActiveTile) {
      val minuteOfDay = RemoteFloat(RemoteContext.FLOAT_TIME_IN_MIN)
      minuteOfDay - minuteOfDay
    } else {
      0f.rf
    }
  val surfaceContainerBaseColour: RemoteColor =
    resolveWidgetColor(
      lightColorRes = android.R.color.system_surface_container_light,
      darkColorRes = android.R.color.system_surface_container_dark,
    )
  val surfaceContainerColor: RemoteColor =
    surfaceContainerBaseColour.copy(alpha = 0.8f.rf + minuteHeartbeat)
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
  val badgeBitmapsByPackage: Map<String, RemoteImageBitmap> =
    if (state is WidgetState.Configured) {
      val remoteBitmapByIcon = mutableMapOf<AppIcon.Loaded, RemoteImageBitmap>()
      state.items
        .asSequence()
        .filterIsInstance<WidgetItemState.Active>()
        .mapNotNull { it.originPackage }
        .distinct()
        .mapNotNull { pkg ->
          val icon = badgeLoader.load(pkg) as? AppIcon.Loaded ?: return@mapNotNull null
          pkg to remoteBitmapByIcon.getOrPut(icon) { icon.bitmap.asImageBitmap().rb }
        }
        .toMap()
    } else {
      emptyMap()
    }
  val cornerRadius = dimensionResource(android.R.dimen.system_app_widget_background_radius).rdp
  val shape = RemoteRoundedCornerShape(cornerRadius)
  val tiles: List<RemoteTileVisuals> =
    when (state) {
      is WidgetState.Unconfigured ->
        listOf(
          RemoteTileVisuals(
            iconRes = state.iconRes,
            contentDescription = state.contentDescription,
            iconTint = onSurfaceVariantColor,
            activeLabels = null,
            staticLabelText = "Sync...".rs,
          )
        )
      is WidgetState.Configured ->
        state.items.map { item ->
          buildTileVisuals(
            item = item,
            primaryColor = primaryColor,
            errorColor = errorColor,
            onSurfaceVariantColor = onSurfaceVariantColor,
            badgeBitmapsByPackage = badgeBitmapsByPackage,
          )
        }
    }

  val cardBackgroundModifier: RemoteModifier =
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

  // Wrap the clipped card inside an unclipped root box so root-level clipping works across players.
  RemoteBox(modifier = RemoteModifier.fillMaxSize()) {
    if (tiles.size == 1) {
      val tile: RemoteTileVisuals = tiles.single()
      RemoteColumn(
        modifier =
          cardBackgroundModifier
            .clickable(hostAction(id = FIRST_CLICK_ACTION_ID))
            .semantics(mergeDescendants = true) {}
            .padding(8.rdp),
        verticalArrangement = RemoteArrangement.Center,
        horizontalAlignment = RemoteAlignment.CenterHorizontally,
      ) {
        if (tile.activeLabels != null) {
          HoistDynamicExpressions(listOf(tile))
        }
        RemoteTileContent(
          tile = tile,
          iconSize = 32.rdp,
          badgeSizeDp = 14,
          badgeRingColour = surfaceContainerBaseColour,
          onSurfaceColor = onSurfaceColor,
        )
      }
    } else {
      val iconSize: RemoteDp = if (tiles.size <= 3) 28.rdp else 24.rdp
      val badgeSizeDp: Int = if (tiles.size <= 3) 12 else 10
      RemoteColumn(
        modifier = cardBackgroundModifier.padding(horizontal = 4.rdp, vertical = 8.rdp)
      ) {
        if (hasActiveTile) {
          HoistDynamicExpressions(tiles)
        }
        RemoteRow(
          modifier = RemoteModifier.fillMaxSize(),
          verticalAlignment = RemoteAlignment.CenterVertically,
        ) {
          for ((index, tile) in tiles.withIndex()) {
            RemoteColumn(
              modifier =
                RemoteModifier.weight(1f)
                  .fillMaxHeight()
                  .clickable(hostAction(id = FIRST_CLICK_ACTION_ID + index))
                  .semantics(mergeDescendants = true) {},
              verticalArrangement = RemoteArrangement.Center,
              horizontalAlignment = RemoteAlignment.CenterHorizontally,
            ) {
              RemoteTileContent(
                tile = tile,
                iconSize = iconSize,
                badgeSizeDp = badgeSizeDp,
                badgeRingColour = surfaceContainerBaseColour,
                onSurfaceColor = onSurfaceColor,
              )
            }
          }
        }
      }
    }
  }
}

private fun buildTileVisuals(
  item: WidgetItemState,
  primaryColor: RemoteColor,
  errorColor: RemoteColor,
  onSurfaceVariantColor: RemoteColor,
  badgeBitmapsByPackage: Map<String, RemoteImageBitmap>,
): RemoteTileVisuals =
  when (item) {
    is WidgetItemState.Syncing ->
      RemoteTileVisuals(
        iconRes = item.iconRes,
        contentDescription = item.contentDescription,
        iconTint = onSurfaceVariantColor,
        activeLabels = null,
        staticLabelText = "Sync...".rs,
      )
    is WidgetItemState.Unauthorized ->
      RemoteTileVisuals(
        iconRes = item.iconRes,
        contentDescription = item.contentDescription,
        iconTint = errorColor,
        activeLabels = null,
        staticLabelText = "Auth".rs,
      )
    is WidgetItemState.NoRecords ->
      RemoteTileVisuals(
        iconRes = item.iconRes,
        contentDescription = item.contentDescription,
        iconTint = onSurfaceVariantColor,
        activeLabels = null,
        staticLabelText = "None".rs,
      )
    is WidgetItemState.Active -> {
      val elapsed = RemoteDuration.since(item.lastRecordTime)
      RemoteTileVisuals(
        iconRes = item.iconRes,
        contentDescription = item.contentDescription,
        iconTint =
          (elapsed isGreaterThan WeightGlanceWidget.STALE_THRESHOLD).select(
            ifTrue = errorColor,
            ifFalse = primaryColor,
          ),
        activeLabels = buildActiveStalenessLabels(elapsed),
        staticLabelText = null,
        badgeBitmap = item.originPackage?.let(badgeBitmapsByPackage::get),
      )
    }
  }

/**
 * Materialises dynamic expression ops inside a childless `RemoteCanvas` (`CanvasContent`) so
 * Android 16's `LayoutComponent.inflate()` retains them in the `Container` tree and runs them
 * during `initializeContext` before `TextLayout.computeWrapSize`.
 */
@Composable
@RemoteComposable
private fun HoistDynamicExpressions(tiles: List<RemoteTileVisuals>) {
  RemoteCanvas(modifier = RemoteModifier.size(0.rdp)) {
    for (tile in tiles) {
      val labels: List<Pair<RemoteString, RemoteInt>> = tile.activeLabels ?: continue
      tile.iconTint.id
      for ((label, visibility) in labels) {
        label.id
        visibility.id
      }
    }
  }
}

@Composable
@RemoteComposable
private fun RemoteTileContent(
  tile: RemoteTileVisuals,
  iconSize: RemoteDp,
  badgeSizeDp: Int,
  badgeRingColour: RemoteColor,
  onSurfaceColor: RemoteColor,
) {
  if (tile.badgeBitmap != null) {
    val ringSizeDp: Int = badgeSizeDp + BADGE_RING_PADDING_DP * 2
    val ringRadius: RemoteDp = (ringSizeDp / 2).rdp
    RemoteBox(
      modifier = RemoteModifier.size(iconSize),
      contentAlignment = RemoteAlignment.BottomEnd,
    ) {
      RemoteBasicIcon(
        imageVector = RemoteImageVector.vectorResource(tile.iconRes),
        contentDescription = tile.contentDescription.rs,
        modifier = RemoteModifier.size(iconSize),
        tint = tile.iconTint,
      )
      RemoteBox(
        modifier =
          RemoteModifier.size(ringSizeDp.rdp)
            .drawWithContent {
              val radiusPx = ringRadius.toPx()
              drawRoundRect(
                paint =
                  RemotePaint {
                    isAntiAlias = true
                    style = PaintingStyle.Fill
                    color = badgeRingColour
                  },
                topLeft = RemoteOffset(0f.rf, 0f.rf),
                size = size,
                cornerRadius = RemoteOffset(radiusPx, radiusPx),
              )
              usePaint(RemotePaint()) {}
              drawContent()
            }
            .clip(RemoteRoundedCornerShape(ringRadius)),
        contentAlignment = RemoteAlignment.Center,
      ) {
        RemoteImage(
          bitmap = tile.badgeBitmap,
          contentDescription = null,
          modifier = RemoteModifier.size(badgeSizeDp.rdp),
          contentScale = ContentScale.Fit,
          alpha = 1f.rf,
        )
      }
    }
  } else {
    RemoteBasicIcon(
      imageVector = RemoteImageVector.vectorResource(tile.iconRes),
      contentDescription = tile.contentDescription.rs,
      modifier = RemoteModifier.size(iconSize),
      tint = tile.iconTint,
    )
  }
  RemoteBox(modifier = RemoteModifier.height(4.rdp))
  val activeLabels: List<Pair<RemoteString, RemoteInt>>? = tile.activeLabels
  val staticLabelText: RemoteString? = tile.staticLabelText
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

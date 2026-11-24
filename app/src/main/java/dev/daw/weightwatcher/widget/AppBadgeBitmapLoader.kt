package dev.daw.weightwatcher.widget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import androidx.health.connect.client.HealthConnectClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val BADGE_BITMAP_SIZE_PX = 48

/**
 * Matches Health Connect on-device tracking Synthetic Package Names (SPNs) of the form
 * `com.android.healthconnect.<device_type>.j<hex_hash>`.
 *
 * See
 * [Health Connect steps attribution](https://developer.android.com/health-and-fitness/health-connect/features/steps#attribution-change)
 * and
 * [read data attribution](https://developer.android.com/health-and-fitness/health-connect/read-data#attribution-change).
 */
private val NATIVE_HEALTH_CONNECT_SPN_REGEX =
  Regex("""^com\.android\.healthconnect\.[a-z0-9_]+\.j[0-9a-f]+$""")

/** Resolved attribution badge icon for a Health Connect origin package. */
sealed interface AppIcon {
  /**
   * A loaded circular badge [bitmap], identified structurally by its [resolvedPackage] so aliases
   * (such as multiple synthetic Health Connect origins) deduplicate cleanly.
   */
  class Loaded(val resolvedPackage: String, val bitmap: Bitmap) : AppIcon {
    override fun equals(other: Any?): Boolean =
      other is Loaded && resolvedPackage == other.resolvedPackage

    override fun hashCode(): Int = resolvedPackage.hashCode()
  }

  data object Missing : AppIcon
}

/**
 * Loads and caches circular launcher-icon [AppIcon]s for widget attribution badges.
 *
 * Native Health Connect tracking records carry synthetic
 * `com.android.healthconnect.<device>.j<hex>` origin packages that are not installed apps; those
 * fall back to the device's Health Connect system app icon resolved via
 * [HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS]. Missing apps are not cached so newly
 * installed apps appear without restarting the process.
 */
@Singleton
class AppBadgeBitmapLoader
@Inject
constructor(@param:ApplicationContext private val context: Context) {

  // TODO: If we switch to an expiring cache (e.g. Guava CacheBuilder or Caffeine), we could also
  // cache AppIcon.Missing with a shorter TTL to avoid repeated PackageManager lookups while still
  // picking up newly installed apps.
  private val cache = ConcurrentHashMap<String, AppIcon.Loaded>()

  fun load(packageName: String): AppIcon {
    if (packageName.isEmpty()) {
      return AppIcon.Missing
    }
    val cached: AppIcon.Loaded? = cache[packageName]
    if (cached != null) {
      return cached
    }

    val installed: AppIcon.Loaded? = loadInstalled(packageName)
    if (installed != null) {
      return installed
    }
    val fallback: AppIcon.Loaded =
      loadNativeHealthConnectFallback(packageName) ?: return AppIcon.Missing
    return cache.putIfAbsent(packageName, fallback) ?: fallback
  }

  private fun loadInstalled(packageName: String): AppIcon.Loaded? {
    val cached: AppIcon.Loaded? = cache[packageName]
    if (cached != null) {
      return cached
    }
    val icon: Drawable =
      try {
        context.packageManager.getApplicationIcon(packageName)
      } catch (_: PackageManager.NameNotFoundException) {
        return null
      }
    return cache.computeIfAbsent(packageName) { key ->
      AppIcon.Loaded(
        resolvedPackage = key,
        bitmap = renderCircularBadgeBitmap(icon, BADGE_BITMAP_SIZE_PX),
      )
    }
  }

  private fun loadNativeHealthConnectFallback(packageName: String): AppIcon.Loaded? {
    if (!NATIVE_HEALTH_CONNECT_SPN_REGEX.matches(packageName)) {
      return null
    }
    val controllerPackage: String =
      context.packageManager
        .resolveActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS), /* flags= */ 0)
        ?.activityInfo
        ?.packageName ?: return null
    return loadInstalled(controllerPackage)
  }
}

private fun renderCircularBadgeBitmap(drawable: Drawable, sizePx: Int): Bitmap {
  val output: Bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(output)
  val radius: Float = sizePx / 2f
  val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  canvas.drawCircle(radius, radius, radius, maskPaint)
  val srcPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
  canvas.saveLayer(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), srcPaint)
  drawable.setBounds(0, 0, sizePx, sizePx)
  drawable.draw(canvas)
  canvas.restore()
  return output
}

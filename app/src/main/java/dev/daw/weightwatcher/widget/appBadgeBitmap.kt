package dev.daw.weightwatcher.widget

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

private const val BADGE_BITMAP_SIZE_PX = 48

private val badgeCacheByAppContext = WeakHashMap<Context, ConcurrentHashMap<String, Bitmap>>()

/**
 * Loads and caches a circular [Bitmap] of [packageName]'s launcher icon for attribution badges, or
 * returns `null` if [packageName] is not installed or not visible to this app.
 *
 * Cached per [Context.getApplicationContext] so repeated widget renders reuse the decoded icon
 * without leaking across test application instances.
 */
internal fun loadAppBadgeBitmap(
  context: Context,
  packageName: String,
  sizePx: Int = BADGE_BITMAP_SIZE_PX,
): Bitmap? {
  if (packageName.isEmpty()) {
    return null
  }
  val appContext: Context = context.applicationContext ?: context
  val cache: ConcurrentHashMap<String, Bitmap> =
    synchronized(badgeCacheByAppContext) {
      badgeCacheByAppContext.getOrPut(appContext) { ConcurrentHashMap() }
    }
  val cacheKey = "$packageName@$sizePx"
  cache[cacheKey]?.let {
    return it
  }

  val icon: Drawable =
    try {
      appContext.packageManager.getApplicationIcon(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
      return null
    }
  val bitmap: Bitmap = renderCircularBadgeBitmap(icon, sizePx)
  cache.putIfAbsent(cacheKey, bitmap)?.let {
    return it
  }
  return bitmap
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

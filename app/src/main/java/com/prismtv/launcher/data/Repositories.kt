package com.prismtv.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

// ------------------------------------------------------------------------------------ apps

class AppRepository(private val context: Context) {

    /** Packages we like to see first on a fresh install (if present). */
    private val preferredOrder = listOf(
        "org.xbmc.kodi",
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.disney.disneyplus",
        "com.amazon.amazonvideo.livingroom",
        "com.plexapp.android",
        "org.jellyfin.androidtv",
        "com.wbd.stream",
        "com.spotify.tv.android",
        "com.apple.atve.androidtv.appletv",
    )

    fun load(): List<LauncherApp> {
        val pm = context.packageManager
        val tvActivities = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        )
        val phoneActivities = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val seen = HashSet<String>()
        val result = ArrayList<LauncherApp>()
        val all = tvActivities.map { it to true } + phoneActivities.map { it to false }
        for ((ri, isTv) in all) {
            val info = ri.activityInfo ?: continue
            val pkg = info.packageName
            if (pkg == context.packageName || !seen.add(pkg)) continue
            try {
                val label = ri.loadLabel(pm)?.toString() ?: pkg
                val bannerDrawable = info.loadBanner(pm) ?: info.applicationInfo?.loadBanner(pm)
                val banner = bannerDrawable?.let { d ->
                    val w = if (d.intrinsicWidth > 0) d.intrinsicWidth.coerceAtMost(640) else 320
                    val h = if (d.intrinsicHeight > 0) d.intrinsicHeight.coerceAtMost(360) else 180
                    d.toBitmap(w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
                }
                val iconBitmap = ri.loadIcon(pm).toBitmap(144, 144, Bitmap.Config.ARGB_8888)
                result += LauncherApp(
                    id = "$pkg/${info.name}",
                    label = label,
                    packageName = pkg,
                    activityName = info.name,
                    banner = banner,
                    icon = iconBitmap.asImageBitmap(),
                    tint = dominantColor(iconBitmap),
                    isTvApp = isTv,
                )
            } catch (_: Throwable) {
                // A misbehaving package should never take the launcher down.
            }
        }
        return result.sortedWith(
            compareBy<LauncherApp> {
                val i = preferredOrder.indexOf(it.packageName)
                if (i >= 0) i else Int.MAX_VALUE
            }.thenBy { !it.isTvApp }
                .thenBy { it.packageName.contains("settings") }
                .thenBy { it.label.lowercase() }
        )
    }

    private fun dominantColor(b: Bitmap): Color = try {
        val px = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
        Color(px).copy(alpha = 1f)
    } catch (_: Throwable) {
        Color(0xFF2B2E36)
    }
}

// ------------------------------------------------------------------------------------ recommendations

/**
 * Reads Android TV "Watch Next" and preview-channel programs. Many devices only allow this for
 * system launchers; failures simply fall back to app artwork.
 */
class RecommendationsRepository(private val context: Context) {
    private val watchNext = Uri.parse("content://android.media.tv/watch_next_program")
    private val preview = Uri.parse("content://android.media.tv/preview_program")

    fun load(): Map<String, List<HeroItem>> {
        val out = LinkedHashMap<String, MutableList<HeroItem>>()
        for (uri in listOf(watchNext, preview)) {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    fun col(name: String) = c.getColumnIndex(name)
                    val iPkg = col("package_name")
                    val iTitle = col("title")
                    val iDesc = col("short_description")
                    val iThumb = col("thumbnail_uri")
                    val iPoster = col("poster_art_uri")
                    val iIntent = col("intent_uri")
                    val iGenre = col("genre")
                    val iDate = col("release_date")
                    var n = 0
                    while (c.moveToNext() && n < 300) {
                        n++
                        fun s(i: Int): String? = if (i >= 0) c.getString(i)?.takeIf { it.isNotBlank() } else null
                        val pkg = s(iPkg) ?: continue
                        val title = s(iTitle) ?: continue
                        val art = s(iThumb) ?: s(iPoster) ?: continue
                        val list = out.getOrPut(pkg) { mutableListOf() }
                        if (list.size >= 6) continue
                        list += HeroItem(
                            key = "$pkg:$title:${list.size}",
                            appId = pkg,
                            appLabel = "",
                            title = title,
                            meta = listOfNotNull(s(iGenre), s(iDate)).joinToString(" · "),
                            description = s(iDesc) ?: "",
                            art = UriArt(art),
                            intentUri = s(iIntent),
                        )
                    }
                }
            } catch (_: Throwable) {
                // Not permitted on this device – fine.
            }
        }
        return out
    }
}

// ------------------------------------------------------------------------------------ recents

class RecentsRepository(private val context: Context) {
    private val sp = context.getSharedPreferences("prism_recents", Context.MODE_PRIVATE)

    fun history(): List<String> =
        (sp.getString("list", "") ?: "").split(',').filter { it.isNotBlank() }

    fun record(pkg: String) {
        val list = listOf(pkg) + history().filter { it != pkg }
        sp.edit().putString("list", list.take(20).joinToString(",")).apply()
    }

    fun remove(pkg: String) {
        sp.edit().putString("list", history().filter { it != pkg }.joinToString(",")).apply()
    }

    fun hasUsageAccess(): Boolean = try {
        val aom = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) {
        false
    }

    /** Most recent first. */
    fun recentPackages(): List<String> {
        val hist = history()
        if (!hasUsageAccess()) return hist
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 3L * 24 * 3600 * 1000, now)
            val fromUsage = stats
                .filter { it.lastTimeUsed > 0 && it.totalTimeInForeground > 0 && it.packageName != context.packageName }
                .sortedByDescending { it.lastTimeUsed }
                .map { it.packageName }
            (fromUsage + hist).distinct()
        } catch (_: Throwable) {
            hist
        }
    }
}

// ------------------------------------------------------------------------------------ artwork

class HeroArt(
    val full: ImageBitmap,
    /** Lightly blurred, low-res copy (fallback glass on Android < 12). */
    val light: ImageBitmap,
    /** Heavily blurred, low-res copy (grid wallpaper, frosted fallback). */
    val heavy: ImageBitmap,
)

class ArtRepository(private val context: Context) {
    private val cache = LruCache<String, HeroArt>(6)

    fun cached(ref: ArtRef): HeroArt? = cache.get(ref.key)

    suspend fun load(ref: ArtRef): HeroArt? = withContext(Dispatchers.IO) {
        cache.get(ref.key)?.let { return@withContext it }
        val bmp = try {
            decode(ref)
        } catch (_: Throwable) {
            null
        } ?: return@withContext null
        val frame = cropTo16x9(bmp)
        val art = HeroArt(
            full = frame.asImageBitmap(),
            light = Blur.blur(frame, 240, 3).asImageBitmap(),
            heavy = Blur.blur(frame, 96, 4).asImageBitmap(),
        )
        cache.put(ref.key, art)
        art
    }

    private fun decode(ref: ArtRef): Bitmap? = when (ref) {
        is ResArt -> BitmapFactory.decodeResource(
            context.resources, ref.resId,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        )
        is UriArt -> {
            val uri = Uri.parse(ref.uri)
            val stream = when (uri.scheme) {
                "http", "https" -> (URL(ref.uri).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }.inputStream
                else -> context.contentResolver.openInputStream(uri)
            }
            stream?.use { BitmapFactory.decodeStream(it) }
        }
    }

    private fun cropTo16x9(src: Bitmap): Bitmap {
        val target = 16f / 9f
        val ratio = src.width.toFloat() / src.height
        val cropped = when {
            ratio > target + 0.01f -> {
                val w = (src.height * target).roundToInt()
                Bitmap.createBitmap(src, (src.width - w) / 2, 0, w, src.height)
            }
            ratio < target - 0.01f -> {
                val h = (src.width / target).roundToInt()
                Bitmap.createBitmap(src, 0, (src.height - h) / 4, src.width, h)
            }
            else -> src
        }
        return if (cropped.width > 1920) {
            Bitmap.createScaledBitmap(cropped, 1920, 1080, true)
        } else cropped
    }
}

/** Small, dependency-free box blur (3 passes ≈ gaussian) on a downscaled copy. */
object Blur {
    fun blur(src: Bitmap, targetWidth: Int, radius: Int, passes: Int = 3): Bitmap {
        val w = targetWidth.coerceAtLeast(8)
        val h = (w * src.height / src.width.toFloat()).roundToInt().coerceAtLeast(8)
        var cur = src
        while (cur.width / 2 >= w) {
            cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
        }
        val scaled = Bitmap.createScaledBitmap(cur, w, h, true)
        val a = IntArray(w * h)
        scaled.getPixels(a, 0, w, 0, 0, w, h)
        val b = IntArray(w * h)
        repeat(passes) {
            pass(a, b, w, h, radius, horizontal = true)
            pass(b, a, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[index(i, line, len, w, horizontal)]
                sa += p ushr 24
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until len) {
                dst[index(i, line, len, w, horizontal)] =
                    ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pOut = src[index(i - r, line, len, w, horizontal)]
                val pIn = src[index(i + r + 1, line, len, w, horizontal)]
                sa += (pIn ushr 24) - (pOut ushr 24)
                sr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                sg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                sb += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }

    private fun index(i: Int, line: Int, len: Int, w: Int, horizontal: Boolean): Int {
        val c = i.coerceIn(0, len - 1)
        return if (horizontal) line * w + c else c * w + line
    }
}

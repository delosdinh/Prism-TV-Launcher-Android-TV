package com.prismtv.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
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
import androidx.compose.ui.graphics.asAndroidBitmap
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
                    // Settings shows a banner at most ~460 px wide, so 512 × 288 looks the same as the
                    // full-size 640 × 360 some apps ship, for a third less memory per app.
                    val w = if (d.intrinsicWidth > 0) d.intrinsicWidth.coerceAtMost(512) else 320
                    val h = if (d.intrinsicHeight > 0) d.intrinsicHeight.coerceAtMost(288) else 180
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
                    val iProvider = col("internal_provider_id")
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
                        if (list.size >= 10) continue
                        list += HeroItem(
                            key = "$pkg:$title:${list.size}",
                            appId = pkg,
                            appLabel = "",
                            title = title,
                            meta = listOfNotNull(s(iGenre), s(iDate)).joinToString(" · "),
                            description = s(iDesc) ?: "",
                            art = UriArt(art),
                            intentUri = s(iIntent),
                            titleArt = TitleLogos.refFor(s(iIntent), s(iProvider)),
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
        sp.edit().putString("list", list.take(20).joinToString(",")).remove("dismissed:$pkg").apply()
    }

    /**
     * Closes [pkg] in the App Switcher. Android's usage history still lists it, so the dismissal time
     * is kept too: the app only comes back once it's used again after being swiped away.
     */
    fun remove(pkg: String) {
        sp.edit()
            .putString("list", history().filter { it != pkg }.joinToString(","))
            .putLong("dismissed:$pkg", System.currentTimeMillis())
            .apply()
    }

    private fun dismissedAt(pkg: String): Long = sp.getLong("dismissed:$pkg", 0L)

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

    /**
     * Most recent first. Only apps that were really on screen count: Android's usage totals also
     * move for apps that just ran in the background (updates, jobs, the Assistant), which made random
     * apps show up in the App Switcher.
     */
    fun recentPackages(): List<String> {
        val hist = history()
        if (!hasUsageAccess()) return hist
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 3L * 24 * 3600 * 1000, now)
            val e = UsageEvents.Event()
            val openedAt = HashMap<String, Long>()
            val since = HashMap<String, Long>()
            val shownFor = HashMap<String, Long>()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val pkg = e.packageName ?: continue
                @Suppress("DEPRECATION")
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        since[pkg] = e.timeStamp
                        openedAt[pkg] = e.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> since.remove(pkg)?.let { t ->
                        shownFor[pkg] = (shownFor[pkg] ?: 0L) + (e.timeStamp - t)
                    }
                }
            }
            since.forEach { (pkg, t) -> shownFor[pkg] = (shownFor[pkg] ?: 0L) + (now - t) }
            val skip = ignoredPackages()
            val fromUsage = openedAt.entries
                .filter { (pkg, t) ->
                    pkg != context.packageName && pkg !in skip && (shownFor[pkg] ?: 0L) >= 2500L && t > dismissedAt(pkg)
                }
                .sortedByDescending { it.value }
                .map { it.key }
            (fromUsage + hist.filter { it !in skip }).distinct()
        } catch (_: Throwable) {
            hist
        }
    }

    /** Launchers, system UI, settings and the Assistant: never "apps" in the switcher. */
    private fun ignoredPackages(): Set<String> {
        val out = hashSetOf(
            "com.android.systemui", "com.android.settings", "com.android.tv.settings", "com.google.android.katniss",
            "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher",
            "com.android.packageinstaller", "com.google.android.packageinstaller", "com.google.android.backdrop",
            "com.google.android.apps.tv.dreamx", "com.google.android.tvrecommendations", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.google.android.inputmethod.latin", "com.google.android.leanback.ime",
        )
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(home, 0).forEach { out += it.activityInfo.packageName }
        } catch (_: Throwable) {
        }
        return out
    }
}

// ------------------------------------------------------------------------------------ artwork

private const val MB = 1024 * 1024

class HeroArt(
    val full: ImageBitmap,
    /** Lightly blurred, low-res copy (fallback glass on Android < 12). */
    val light: ImageBitmap,
    /** Heavily blurred, low-res copy (grid wallpaper, frosted fallback). */
    val heavy: ImageBitmap,
    /** Average brightness (0..1) of the lower part, where app names sit over it. */
    val luma: Float = 0.3f,
)

class ArtRepository(private val context: Context) {
    /** Boxes with 2 GB or less get smaller caches (Android kills a launcher that hogs memory). */
    private val lowRam: Boolean = run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        am.isLowRamDevice || am.memoryClass <= 192 || info.totalMem < 2_500_000_000L
    }

    /** Full-screen artwork, limited by size: about four 1080p backdrops (three on small boxes). */
    private val cache = object : LruCache<String, HeroArt>(if (lowRam) 26 * MB else 44 * MB) {
        override fun sizeOf(key: String, value: HeroArt): Int = bytes(value.full) + bytes(value.light) + bytes(value.heavy)
    }

    /**
     * Lite Mode: backdrops at 1280 × 720 in 16-bit colour (a fifth of the memory of 1080p) and smaller
     * caches. Artwork already loaded is dropped so it reloads at the new size.
     */
    var lite: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            cache.evictAll()
            cache.resize(if (value || lowRam) 18 * MB else 44 * MB)
            thumbs.resize(if (value || lowRam) 20 * MB else 40 * MB)
        }

    private fun bytes(b: ImageBitmap): Int = runCatching { b.asAndroidBitmap().allocationByteCount }.getOrDefault(b.width * b.height * 4)

    /** Android asked for memory back: let go of artwork that isn't on screen. */
    fun trim(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> { cache.evictAll(); thumbs.evictAll() }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                cache.trimToSize(cache.maxSize() / 3)
                thumbs.trimToSize(thumbs.maxSize() / 3)
            }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> cache.trimToSize(cache.maxSize() / 2)
        }
    }

    /**
     * Android 8+: keep display-only artwork in graphics memory alone. A normal bitmap is held twice once
     * it's drawn (in the app and again as a GPU texture), which doubled the cost of every backdrop.
     */
    private fun gpuOnly(b: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT < 28) return b
        return runCatching { b.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()?.also { if (it !== b) b.recycle() } ?: b
    }

    private class Brand(val banner: Bitmap?, val icon: Bitmap?, val tint: Int)

    private val brands = java.util.concurrent.ConcurrentHashMap<String, Brand>()

    fun registerBrand(appId: String, banner: Bitmap?, icon: Bitmap?, tint: Int) {
        brands[appId] = Brand(banner, icon, tint)
    }

    fun cached(ref: ArtRef): HeroArt? = cache.get(ref.key)

    suspend fun load(ref: ArtRef): HeroArt? = withContext(Dispatchers.IO) {
        cache.get(ref.key)?.let { return@withContext it }
        val bmp = try {
            decode(ref)
        } catch (_: Throwable) {
            null
        } ?: return@withContext null
        val frame = cropTo16x9(bmp).let { f ->
            if (f !== bmp) bmp.recycle()
            if (lite && f.width > 1280) Bitmap.createScaledBitmap(f, 1280, 720, true).also { if (it !== f) f.recycle() } else f
        }
        val heavy = Blur.blur(frame, 96, 4)
        // The lightly blurred copy is only the glass fallback where live blur isn't used.
        val light = if (Build.VERSION.SDK_INT < 31 || lite) Blur.blur(frame, 240, 3) else heavy
        val shown = if (lite) frame.copy(Bitmap.Config.RGB_565, false)?.also { frame.recycle() } ?: frame else frame
        val art = HeroArt(
            full = gpuOnly(shown).asImageBitmap(),
            light = light.asImageBitmap(),
            heavy = heavy.asImageBitmap(),
            luma = lowerLuma(heavy),
        )
        cache.put(ref.key, art)
        art
    }

    /** Cards, logos and previews, limited by size. */
    private val thumbs = object : LruCache<String, ImageBitmap>(if (lowRam) 20 * MB else 40 * MB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = bytes(value)
    }

    fun cachedThumb(ref: ArtRef): ImageBitmap? = thumbs.get(ref.key)

    /** A small copy (about [width] px wide) for cards and previews. */
    suspend fun thumb(ref: ArtRef, width: Int = 400): ImageBitmap? = withContext(Dispatchers.IO) {
        thumbs.get(ref.key)?.let { return@withContext it }
        val bmp = try {
            decode(ref, maxSide = width * 2)
        } catch (_: Throwable) {
            null
        } ?: return@withContext null
        val h = (width * bmp.height.toFloat() / bmp.width).roundToInt().coerceAtLeast(1)
        val out = if (bmp.width > width) Bitmap.createScaledBitmap(bmp, width, h, true).also { if (it !== bmp) bmp.recycle() } else bmp
        gpuOnly(out).asImageBitmap().also { thumbs.put(ref.key, it) }
    }

    /** Mean luminance of the lower 70% of a (small) bitmap. */
    private fun lowerLuma(b: Bitmap): Float = try {
        val w = b.width
        val h = b.height
        val top = (h * 0.3f).toInt()
        val px = IntArray(w * (h - top))
        b.getPixels(px, 0, w, 0, top, w, h - top)
        var sum = 0.0
        for (c in px) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val bl = c and 0xFF
            sum += (0.2126 * r + 0.7152 * g + 0.0722 * bl) / 255.0
        }
        (sum / px.size.coerceAtLeast(1)).toFloat()
    } catch (_: Throwable) {
        0.3f
    }

    private fun decode(ref: ArtRef, maxSide: Int = 2400): Bitmap? = when (ref) {
        is ResArt -> BitmapFactory.decodeResource(
            context.resources, ref.resId,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                if (maxSide < 1200) inSampleSize = 4
            }
        )
        is BannerArt -> brands[ref.appId]?.let { brandBackdrop(it.banner, it.icon, it.tint) }
        is UriArt -> {
            val uri = Uri.parse(ref.uri)
            val stream = when (uri.scheme) {
                "http", "https" -> (URL(ref.uri).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }.inputStream
                else -> null
            }
            if (stream != null) {
                stream.use { BitmapFactory.decodeStream(it) }
            } else {
                // The user's own pictures can be huge: read the size first and sample down.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        }
    }

    /**
     * Top Shelf for apps that publish no artwork: the app's banner large and centred on a field
     * of its own colours (a heavily blurred, enlarged copy of the banner), like tvOS app Top Shelves.
     */
    private fun brandBackdrop(banner: Bitmap?, icon: Bitmap?, tint: Int): Bitmap {
        val out = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(darken(tint, 0.55f))
        val src = banner ?: icon
        if (src != null) {
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
            val wash = Blur.blur(src, 48, 3)
            c.drawBitmap(wash, null, android.graphics.RectF(-160f, -160f, 2080f, 1240f), paint)
            c.drawColor(0x59000000)
            val isBanner = banner != null
            val w = if (isBanner) 760f else 300f
            val h = w * src.height / src.width
            val left = (1920f - w) / 2f
            val top = (1080f - h) / 2f - 30f
            val rect = android.graphics.RectF(left, top, left + w, top + h)
            val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x66000000
                maskFilter = android.graphics.BlurMaskFilter(48f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            val radius = if (isBanner) 44f else w * 0.22f
            c.drawRoundRect(android.graphics.RectF(rect.left, rect.top + 24f, rect.right, rect.bottom + 24f), radius, radius, shadow)
            val save = c.save()
            val clip = android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) }
            c.clipPath(clip)
            c.drawColor(tint)
            c.drawBitmap(src, null, rect, paint)
            c.restoreToCount(save)
        }
        return out
    }

    private fun darken(argb: Int, f: Float): Int {
        val r = ((argb shr 16) and 0xFF) * (1 - f)
        val g = ((argb shr 8) and 0xFF) * (1 - f)
        val b = (argb and 0xFF) * (1 - f)
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    /** Portrait/square poster art: poster on the right over a wash of its own colours. */
    private fun posterBackdrop(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        c.drawBitmap(Blur.blur(src, 64, 4), null, android.graphics.RectF(-120f, -320f, 2040f, 1400f), paint)
        c.drawColor(0x40000000)
        val h = 900f
        val w = h * src.width / src.height
        val rect = android.graphics.RectF(1920f - 150f - w, 90f, 1920f - 150f, 90f + h)
        val save = c.save()
        c.clipPath(android.graphics.Path().apply { addRoundRect(rect, 28f, 28f, android.graphics.Path.Direction.CW) })
        c.drawBitmap(src, null, rect, paint)
        c.restoreToCount(save)
        return out
    }

    private fun cropTo16x9(src: Bitmap): Bitmap {
        val target = 16f / 9f
        val ratio = src.width.toFloat() / src.height
        if (ratio < 1.25f) return posterBackdrop(src)
        val cropped = when {
            ratio > target + 0.01f -> {
                val w = (src.height * target).roundToInt()
                Bitmap.createBitmap(src, (src.width - w) / 2, 0, w, src.height)
            }
            ratio < target - 0.01f -> {
                val h = (src.width / target).roundToInt()
                Bitmap.createBitmap(src, 0, (src.height - h) / 2, src.width, h) // centred: drops 4:3 letterboxing
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

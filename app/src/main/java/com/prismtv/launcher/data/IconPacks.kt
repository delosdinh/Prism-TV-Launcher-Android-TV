package com.prismtv.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser

/**
 * Icon packs (the common launcher format: an appfilter.xml mapping each app's activity to a drawable)
 * and banners you pick from your own pictures. TV banner packs replace an app's banner outright; square
 * phone icon packs are placed on a tile of the app's own colour, like a tvOS app icon.
 */
object IconPacks {
    data class Pack(val pkg: String, val label: String)

    // A pack's appfilter.xml can list thousands of apps: read it once per pack version.
    private var filterKey: String? = null
    private var filterMap: Map<String, String> = emptyMap()
    // Banners made from your pictures, by picture (512×288 each).
    private val photoBanners = object : android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun memoryLine(): String = com.prismtv.launcher.system.BitmapTally("banners from your pictures").also { t ->
        photoBanners.snapshot().values.forEach { t.add(it) }
    }.toString()

    private val themeActions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
    )

    /** Installed icon packs. */
    fun installed(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, Pack>()
        themeActions.forEach { action ->
            runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrNull().orEmpty().forEach { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@forEach
                if (pkg !in seen) seen[pkg] = Pack(pkg, runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg))
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    /** The banner for each app from [pack], applied on top of [apps]; apps the pack doesn't cover keep theirs. */
    fun apply(ctx: Context, pack: String, apps: List<LauncherApp>): List<LauncherApp> {
        if (pack.isEmpty()) return apps
        val res = runCatching { ctx.packageManager.getResourcesForApplication(pack) }.getOrNull() ?: return apps
        val version = runCatching { ctx.packageManager.getPackageInfo(pack, 0).lastUpdateTime }.getOrDefault(0L)
        val key = "$pack@$version"
        val map = if (key == filterKey) filterMap else appFilter(ctx, pack, res).also {
            filterKey = key
            filterMap = it
        }
        if (map.isEmpty()) return apps
        return apps.map { app ->
            val activity = app.activityName ?: return@map app
            val drawableName = map["${app.packageName}/$activity"] ?: map["${app.packageName}/"] ?: return@map app
            val id = res.getIdentifier(drawableName, "drawable", pack).takeIf { it != 0 }
                ?: res.getIdentifier(drawableName, "mipmap", pack).takeIf { it != 0 }
                ?: return@map app
            val d = runCatching { androidx.core.content.res.ResourcesCompat.getDrawable(res, id, null) }.getOrNull() ?: return@map app
            app.copy(banner = bannerFrom(d, app.tint.toArgb()).asImageBitmap())
        }
    }

    /** One of your pictures as an app's banner: centre-cropped to 16:9 at banner size. */
    fun bannerFromPhoto(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 288) sample *= 2
        val src = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val out = Bitmap.createBitmap(512, 288, Bitmap.Config.ARGB_8888)
        val scale = maxOf(512f / src.width, 288f / src.height)
        val w = 512f / scale
        val h = 288f / scale
        val left = ((src.width - w) / 2f).toInt()
        val top = ((src.height - h) / 2f).toInt()
        Canvas(out).drawBitmap(src, Rect(left, top, left + w.toInt(), top + h.toInt()), Rect(0, 0, 512, 288), Paint(Paint.FILTER_BITMAP_FLAG))
        out
    }.getOrNull()

    /** Your banners (app id → "photo:<uri>") on top of [apps]. */
    fun applyCustom(ctx: Context, custom: Map<String, String>, apps: List<LauncherApp>): List<LauncherApp> {
        if (custom.isEmpty()) return apps
        return apps.map { app ->
            val ref = custom[app.id] ?: return@map app
            val b = photoBanners.get(ref) ?: bannerFromPhoto(ctx, Uri.parse(ref.removePrefix("photo:")))?.also { photoBanners.put(ref, it) }
                ?: return@map app
            app.copy(banner = b.asImageBitmap())
        }
    }

    private fun bannerFrom(d: Drawable, tint: Int): Bitmap {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: 192
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: 192
        // A TV banner pack: use it as the banner.
        if (iw.toFloat() / ih > 1.4f) return d.toBitmap(512, (512f * ih / iw).toInt().coerceIn(200, 360), Bitmap.Config.ARGB_8888)
        // A square icon: centred on the app's colour.
        val out = Bitmap.createBitmap(512, 288, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(tint)
        val icon = d.toBitmap(200, 200, Bitmap.Config.ARGB_8888)
        c.drawBitmap(icon, null, RectF(156f, 44f, 356f, 244f), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** "package/activity" → drawable name, from the pack's appfilter.xml (res/xml or assets). */
    private fun appFilter(ctx: Context, pack: String, res: android.content.res.Resources): Map<String, String> {
        val out = HashMap<String, String>()
        fun read(p: XmlPullParser) {
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.name == "item") {
                    val component = p.getAttributeValue(null, "component")
                    val drawable = p.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) {
                        // ComponentInfo{com.example.app/com.example.app.MainActivity}
                        val inner = component.substringAfter('{', "").substringBefore('}', "")
                        val pkg = inner.substringBefore('/')
                        var cls = inner.substringAfter('/', "")
                        if (cls.startsWith(".")) cls = pkg + cls
                        if (pkg.isNotEmpty()) {
                            out["$pkg/$cls"] = drawable
                            out.putIfAbsent("$pkg/", drawable)
                        }
                    }
                }
                ev = p.next()
            }
        }
        val xmlId = res.getIdentifier("appfilter", "xml", pack)
        if (xmlId != 0) {
            runCatching {
                val parser = res.getXml(xmlId)
                try { read(parser) } finally { parser.close() }
            }
        } else {
            runCatching {
                val packCtx = ctx.createPackageContext(pack, 0)
                packCtx.assets.open("appfilter.xml").use { stream ->
                    val parser = android.util.Xml.newPullParser()
                    parser.setInput(stream, null)
                    read(parser)
                }
            }
        }
        return out
    }
}

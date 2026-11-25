package com.prismtv.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap

/**
 * The TV's HDMI (and other pass-through) inputs as Home Screen tiles, like Projectivy's input
 * shortcuts: one press to the console or set-top box. Only TVs have these; streaming boxes don't.
 */
object TvInputs {
    const val Prefix = "input:"

    data class Input(val id: String, val label: String, val pkg: String)

    /** CI screenshots: two pretend inputs (boxes and emulators have none). */
    @Volatile var demo = false

    /** Banners already drawn, by name and port, so reloading the inputs doesn't redraw them. */
    private val banners = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

    fun list(ctx: Context): List<Input> {
        if (demo) return listOf(
            Input("demo.hdmi/HW1", "HDMI 1", "demo.hdmi"),
            Input("demo.hdmi/HW2", "HDMI 2", "demo.hdmi"),
        )
        val m = ctx.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return runCatching { m.tvInputList }.getOrNull().orEmpty()
            // Hardware ports only: devices behind them (HDMI-CEC) show up as children of a port.
            .filter { it.isPassthroughInput && it.parentId == null }
            .mapNotNull { info -> runCatching { Input(info.id, label(ctx, info), info.serviceInfo.packageName) }.getOrNull() }
    }

    private fun label(ctx: Context, info: TvInputInfo): String =
        (info.loadCustomLabel(ctx)?.toString()?.takeIf { it.isNotBlank() } ?: info.loadLabel(ctx).toString())

    /** Inputs as launcher tiles, with your own names where you've given them one. */
    fun apps(ctx: Context, names: Map<String, String>): List<LauncherApp> = list(ctx).map { input ->
        val name = names[input.id]?.takeIf { it.isNotBlank() } ?: input.label
        LauncherApp(
            id = Prefix + input.id,
            label = name,
            packageName = input.pkg,
            activityName = null,
            banner = (if (name != input.label) input.label else null).let { port ->
                synchronized(banners) { banners.getOrPut("$name|$port") { banner(name, port).asImageBitmap() } }
            },
            tint = Color(0xFF2E3340),
        )
    }

    fun isInput(app: LauncherApp): Boolean = app.id.startsWith(Prefix)

    /** Switches the TV to the input (through its Live TV / Inputs app). */
    fun intent(app: LauncherApp): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(app.id.removePrefix(Prefix)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** A graphite banner with an HDMI port and the input's name (and the port, when renamed). */
    private fun banner(name: String, port: String?): Bitmap {
        val w = 320
        val h = 180
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF434956.toInt(), 0xFF1B1E25.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        // The port: a wide trapezoid with chamfered lower corners.
        val cx = w / 2f
        val top = 44f
        val outline = Path().apply {
            moveTo(cx - 46f, top)
            lineTo(cx + 46f, top)
            lineTo(cx + 46f, top + 13f)
            lineTo(cx + 34f, top + 29f)
            lineTo(cx - 34f, top + 29f)
            lineTo(cx - 46f, top + 13f)
            close()
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(outline, stroke)
        val pins = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
        c.drawRoundRect(cx - 28f, top + 10f, cx + 28f, top + 15f, 2.5f, 2.5f, pins)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 30f
        }
        while (text.measureText(name) > w - 36 && text.textSize > 16f) text.textSize -= 1f
        val baseline = if (port != null) 122f else 130f
        c.drawText(name, cx, baseline, text)
        if (port != null) {
            val small = Paint(text).apply {
                textSize = 17f
                typeface = Typeface.DEFAULT
                color = 0xB3FFFFFF.toInt()
            }
            c.drawText(port.uppercase(), cx, 150f, small)
        }
        return b
    }
}

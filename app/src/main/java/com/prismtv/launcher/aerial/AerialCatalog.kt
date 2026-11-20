package com.prismtv.launcher.aerial

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.hardware.display.DisplayManager
import android.view.Display
import com.prismtv.launcher.data.AerialQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** One aerial video. [url] is the preferred stream; [fallbackUrl] is the same file over HTTP. */
data class AerialVideo(val id: String, val label: String, val url: String) {
    val fallbackUrl: String get() = url.replaceFirst("https://", "http://")
}

/**
 * Apple TV aerials, read from Apple's own tvOS manifest (a tar holding `entries.json`), the same source
 * the Kodi "Aerial" screensaver and the macOS Aerial project use. The manifest is cached for a week.
 */
object AerialCatalog {
    private const val TAG = "PrismAerial"
    private const val MANIFEST = "https://sylvan.apple.com/Aerials/resources-16.tar"
    private const val CACHE = "aerial-entries.json"
    private const val MAX_AGE = 7L * 24 * 60 * 60 * 1000

    suspend fun load(ctx: Context, quality: AerialQuality): List<AerialVideo> = withContext(Dispatchers.IO) {
        val json = entriesJson(ctx) ?: return@withContext emptyList()
        runCatching { parse(json, urlKeys(ctx, quality)) }
            .onFailure { Log.w(TAG, "Couldn't parse aerial manifest", it) }
            .getOrDefault(emptyList())
    }

    private fun entriesJson(ctx: Context): String? {
        val cache = File(ctx.filesDir, CACHE)
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < MAX_AGE) {
            return runCatching { cache.readText() }.getOrNull()
        }
        val fetched = fetch(MANIFEST) ?: fetch(MANIFEST.replaceFirst("https://", "http://"))
        if (fetched != null) {
            runCatching { cache.writeText(fetched) }
            return fetched
        }
        return if (cache.exists()) runCatching { cache.readText() }.getOrNull() else null
    }

    private fun fetch(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            conn.inputStream.buffered(64 * 1024).use { readTarEntry(it, "entries.json") }
        } finally {
            conn.disconnect()
        }
    }.onFailure { Log.w(TAG, "Manifest fetch failed: $url", it) }.getOrNull()

    /** Minimal ustar reader: returns the text of the first regular file called [name]. */
    private fun readTarEntry(input: InputStream, name: String): String? {
        val header = ByteArray(512)
        while (true) {
            if (!readFully(input, header)) return null
            if (header.all { it.toInt() == 0 }) return null
            val entryName = ascii(header, 0, 100)
            val prefix = ascii(header, 345, 155)
            val size = ascii(header, 124, 12).trim().ifEmpty { "0" }.toLong(8)
            val type = header[156].toInt().toChar()
            val full = if (prefix.isNotEmpty()) "$prefix/$entryName" else entryName
            val padded = (size + 511) / 512 * 512
            if ((type == '0' || type == '\u0000') && full.substringAfterLast('/') == name) {
                val data = ByteArray(size.toInt())
                if (!readFully(input, data)) return null
                return String(data, Charsets.UTF_8)
            }
            skipFully(input, padded)
        }
    }

    private fun ascii(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end].toInt() != 0) end++
        return String(b, off, end - off, Charsets.US_ASCII)
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        val scratch = ByteArray(8192)
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
            } else {
                val n = input.read(scratch, 0, minOf(scratch.size.toLong(), left).toInt())
                if (n < 0) return
                left -= n
            }
        }
    }

    private fun parse(json: String, keys: List<String>): List<AerialVideo> {
        val assets = JSONObject(json).optJSONArray("assets") ?: return emptyList()
        val out = ArrayList<AerialVideo>(assets.length())
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val url = keys.firstNotNullOfOrNull { k -> a.optString(k).takeIf { it.startsWith("http") } } ?: continue
            val label = a.optString("accessibilityLabel").ifBlank { a.optString("title") }
            out += AerialVideo(a.optString("id", url), label, url)
        }
        return out
    }

    /** Best stream for this TV: 4K HEVC on 4K panels with a hardware HEVC decoder, else 1080p. */
    private fun urlKeys(ctx: Context, quality: AerialQuality): List<String> {
        val hevc = hasHardwareHevc()
        val uhdPanel = displayWidth(ctx) >= 3000
        val want4k = when (quality) {
            AerialQuality.UHD -> true
            AerialQuality.HD -> false
            AerialQuality.Auto -> uhdPanel && hevc
        }
        return when {
            want4k -> listOf("url-4K-SDR", "url-4K-SDR-120FPS", "url-1080-SDR", "url-1080-H264")
            hevc -> listOf("url-1080-SDR", "url-1080-H264", "url-4K-SDR")
            else -> listOf("url-1080-H264", "url-1080-SDR", "url-4K-SDR")
        }
    }

    private fun hasHardwareHevc(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder &&
                info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) } &&
                if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
                else !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
        }
    }.getOrDefault(false)

    private fun displayWidth(ctx: Context): Int = runCatching {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        if (Build.VERSION.SDK_INT >= 23) {
            display.supportedModes.maxOf { maxOf(it.physicalWidth, it.physicalHeight) }
        } else {
            val p = android.graphics.Point()
            @Suppress("DEPRECATION") display.getRealSize(p)
            maxOf(p.x, p.y)
        }
    }.getOrDefault(1920)
}

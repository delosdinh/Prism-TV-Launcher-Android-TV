package com.prismtv.launcher.aerial

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.annotation.RequiresApi
import com.prismtv.launcher.data.AerialQuality
import com.prismtv.launcher.data.AerialRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** One aerial video. [url] is the preferred stream; [fallbackUrl] is the same file over HTTP. */
data class AerialVideo(
    val id: String,
    val label: String,
    val url: String,
    val key: String = "",
    /** The lightest stream of the same shot (1080p H.264 where there is one), for grabbing a poster frame. */
    val posterUrl: String = url,
) {
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

    suspend fun load(ctx: Context, quality: AerialQuality, range: AerialRange): List<AerialVideo> = withContext(Dispatchers.IO) {
        // Play edition: Apple's aerial videos aren't offered (Google Play's IP rules); the screensaver and
        // wallpaper fall back to Prism's own scenes and your pictures.
        if (com.prismtv.launcher.BuildConfig.PLAY) return@withContext emptyList()
        val json = entriesJson(ctx) ?: return@withContext emptyList()
        val keys = urlKeys(ctx, quality, range)
        runCatching { parse(json, keys) }
            .onFailure { Log.w(TAG, "Couldn't parse aerial manifest", it) }
            .getOrDefault(emptyList())
            .also { list ->
                // Which stream this TV gets, and why (`adb logcat -s PrismAerial`).
                val picked = list.groupingBy { it.key }.eachCount()
                Log.i(TAG, "Aerials: $quality/$range, panel ${displayWidth(ctx)} px wide, hardware HEVC ${hasHardwareHevc()}, " +
                    "Dolby Vision ${dolbyVisionSupport(ctx)}; tried $keys; got $picked")
            }
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

    /** Streams cheapest to decode one frame from, best first. */
    private val PosterKeys = listOf("url-1080-H264", "url-1080-SDR", "url-4K-SDR")

    private fun parse(json: String, keys: List<String>): List<AerialVideo> {
        val assets = JSONObject(json).optJSONArray("assets") ?: return emptyList()
        val out = ArrayList<AerialVideo>(assets.length())
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val url = keys.firstNotNullOfOrNull { k -> a.optString(k).takeIf { it.startsWith("http") } } ?: continue
            val label = a.optString("accessibilityLabel").ifBlank { a.optString("title") }
            val poster = PosterKeys.firstNotNullOfOrNull { k -> a.optString(k).takeIf { it.startsWith("http") } } ?: url
            out += AerialVideo(a.optString("id", url), label, url, keys.first { k -> a.optString(k) == url }, poster)
        }
        return out
    }

    /** Best stream for this TV: 4K HEVC on 4K panels with a hardware HEVC decoder, else 1080p. */
    private fun urlKeys(ctx: Context, quality: AerialQuality, range: AerialRange): List<String> {
        val hevc = hasHardwareHevc()
        val uhdPanel = displayWidth(ctx) >= 3000
        val want4k = when (quality) {
            AerialQuality.UHD -> true
            AerialQuality.HD -> false
            AerialQuality.Auto -> uhdPanel && hevc
        }
        val sdr = when {
            want4k -> listOf("url-4K-SDR", "url-4K-SDR-120FPS", "url-1080-SDR", "url-1080-H264")
            hevc -> listOf("url-1080-SDR", "url-1080-H264", "url-4K-SDR")
            else -> listOf("url-1080-H264", "url-1080-SDR", "url-4K-SDR")
        }
        if (!useDolbyVision(ctx, range)) return sdr
        // Dolby Vision first; a clip without an HDR master still plays in SDR.
        val dv = if (want4k) listOf("url-4K-HDR", "url-1080-HDR") else listOf("url-1080-HDR", "url-4K-HDR")
        return dv + sdr
    }

    /** What this box and TV report for Dolby Vision (Apple's HDR aerials are Dolby Vision profile 5). */
    data class DolbyVisionSupport(val decoder: Boolean, val display: Boolean, val trusted: Boolean = true) {
        /** What Automatic goes by. */
        val full: Boolean get() = decoder && display && trusted
    }

    fun dolbyVisionSupport(ctx: Context): DolbyVisionSupport =
        DolbyVisionSupport(hasDolbyVisionDecoder(), displaySupportsDolbyVision(ctx), !amlogicDolbyVision())

    /**
     * Amlogic boxes (Ugoos AM9 Pro) report a Dolby Vision decoder and display, but Apple's profile 5
     * aerials came out magenta and green there through ExoPlayer. Automatic stays SDR on them; choosing
     * Dolby Vision in Settings still plays it.
     */
    private fun amlogicDolbyVision(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.name.contains("amlogic", ignoreCase = true) &&
                info.supportedTypes.any { it.equals("video/dolby-vision", ignoreCase = true) }
        }
    }.getOrDefault(false) || Build.HARDWARE.contains("amlogic", ignoreCase = true)

    /**
     * Automatic plays Dolby Vision only when both the box and the TV support it. Choosing Dolby Vision
     * still needs a Dolby Vision decoder (without one the clips can't be decoded at all), but trusts
     * the user about the TV, as some TVs and receivers under-report their HDR formats.
     */
    fun useDolbyVision(ctx: Context, range: AerialRange): Boolean = when (range) {
        AerialRange.SDR -> false
        AerialRange.Auto -> dolbyVisionSupport(ctx).full
        AerialRange.DolbyVision -> hasDolbyVisionDecoder()
    }

    private fun hasDolbyVisionDecoder(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { type ->
                type.equals("video/dolby-vision", ignoreCase = true) &&
                    runCatching {
                        val levels = info.getCapabilitiesForType(type).profileLevels
                        // Profile 5 (DvheStn); some decoders list no profiles at all.
                        levels.isEmpty() || levels.any { it.profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn }
                    }.getOrDefault(true)
            }
        }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun displaySupportsDolbyVision(ctx: Context): Boolean = runCatching {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val viaMode = if (Build.VERSION.SDK_INT >= 34) modesSupportDolbyVision(display) else false
        viaMode || display.hdrCapabilities?.supportedHdrTypes?.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) == true
    }.getOrDefault(false)

    @RequiresApi(34)
    private fun modesSupportDolbyVision(display: Display): Boolean =
        display.supportedModes.any { Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in it.supportedHdrTypes }

    private fun hasHardwareHevc(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder &&
                info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) } &&
                // Some vendor codec lists (Amlogic's among them) don't flag their decoders as hardware:
                // a vendor decoder that isn't marked software-only counts too.
                if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated || (!info.isSoftwareOnly && !info.isAlias && isVendor(info.name))
                else isVendor(info.name)
        }
    }.getOrDefault(false)

    private fun isVendor(name: String): Boolean =
        !name.startsWith("OMX.google.") && !name.startsWith("c2.android.") && !name.endsWith(".secure")

    private fun displayWidth(ctx: Context): Int = runCatching {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        display.supportedModes.maxOf { maxOf(it.physicalWidth, it.physicalHeight) }
    }.getOrDefault(1920)
}

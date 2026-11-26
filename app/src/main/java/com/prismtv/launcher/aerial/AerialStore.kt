package com.prismtv.launcher.aerial

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Settings › Screensaver › Save on This TV: the Home Screen's aerials kept on the TV as they first play, so
 * they don't download again each time round. Streamed, Home plays the same 12 shots over and over and
 * downloads each again every time (a 4K aerial is about 10 Mbit/s, 4 to 5 GB an hour, measured on the AM9).
 *
 * Saving, Home plays a set of [homeSet]'s size picked once and kept across restarts, with one shot swapped
 * for a new one a day (one download a day once the set is saved). It uses at most a quarter of the space free
 * for it (counting what's already saved): when fewer shots fit, it saves as many as do, and when fewer than
 * two do, Home streams as before. The files are in the app's cache, so Android can take them back if it runs
 * short of space; a part it took downloads again the next time that shot plays.
 *
 * The screensaver plays a saved shot from the TV when it happens to pick one ([reader]) but never saves.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object AerialStore {
    private const val TAG = "PrismAerial"
    private const val DIR = "aerials"
    private const val SET = "aerial-saved.json"
    private const val DAY = 24L * 60 * 60 * 1000
    private const val MIN_SHOTS = 2

    private var cache: SimpleCache? = null
    private var db: StandaloneDatabaseProvider? = null

    /** When the saved set next swaps a shot (from the last [homeSet] in this process). */
    @Volatile private var nextSwap = Long.MAX_VALUE

    /** What Home last saved, for Settings: (shots asked for, shots that fit). Null until Home has picked. */
    var fit by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())

    /** Saved and streamed copies of a shot are one entry: the HTTP fallback is the same file. */
    private fun keyOf(url: String): String = url.substringAfter("://")
    private val keys = CacheKeyFactory { spec -> spec.key ?: keyOf(spec.uri.toString()) }

    private fun dir(app: Context) = File(app.cacheDir, DIR)
    private fun setFile(app: Context) = File(app.filesDir, SET)

    /** The cache, opened once (a directory can only have one). Off the main thread. */
    @Synchronized private fun open(app: Context): SimpleCache {
        cache?.let { return it }
        val d = dir(app)
        val existing = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        // Only a backstop: [homeSet] keeps the set within the same quarter, and removes shots it drops.
        val cap = ((app.cacheDir.usableSpace + existing) / 4).coerceAtLeast(1L)
        val provider = db ?: StandaloneDatabaseProvider(app).also { db = it }
        return SimpleCache(d, LeastRecentlyUsedCacheEvictor(cap), provider).also { cache = it }
    }

    private fun factory(app: Context, c: SimpleCache, write: Boolean): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(c)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(app))
            .setCacheKeyFactory(keys)
            // A file that can't be read or written plays from the network instead of failing the shot.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .apply { if (!write) setCacheWriteDataSinkFactory(null) }

    /** Home's player while it plays the saved set: reads what's saved, saves what it downloads. */
    fun writer(ctx: Context): DataSource.Factory? = cache?.let { factory(ctx.applicationContext, it, write = true) }

    /** The screensaver's: plays saved shots from the TV, never saves. Null when nothing is saved. Off the main thread. */
    fun reader(ctx: Context): DataSource.Factory? {
        val app = ctx.applicationContext
        if (cache == null && !setFile(app).exists()) return null
        val c = runCatching { open(app) }.onFailure { Log.w(TAG, "Saved aerials: couldn't open", it) }.getOrNull() ?: return null
        return factory(app, c, write = false)
    }

    /** The set is a day old: Home should pick again (one shot swapped) next time it starts its aerials. */
    fun swapDue(): Boolean = System.currentTimeMillis() >= nextSwap

    private class Saved(val id: String, val url: String, val bytes: Long)

    /**
     * Home's aerials when saving [count] of them, from [all] (this TV's streams at Home's quality): the saved
     * set, topped up to [count] with shots that fit and one swapped a day. Shots no longer in it are deleted.
     * Null when fewer than two fit: Home streams instead. Off the main thread.
     */
    suspend fun homeSet(ctx: Context, all: List<AerialVideo>, count: Int): List<AerialVideo>? {
        val app = ctx.applicationContext
        if (all.isEmpty()) return null
        val c = runCatching { open(app) }.onFailure { Log.w(TAG, "Saved aerials: couldn't open", it) }.getOrNull() ?: return null
        val byUrl = all.associateBy { it.url }
        val (stored, storedAt) = read(app)
        val now = System.currentTimeMillis()
        // A shot Apple no longer lists, or one at another quality (Video Quality changed), isn't in the set.
        var items = stored.filter { it.url in byUrl }.map { s ->
            // Its real size, once the server has given it (with the first part saved), instead of the estimate.
            val len = ContentMetadata.getContentLength(c.getContentMetadata(keyOf(s.url)))
            if (len != C.LENGTH_UNSET.toLong() && len > 0) Saved(s.id, s.url, len) else s
        }
        if (items.size > count) items = items.takeLast(count)
        var swappedAt = if (storedAt in 1..now) storedAt else now
        if (items.size == count && now - swappedAt >= DAY) {
            items = items.drop(1) // the oldest
            swappedAt = now
        }
        val budget = (app.cacheDir.usableSpace + c.cacheSpace) / 4
        // Less space free than when they were saved: the oldest go first.
        while (items.isNotEmpty() && items.sumOf { it.bytes } > budget) items = items.drop(1)
        if (items.size < count) {
            val have = items.map { it.url }.toSet()
            val candidates = all.filter { it.url !in have }.shuffled().take(count - items.size + 6)
            val sizes = sizes(app, candidates)
            var used = items.sumOf { it.bytes }
            val added = ArrayList<Saved>()
            for ((v, size) in candidates.zip(sizes)) {
                if (items.size + added.size >= count) break
                if (used + size > budget) continue
                used += size
                added += Saved(v.id, v.url, size)
            }
            items = items + added
        }
        val keep = items.map { keyOf(it.url) }.toSet()
        for (k in c.keys.toList()) if (k !in keep) runCatching { c.removeResource(k) }
        val fitted = items.size
        main.post { fit = count to fitted }
        Log.i(TAG, "Saved aerials: $fitted of $count fit in ${budget / MB} MB (about ${items.sumOf { it.bytes } / MB} MB), " +
            "${c.cacheSpace / MB} MB saved so far")
        if (fitted < MIN_SHOTS) {
            clear(app)
            main.post { fit = count to fitted }
            return null
        }
        write(app, items, swappedAt)
        nextSwap = swappedAt + DAY
        return items.mapNotNull { byUrl[it.url] }.shuffled()
    }

    /** Deletes every saved aerial (Save on This TV off, or the aerials no longer on Home). Off the main thread. */
    @Synchronized fun clear(ctx: Context) {
        val app = ctx.applicationContext
        val set = setFile(app)
        val d = dir(app)
        val c = cache
        if (c == null && !set.exists() && !d.exists()) return
        set.delete()
        nextSwap = Long.MAX_VALUE
        if (c != null) {
            for (k in c.keys.toList()) runCatching { c.removeResource(k) }
        } else {
            runCatching { SimpleCache.delete(d, db ?: StandaloneDatabaseProvider(app).also { db = it }) }
                .onFailure { Log.w(TAG, "Saved aerials: couldn't delete", it) }
        }
        main.post { fit = null }
        Log.i(TAG, "Saved aerials: deleted")
    }

    // ------------------------------------------------------------------------------------ sizes

    private const val MB = 1024L * 1024

    /** Each shot's size from its server (a HEAD request each, together), or an estimate when it won't say. */
    private suspend fun sizes(app: Context, list: List<AerialVideo>): List<Long> = coroutineScope {
        val http = AerialNet.preferHttp(app)
        list.map { v ->
            async(Dispatchers.IO) {
                (if (http) head(v.fallbackUrl) else head(v.url) ?: head(v.fallbackUrl)) ?: estimate(v.key)
            }
        }.awaitAll()
    }

    private fun head(url: String): Long? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 4_000
        conn.readTimeout = 4_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode in 200..299) conn.contentLengthLong.takeIf { it > 0 } else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** On the large side, so a guess doesn't overfill the space (estimates, not measured). */
    private fun estimate(key: String): Long = when (key) {
        "url-4K-SDR-120FPS" -> 1200 * MB
        "url-4K-SDR" -> 600 * MB
        "url-1080-SDR" -> 250 * MB
        else -> 300 * MB
    }

    // ------------------------------------------------------------------------------------ the set

    private fun read(app: Context): Pair<List<Saved>, Long> = runCatching {
        val o = JSONObject(setFile(app).readText())
        val a = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until a.length()).mapNotNull { i ->
            val e = a.optJSONObject(i) ?: return@mapNotNull null
            val url = e.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            Saved(e.optString("id", url), url, e.optLong("bytes", 0L).coerceAtLeast(0L))
        }
        items to o.optLong("swappedAt", 0L)
    }.getOrDefault(emptyList<Saved>() to 0L)

    private fun write(app: Context, items: List<Saved>, swappedAt: Long) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("url", it.url).put("bytes", it.bytes)) }
        runCatching { setFile(app).writeText(JSONObject().put("swappedAt", swappedAt).put("items", a).toString()) }
            .onFailure { Log.w(TAG, "Saved aerials: couldn't write the set", it) }
    }
}

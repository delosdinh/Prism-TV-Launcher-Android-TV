package com.prismtv.launcher.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * A Home Screen widget: one row of an app's cards (Continue Watching, Up Next, a recommendation
 * channel) or a row gathered from every app ("Recommended for You").
 */
data class WidgetRow(
    /** Stable id kept in prefs: "wn:<pkg>:<type>", "ch:<pkg>:<channel name>" or "all:<kind>". */
    val key: String,
    /** Package the row comes from; empty for rows gathered from every app. */
    val pkg: String,
    val title: String,
    val items: List<HeroItem>,
    /** The app's name, filled in by the ViewModel ("All Apps" for gathered rows). */
    val appLabel: String = "",
)

/** Reads Android TV channels and Watch Next into widget rows. */
class WidgetRepository(private val context: Context) {
    private val channels = Uri.parse("content://android.media.tv/channel")
    private val preview = Uri.parse("content://android.media.tv/preview_program")
    private val watchNext = Uri.parse("content://android.media.tv/watch_next_program")

    fun load(): List<WidgetRow> {
        val names = HashMap<Long, Pair<String, String>>() // channel id -> (package, display name)
        query(channels) { c ->
            val iId = c.getColumnIndex("_id")
            val iPkg = c.getColumnIndex("package_name")
            val iName = c.getColumnIndex("display_name")
            while (c.moveToNext()) {
                if (iId < 0 || iPkg < 0) break
                val pkg = c.getString(iPkg) ?: continue
                names[c.getLong(iId)] = pkg to (if (iName >= 0) c.getString(iName) else null).orEmpty()
            }
        }

        val byChannel = LinkedHashMap<Long, MutableList<HeroItem>>()
        val channelPkg = HashMap<Long, String>()
        query(preview) { c ->
            val iCh = c.getColumnIndex("channel_id")
            val iPkg = c.getColumnIndex("package_name")
            var n = 0
            while (c.moveToNext() && n < 600) {
                n++
                val item = program(c) ?: continue
                val ch = if (iCh >= 0) c.getLong(iCh) else -1L
                val list = byChannel.getOrPut(ch) { mutableListOf() }
                if (list.size < 20) list += item
                if (iPkg >= 0) c.getString(iPkg)?.let { channelPkg[ch] = it }
            }
        }

        val byWatchNext = LinkedHashMap<Pair<String, Int>, MutableList<HeroItem>>()
        query(watchNext) { c ->
            val iPkg = c.getColumnIndex("package_name")
            val iType = c.getColumnIndex("watch_next_type")
            var n = 0
            while (c.moveToNext() && n < 300) {
                n++
                val pkg = if (iPkg >= 0) c.getString(iPkg) else null
                val item = program(c) ?: continue
                if (pkg == null) continue
                val type = if (iType >= 0) c.getInt(iType) else 0
                val list = byWatchNext.getOrPut(pkg to type) { mutableListOf() }
                if (list.size < 20) list += item
            }
        }

        val rows = ArrayList<WidgetRow>()
        byWatchNext.forEach { (k, items) ->
            val (pkg, type) = k
            rows += WidgetRow("wn:$pkg:$type", pkg, watchNextTitle(type), items)
        }
        byChannel.forEach { (id, items) ->
            val (pkg, name) = names[id] ?: ((channelPkg[id] ?: return@forEach) to "")
            val title = name.ifBlank { "Recommended" }
            rows += WidgetRow("ch:$pkg:$title", pkg, title, items)
        }

        // Rows gathered from every app, newest first, taking turns between apps.
        val continueAll = byWatchNext.filterKeys { it.second == 0 }.values.toList()
        if (continueAll.isNotEmpty()) rows.add(0, WidgetRow("all:continue", "", "Continue Watching", interleave(continueAll)))
        if (byChannel.isNotEmpty()) rows.add(minOf(1, rows.size), WidgetRow("all:recommended", "", "Recommended for You", interleave(byChannel.values.toList())))
        return rows
    }

    private fun interleave(lists: List<List<HeroItem>>): List<HeroItem> {
        val out = ArrayList<HeroItem>()
        var i = 0
        while (out.size < 20) {
            var added = false
            lists.forEach { l -> l.getOrNull(i)?.let { out += it; added = true } }
            if (!added) break
            i++
        }
        return out.take(20)
    }

    private fun program(c: android.database.Cursor): HeroItem? {
        fun s(name: String): String? {
            val i = c.getColumnIndex(name)
            return if (i >= 0) c.getString(i)?.takeIf { it.isNotBlank() } else null
        }
        val pkg = s("package_name") ?: return null
        val title = s("title") ?: return null
        val art = s("thumbnail_uri") ?: s("poster_art_uri") ?: return null
        val episode = listOfNotNull(
            s("season_display_number")?.let { "S$it" },
            s("episode_display_number")?.let { "E$it" },
        ).joinToString("")
        return HeroItem(
            key = "w:$pkg:$title:${s("intent_uri").hashCode()}",
            appId = pkg,
            appLabel = "",
            title = title,
            meta = listOfNotNull(episode.ifBlank { null }, s("episode_title"), s("genre"), s("release_date")).joinToString(" · "),
            description = s("short_description") ?: "",
            art = UriArt(art),
            intentUri = s("intent_uri"),
            titleArt = TitleLogos.refFor(s("intent_uri"), s("internal_provider_id"), s("content_id")),
        )
    }

    private fun watchNextTitle(type: Int): String = when (type) {
        1 -> "Up Next"
        2 -> "New"
        3 -> "Watchlist"
        else -> "Continue Watching"
    }

    private fun query(uri: Uri, block: (android.database.Cursor) -> Unit) {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use(block)
        } catch (_: Throwable) {
            // Not readable on this device: no rows from it.
        }
    }
}

/** App-specific links for recommendation cards. */
object DeepLinks {
    private val nuvioPlay = setOf("play", "watch", "resume", "stream", "streams")

    /**
     * The title's own details page in its app, when we know how to ask for it. Nuvio's cards resume
     * playback (finding sources); Info opens `nuvio://detail/<type>/<id>` instead.
     */
    fun details(context: Context, intentUri: String?): Intent? {
        val raw = intentUri ?: return null
        val src = runCatching { Intent.parseUri(raw, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return null
        val pkg = src.`package` ?: src.component?.packageName
        val data = src.data
        val link: Uri? = when {
            data?.scheme == "nuvio" -> {
                val host = data.host.orEmpty()
                val path = data.pathSegments
                when {
                    host in setOf("detail", "details", "meta") -> null // already a details link
                    host in nuvioPlay && path.size >= 2 -> nuvioDetail(path[0], path[1])
                    else -> null
                }
            }
            pkg?.contains("nuvio", ignoreCase = true) == true -> {
                val id = src.getStringExtra("contentId") ?: src.getStringExtra("itemId") ?: src.getStringExtra("id")
                val type = src.getStringExtra("contentType") ?: src.getStringExtra("itemType") ?: src.getStringExtra("type")
                if (id != null && type != null) nuvioDetail(type, id) else null
            }
            else -> null
        }
        link ?: return null
        val intent = Intent(Intent.ACTION_VIEW, link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg != null) intent.setPackage(pkg)
        return intent.takeIf { runCatching { it.resolveActivity(context.packageManager) != null }.getOrDefault(false) }
    }

    private fun nuvioDetail(type: String, id: String): Uri {
        // Episode ids ("tt0944947:1:3") open the series page.
        val showId = Regex("^(.+):\\d+:\\d+$").find(id)?.groupValues?.get(1) ?: id
        val kind = when (type.lowercase()) {
            "episode", "show", "tv" -> "series"
            else -> type.lowercase()
        }
        return Uri.Builder().scheme("nuvio").authority("detail").appendPath(kind).appendPath(showId).build()
    }
}

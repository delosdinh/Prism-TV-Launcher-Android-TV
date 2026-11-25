package com.prismtv.launcher.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * The TV's pictures grouped into albums (Android's folders: Camera, Download, Screenshots…), for
 * choosing what the screensaver shows. Needs the same photo access as Settings › Wallpaper.
 */
object PhotoLibrary {
    data class Album(val id: String, val name: String, val count: Int, val cover: Uri)
    data class Photo(val id: Long, val uri: Uri, val name: String, val album: String)

    private val images: Uri get() = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    /** Albums, largest first, each with its newest picture as the cover. */
    fun albums(ctx: Context): List<Album> {
        data class Acc(val name: String, var count: Int, val cover: Uri)
        val acc = LinkedHashMap<String, Acc>()
        runCatching {
            val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            ctx.contentResolver.query(images, proj, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                while (c.moveToNext()) {
                    val bucket = c.getString(1) ?: continue
                    val a = acc[bucket]
                    if (a == null) {
                        acc[bucket] = Acc(c.getString(2) ?: "Pictures", 1, ContentUris.withAppendedId(images, c.getLong(0)))
                    } else a.count++
                }
            }
        }
        return acc.map { (id, a) -> Album(id, a.name, a.count, a.cover) }.sortedByDescending { it.count }
    }

    /** The newest pictures (to pick one by one). */
    fun recent(ctx: Context, limit: Int): List<Photo> {
        val out = ArrayList<Photo>()
        runCatching {
            val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.BUCKET_ID)
            ctx.contentResolver.query(images, proj, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    out += Photo(id, ContentUris.withAppendedId(images, id), (c.getString(1) ?: "Picture").substringBeforeLast('.'), c.getString(2).orEmpty())
                }
            }
        }
        return out
    }

    /**
     * The pictures the screensaver shows: everything (newest first), or just the chosen albums
     * ("album:<id>") and pictures ("photo:<id>").
     */
    fun chosen(ctx: Context, all: Boolean, picks: Set<String>, limit: Int = 200): List<Uri> {
        val albums = picks.filter { it.startsWith("album:") }.map { it.removePrefix("album:") }
        val photos = picks.filter { it.startsWith("photo:") }.mapNotNull { it.removePrefix("photo:").toLongOrNull() }
        if (!all && albums.isEmpty() && photos.isEmpty()) return emptyList()
        val where = if (all) null else buildList {
            if (albums.isNotEmpty()) add("${MediaStore.Images.Media.BUCKET_ID} IN (${albums.joinToString(",") { "?" }})")
            if (photos.isNotEmpty()) add("${MediaStore.Images.Media._ID} IN (${photos.joinToString(",")})")
        }.joinToString(" OR ")
        val args = if (all) null else albums.toTypedArray()
        val out = ArrayList<Uri>()
        runCatching {
            ctx.contentResolver.query(images, arrayOf(MediaStore.Images.Media._ID), where, args, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                while (c.moveToNext() && out.size < limit) out += ContentUris.withAppendedId(images, c.getLong(0))
            }
        }
        return out
    }
}

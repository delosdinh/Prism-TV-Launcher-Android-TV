package com.prismtv.launcher.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup & Restore: Prism's settings, Home Screen layout (folders, order, hidden apps) and the words
 * the keyboard has learned, as one JSON file in Download › Prism TV, where a file manager can copy it
 * to another TV. Caches (artwork, weather, recent apps) aren't included; they rebuild themselves.
 */
object Backup {
    data class Entry(val uri: Uri, val name: String, val time: Long)

    private val files = listOf("prism_prefs", "prism_layout", "prism_keyboard")
    /** Per-TV bookkeeping that shouldn't travel with a backup. */
    private val skipKeys = setOf("startupBootAt", "startupAppBootAt", "skippedUpdate", "askedTvListings", "deniedTvListings", "adbAuthorized", "hiddenLauncher")
    private const val Folder = "Prism TV"
    private const val Prefix = "Prism TV Backup"

    /** Writes a new backup; returns where it went, or null when it couldn't be saved. */
    fun save(ctx: Context, version: String): String? = runCatching {
        val json = export(ctx, version).toString(2).toByteArray()
        val stamp = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(Date())
        val name = "$Prefix $stamp.json"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + Folder)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(json) } ?: return null
            "Download › $Folder"
        } else {
            val dir = File(ctx.getExternalFilesDir(null), "backups").apply { mkdirs() }
            File(dir, name).writeBytes(json)
            "Prism TV's storage"
        }
    }.getOrNull()

    /** Backups this TV can see, newest first. */
    fun list(ctx: Context): List<Entry> {
        val out = ArrayList<Entry>()
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val proj = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATE_ADDED)
                ctx.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj,
                    "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?", arrayOf("$Prefix%"),
                    "${MediaStore.Downloads.DATE_ADDED} DESC",
                )?.use { c ->
                    while (c.moveToNext() && out.size < 20) {
                        val uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
                        out += Entry(uri, c.getString(1) ?: Prefix, c.getLong(2) * 1000L)
                    }
                }
            } else {
                File(ctx.getExternalFilesDir(null), "backups").listFiles()
                    ?.filter { it.name.startsWith(Prefix) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.take(20)
                    ?.forEach { out += Entry(Uri.fromFile(it), it.name, it.lastModified()) }
            }
        }
        return out
    }

    /** Reads a backup and writes it over the current settings. False if it isn't a Prism TV backup. */
    fun restore(ctx: Context, uri: Uri): Boolean = runCatching {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return false
        val root = JSONObject(text)
        if (root.optString("app") != "Prism TV") return false
        val prefs = root.getJSONObject("prefs")
        files.forEach { file ->
            val data = prefs.optJSONObject(file) ?: return@forEach
            val edit = ctx.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear()
            data.keys().forEach { key ->
                if (key in skipKeys) return@forEach
                val e = data.getJSONObject(key)
                when (e.getString("t")) {
                    "s" -> edit.putString(key, e.getString("v"))
                    "b" -> edit.putBoolean(key, e.getBoolean("v"))
                    "i" -> edit.putInt(key, e.getInt("v"))
                    "l" -> edit.putLong(key, e.getLong("v"))
                    "f" -> edit.putFloat(key, e.getDouble("v").toFloat())
                    "ss" -> {
                        val arr = e.getJSONArray("v")
                        edit.putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                }
            }
            edit.commit()
        }
        true
    }.getOrDefault(false)

    private fun export(ctx: Context, version: String): JSONObject {
        val prefs = JSONObject()
        files.forEach { file ->
            val data = JSONObject()
            ctx.getSharedPreferences(file, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                if (key in skipKeys) return@forEach
                val e = when (value) {
                    is String -> JSONObject().put("t", "s").put("v", value)
                    is Boolean -> JSONObject().put("t", "b").put("v", value)
                    is Int -> JSONObject().put("t", "i").put("v", value)
                    is Long -> JSONObject().put("t", "l").put("v", value)
                    is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                    is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.filterIsInstance<String>()))
                    else -> null
                }
                if (e != null) data.put(key, e)
            }
            prefs.put(file, data)
        }
        return JSONObject()
            .put("app", "Prism TV")
            .put("version", version)
            .put("created", System.currentTimeMillis())
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("prefs", prefs)
    }
}

package com.prismtv.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FolderDef(val id: String, val name: String, val appIds: List<String>)

/** The user's Home Screen arrangement: top-level order (app ids and folder ids) plus folders. */
data class GridLayout(
    val order: List<String> = emptyList(),
    val folders: Map<String, FolderDef> = emptyMap(),
)

class LayoutStore(context: Context) {
    private val sp = context.getSharedPreferences("prism_layout", Context.MODE_PRIVATE)

    fun load(key: String): GridLayout? {
        val json = sp.getString(key, null) ?: return null
        return try {
            val o = JSONObject(json)
            val order = o.optJSONArray("order").strings()
            val folders = LinkedHashMap<String, FolderDef>()
            val fa = o.optJSONArray("folders") ?: JSONArray()
            for (i in 0 until fa.length()) {
                val f = fa.getJSONObject(i)
                val id = f.getString("id")
                folders[id] = FolderDef(id, f.optString("name", "Folder"), f.optJSONArray("apps").strings())
            }
            GridLayout(order, folders)
        } catch (_: Throwable) {
            null
        }
    }

    fun save(key: String, layout: GridLayout) {
        val o = JSONObject()
        o.put("order", JSONArray(layout.order))
        val fa = JSONArray()
        layout.folders.values.forEach { f ->
            fa.put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.appIds)))
        }
        o.put("folders", fa)
        sp.edit().putString(key, o.toString()).apply()
    }

    fun remove(key: String) {
        sp.edit().remove(key).apply()
    }

    fun clear() {
        sp.edit().clear().apply()
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it) }
    }
}

object GridLayouts {

    /** Applies a saved layout to the installed apps. New apps are appended; hidden apps are skipped. */
    fun build(apps: List<LauncherApp>, layout: GridLayout?, hidden: Set<String>): List<GridItem> {
        if (layout == null) return apps.filterNot { it.id in hidden }.map { AppItem(it) }
        val byId = apps.associateBy { it.id }
        val inFolders = layout.folders.values.flatMap { it.appIds }.toSet()
        val result = ArrayList<GridItem>()
        val placed = HashSet<String>()
        for (id in layout.order) {
            val folder = layout.folders[id]
            if (folder != null) {
                val folderApps = folder.appIds.mapNotNull { byId[it] }.filterNot { it.id in hidden }
                if (folderApps.isNotEmpty()) result += FolderItem(folder.id, folder.name, folderApps)
                placed += folder.appIds
                continue
            }
            val app = byId[id] ?: continue
            if (app.id in hidden || app.id in inFolders || !placed.add(app.id)) continue
            result += AppItem(app)
        }
        apps.forEach { app ->
            if (app.id !in placed && app.id !in inFolders && app.id !in hidden) result += AppItem(app)
        }
        return result
    }

    fun fromItems(items: List<GridItem>): GridLayout {
        val folders = LinkedHashMap<String, FolderDef>()
        val order = items.map { item ->
            if (item is FolderItem) folders[item.id] = FolderDef(item.id, item.label, item.apps.map { it.id })
            item.id
        }
        return GridLayout(order, folders)
    }

    fun withoutApp(items: List<GridItem>, appId: String): List<GridItem> = items.mapNotNull { item ->
        when (item) {
            is AppItem -> if (item.app.id == appId) null else item
            is FolderItem -> {
                val rest = item.apps.filterNot { it.id == appId }
                if (rest.isEmpty()) null else item.copy(apps = rest)
            }
        }
    }

    fun indexOfApp(items: List<GridItem>, appId: String): Int = items.indexOfFirst {
        when (it) {
            is AppItem -> it.app.id == appId
            is FolderItem -> it.apps.any { a -> a.id == appId }
        }
    }
}

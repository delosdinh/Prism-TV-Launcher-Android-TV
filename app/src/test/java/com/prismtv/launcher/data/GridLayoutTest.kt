package com.prismtv.launcher.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How a saved Home Screen arrangement is applied to the installed apps, and kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GridLayoutTest {
    private fun app(id: String) = LauncherApp(id = id, label = id.uppercase(), packageName = "pkg.$id", activityName = null)
    private val apps = listOf("tv", "music", "photos", "games", "news").map(::app)

    private fun ids(items: List<GridItem>): List<String> = items.map {
        when (it) {
            is AppItem -> it.app.id
            is FolderItem -> it.id + it.apps.joinToString(",", "[", "]") { a -> a.id }
        }
    }

    @Test
    fun withoutALayoutAppsKeepTheirOrderAndHiddenOnesStayOut() {
        assertEquals(listOf("tv", "photos", "games", "news"), ids(GridLayouts.build(apps, null, setOf("music"))))
    }

    @Test
    fun aSavedLayoutIsAppliedAndNewAppsGoAtTheEnd() {
        val layout = GridLayout(
            order = listOf("news", "f1", "gone", "tv"),
            folders = mapOf("f1" to FolderDef("f1", "Fun", listOf("games", "music"))),
        )
        // "gone" was uninstalled; "photos" is new; apps in the folder aren't repeated outside it.
        assertEquals(listOf("news", "f1[games,music]", "tv", "photos"), ids(GridLayouts.build(apps, layout, emptySet())))
    }

    @Test
    fun aFolderOfHiddenAppsDisappears() {
        val layout = GridLayout(listOf("f1", "tv"), mapOf("f1" to FolderDef("f1", "Fun", listOf("games"))))
        assertEquals(listOf("tv", "music", "photos", "news"), ids(GridLayouts.build(apps, layout, setOf("games"))))
    }

    @Test
    fun itemsRoundTripThroughALayout() {
        val items = listOf(AppItem(app("news")), FolderItem("f1", "Fun", listOf(app("games"), app("music"))), AppItem(app("tv")), AppItem(app("photos")))
        assertEquals(ids(items), ids(GridLayouts.build(apps, GridLayouts.fromItems(items), emptySet())))
    }

    @Test
    fun removingAnAppEmptiesAndDropsItsFolder() {
        val items = listOf(AppItem(app("tv")), FolderItem("f1", "Fun", listOf(app("games"))), FolderItem("f2", "Two", listOf(app("music"), app("news"))))
        assertEquals(listOf("f1[games]", "f2[music,news]"), ids(GridLayouts.withoutApp(items, "tv")))
        assertEquals(listOf("tv", "f2[music,news]"), ids(GridLayouts.withoutApp(items, "games")))
        assertEquals(2, GridLayouts.indexOfApp(items, "news"))
        assertEquals(-1, GridLayouts.indexOfApp(items, "nope"))
    }

    @Test
    fun theStoreKeepsALayout() {
        val store = LayoutStore(ApplicationProvider.getApplicationContext())
        val layout = GridLayout(listOf("news", "f1", "tv"), mapOf("f1" to FolderDef("f1", "Fun & Games", listOf("games", "music"))))
        store.save("main", layout)
        assertEquals(layout, store.load("main"))
        store.remove("main")
        assertNull(store.load("main"))
    }
}

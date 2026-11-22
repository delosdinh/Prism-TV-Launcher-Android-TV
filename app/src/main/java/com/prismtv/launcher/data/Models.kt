package com.prismtv.launcher.data

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector

/** Artwork used for bundled demo apps (no third-party logos). */
@Immutable
data class DemoGlyph(
    val vector: ImageVector?,
    val colors: List<Color>,
    val fg: Color = Color.White,
    val wordmark: String? = null,
    val prism: Boolean = false,
)

@Immutable
data class LauncherApp(
    val id: String,
    val label: String,
    val packageName: String,
    val activityName: String?,
    val banner: ImageBitmap? = null,
    val icon: ImageBitmap? = null,
    val demo: DemoGlyph? = null,
    /** Dominant colour – used for focus glow and fallback tiles. */
    val tint: Color = Color(0xFF2B2E36),
    val isTvApp: Boolean = true,
)

/** An entry on the Home Screen grid: an app or a folder of apps. */
sealed interface GridItem {
    val id: String
    val label: String
}

@Immutable
data class AppItem(val app: LauncherApp) : GridItem {
    override val id: String get() = app.id
    override val label: String get() = app.label
}

@Immutable
data class FolderItem(
    override val id: String,
    override val label: String,
    val apps: List<LauncherApp>,
) : GridItem

sealed interface ArtRef {
    val key: String
}

data class ResArt(@DrawableRes val resId: Int) : ArtRef {
    override val key: String get() = "res:$resId"
}

data class UriArt(val uri: String) : ArtRef {
    override val key: String get() = uri
}

/** A picture already in memory (Now Playing artwork). [key] must change when the picture does. */
class MemoryArt(override val key: String, val bitmap: android.graphics.Bitmap) : ArtRef {
    override fun equals(other: Any?): Boolean = other is MemoryArt && other.key == key
    override fun hashCode(): Int = key.hashCode()
}

/** A full-screen backdrop built from an app's own banner/icon and colour. */
data class BannerArt(val appId: String) : ArtRef {
    override val key: String get() = "brand:$appId"
}

/** A "Top Shelf" item shown in the dynamic hero. */
@Immutable
data class HeroItem(
    val key: String,
    val appId: String,
    val appLabel: String,
    val title: String,
    val meta: String = "",
    val description: String = "",
    val badges: List<String> = emptyList(),
    val art: ArtRef,
    val logo: ImageBitmap? = null,
    val intentUri: String? = null,
    /** The title's logo artwork (a clear logo), shown in place of its name when Title Logos is on. */
    val titleArt: ArtRef? = null,
    /** What another app is playing right now (Now Playing): its buttons are play/pause and next. */
    val media: Boolean = false,
    val playing: Boolean = false,
)

/** Clear title logos for cards that carry an IMDb id (Nuvio, Stremio-style apps), from Metahub. */
object TitleLogos {
    private val imdb = Regex("""(?<![A-Za-z0-9])tt\d{7,9}(?![0-9])""")

    fun refFor(vararg texts: String?): ArtRef? {
        for (t in texts) {
            val id = t?.let { imdb.find(it)?.value } ?: continue
            return UriArt("https://images.metahub.space/logo/medium/$id/img")
        }
        return null
    }
}

@Immutable
data class RecentEntry(
    val key: String,
    val app: LauncherApp?, // null = the Home Screen card
    val art: ArtRef?,
    val title: String? = null,
    /** [art] is a snapshot of the app as you left it (drawn as is, with no title over it). */
    val snapshot: Boolean = false,
)

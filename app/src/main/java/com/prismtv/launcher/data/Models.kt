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
)

@Immutable
data class RecentEntry(
    val key: String,
    val app: LauncherApp?, // null = the Home Screen card
    val art: ArtRef?,
    val title: String? = null,
)

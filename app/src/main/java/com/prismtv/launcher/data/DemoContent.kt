package com.prismtv.launcher.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.prismtv.launcher.R

/**
 * Original placeholder content so the launcher can be previewed (and screenshotted)
 * on a device or emulator with no apps installed. Toggle in Settings › Home Screen › Demo Content.
 */
object DemoContent {

    val scenes = listOf(
        R.drawable.hero_glacier,
        R.drawable.hero_aurora,
        R.drawable.hero_dunes,
        R.drawable.hero_ocean,
        R.drawable.hero_nebula,
    )

    fun sceneFor(seed: String): Int = scenes[(seed.hashCode() and 0x7fffffff) % scenes.size]

    private fun c(vararg hex: Long): List<Color> = hex.map { Color(it) }

    private fun app(
        id: String,
        label: String,
        vector: ImageVector?,
        colors: List<Color>,
        fg: Color = Color.White,
        wordmark: String? = null,
        prism: Boolean = false,
    ) = LauncherApp(
        id = "demo:$id",
        label = label,
        packageName = "demo.$id",
        activityName = null,
        demo = DemoGlyph(vector, colors, fg, wordmark, prism),
        tint = colors.last(),
    )

    private val prismTv = app("prism", "Prism TV", null, c(0xFF23252C, 0xFF0C0D11), prism = true)
    private val cinema = app("cinema", "Cinema", Icons.Rounded.Theaters, c(0xFFFF5A5F, 0xFFD62C34))
    private val music = app("music", "Music", Icons.Rounded.MusicNote, c(0xFFFF4E6A, 0xFFE0243F))
    private val arcade = app("arcade", "Arcade", Icons.Rounded.SportsEsports, c(0xFF2E3038, 0xFF15161B), fg = Color(0xFFFF5E5B))
    private val photos = app("photos", "Photos", Icons.Rounded.PhotoLibrary, c(0xFFFFFFFF, 0xFFF0F1F5), fg = Color(0xFFFF9F0A))
    private val fitness = app("fitness", "Fitness", Icons.Rounded.FitnessCenter, c(0xFF1E2024, 0xFF0B0C0E), fg = Color(0xFFA6FF3D))

    private val settings = app("settings", "Settings", Icons.Rounded.Settings, c(0xFFA3A6AE, 0xFF7C7F87))
    private val store = app("store", "Store", Icons.Rounded.Store, c(0xFF3EA0FF, 0xFF1567E0))
    private val podcasts = app("podcasts", "Podcasts", Icons.Rounded.Mic, c(0xFFB66CFF, 0xFF7B2FD6))
    private val live = app("live", "Live TV", Icons.Rounded.LiveTv, c(0xFF2F7BFF, 0xFF1B4FCF))
    private val sports = app("sports", "Sports", Icons.Rounded.SportsSoccer, c(0xFFFFFFFF, 0xFFEDEEF2), fg = Color(0xFFE5342F))
    private val kids = app("kids", "Kids", Icons.Rounded.ChildCare, c(0xFFFFD34E, 0xFFFFB020), fg = Color(0xFF6A3B00))

    private val radio = app("radio", "Radio", Icons.Rounded.Radio, c(0xFFFF7A45, 0xFFE8501E))
    private val travel = app("travel", "Travel", Icons.Rounded.FlightTakeoff, c(0xFF2DD4BF, 0xFF0E9F8E))
    private val library = app("library", "Library", Icons.Rounded.VideoLibrary, c(0xFF0B1A3A, 0xFF050B1C), fg = Color(0xFF6FB8FF))
    private val stars = app("stars", "Stars", Icons.Rounded.Star, c(0xFF5856D6, 0xFF3634A3))
    private val news = app("news", "News", Icons.Rounded.Description, c(0xFFFF453A, 0xFFC9221A))
    private val films = app("films", "Films", Icons.Rounded.Movie, c(0xFF101A2C, 0xFF060A12), fg = Color(0xFF5AC8FA))

    // Folder contents
    private val camera = app("camera", "Camera", Icons.Rounded.Videocam, c(0xFF5EE07A, 0xFF2DB84C))
    private val search = app("search", "Search", Icons.Rounded.Search, c(0xFFA3A6AE, 0xFF7C7F87))
    private val weather = app("weather", "Weather", Icons.Rounded.WbSunny, c(0xFF5AB8FF, 0xFF2B7BE4))
    private val files = app("files", "Files", Icons.Rounded.Folder, c(0xFF45A3FF, 0xFF1F6FE0))
    private val browser = app("browser", "Browser", Icons.Rounded.Public, c(0xFFFFFFFF, 0xFFEDEEF2), fg = Color(0xFF0A84FF))
    private val snapshots = app("snapshots", "Snapshots", Icons.Rounded.CameraAlt, c(0xFFFF9F0A, 0xFFE57B00))
    private val gamepad = app("gamepad", "Gamepad", Icons.Rounded.Gamepad, c(0xFFFF453A, 0xFFD12A20))

    val items: List<GridItem> = listOf(
        AppItem(prismTv), AppItem(cinema), AppItem(music), AppItem(arcade), AppItem(photos), AppItem(fitness),
        AppItem(settings), AppItem(store), AppItem(podcasts),
        FolderItem("folder:essentials", "Essentials", listOf(camera, search, weather, files, browser, snapshots)),
        AppItem(live), AppItem(sports),
        AppItem(kids), AppItem(radio), AppItem(travel), AppItem(library), AppItem(stars), AppItem(news),
        AppItem(films), AppItem(gamepad),
    )

    val allApps: List<LauncherApp> = items.flatMap {
        when (it) {
            is AppItem -> listOf(it.app)
            is FolderItem -> it.apps
        }
    }

    val defaultLayout: GridLayout = GridLayouts.fromItems(items)

    val heroes: Map<String, List<HeroItem>> = mapOf(
        prismTv.id to listOf(
            HeroItem(
                key = "demo-hero-glacier", appId = prismTv.id, appLabel = "Prism TV",
                title = "The Last Ice",
                meta = "Documentary · 2026 · 1 hr 48 min",
                badges = listOf("4K", "Dolby Vision", "Dolby Atmos"),
                description = "A year inside the fastest-changing glaciers on Earth, filmed across four seasons at the edge of the Arctic Circle.",
                art = ResArt(R.drawable.hero_glacier),
            ),
            HeroItem(
                key = "demo-hero-aurora", appId = prismTv.id, appLabel = "Prism TV",
                title = "Northern Signal",
                meta = "Sci-Fi Thriller · Season 2 · New Episode Friday",
                badges = listOf("4K", "HDR10+", "5.1"),
                description = "A remote listening station picks up a transmission from a satellite that went silent forty years ago.",
                art = ResArt(R.drawable.hero_aurora),
            ),
        ),
        cinema.id to listOf(
            HeroItem(
                key = "demo-hero-dunes", appId = cinema.id, appLabel = "Cinema",
                title = "Sand & Silence",
                meta = "Drama · 2025 · 2 hr 11 min",
                badges = listOf("4K", "Dolby Vision", "Dolby Atmos"),
                description = "Two strangers attempt a crossing of the Empty Quarter on foot, and discover what they were really running from.",
                art = ResArt(R.drawable.hero_dunes),
            ),
        ),
        music.id to listOf(
            HeroItem(
                key = "demo-hero-ocean", appId = music.id, appLabel = "Music",
                title = "Low Tide Sessions",
                meta = "Live Album · 14 Songs",
                badges = listOf("Lossless", "Spatial Audio"),
                description = "Recorded in a single evening on the coast, with the ocean as the opening act.",
                art = ResArt(R.drawable.hero_ocean),
            ),
        ),
        arcade.id to listOf(
            HeroItem(
                key = "demo-hero-nebula", appId = arcade.id, appLabel = "Arcade",
                title = "Beyond Kepler",
                meta = "Game · Exploration · 1–4 Players",
                badges = listOf("4K", "60 FPS"),
                description = "Chart unknown systems, trade with drifting colonies and find your way home through the dark.",
                art = ResArt(R.drawable.hero_nebula),
            ),
        ),
        photos.id to listOf(
            HeroItem(
                key = "demo-hero-memories", appId = photos.id, appLabel = "Photos",
                title = "Winter Nights",
                meta = "Memories · 214 Photos",
                description = "Your best moments from the north, set to music.",
                art = ResArt(R.drawable.hero_aurora),
            ),
        ),
        fitness.id to listOf(
            HeroItem(
                key = "demo-hero-fitness", appId = fitness.id, appLabel = "Fitness",
                title = "Summit Sessions",
                meta = "HIIT · 20 min · New this week",
                description = "High-altitude intervals with a view.",
                art = ResArt(R.drawable.hero_glacier),
            ),
        ),
    )
}

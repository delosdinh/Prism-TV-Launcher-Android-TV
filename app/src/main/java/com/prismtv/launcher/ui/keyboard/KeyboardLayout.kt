package com.prismtv.launcher.ui.keyboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EmojiFoodBeverage
import androidx.compose.material.icons.rounded.EmojiNature
import androidx.compose.material.icons.rounded.EmojiObjects
import androidx.compose.material.icons.rounded.EmojiPeople
import androidx.compose.material.icons.rounded.EmojiSymbols
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.graphics.vector.ImageVector

enum class KeyKind { Char, Shift, Delete, Layer, Space, Done, Emoji, Mic, EmojiCategory }

/** A key: [units] wide (10 per row); [target] is the layer (Layer) or category (EmojiCategory). */
data class Key(val label: String, val kind: KeyKind = KeyKind.Char, val units: Float = 1f, val target: Int = 0)

/** iOS-style layouts: letters, numbers, symbols and the emoji bar. Widths are in key units (10 per row). */
object KeyboardLayout {
    const val Letters = 0
    const val Numbers = 1
    const val Symbols = 2
    const val Emoji = 3

    private fun chars(s: String) = s.map { Key(it.toString()) }

    private fun bottom(switchLabel: String, switchTarget: Int) = listOf(
        Key(switchLabel, KeyKind.Layer, 1.5f, target = switchTarget),
        Key("emoji", KeyKind.Emoji, 1.25f),
        Key("mic", KeyKind.Mic, 1.25f),
        Key("space", KeyKind.Space, 4f),
        Key("return", KeyKind.Done, 2f),
    )

    private val letterRows: List<List<Key>> = listOf(
        chars("qwertyuiop"),
        chars("asdfghjkl"),
        listOf(Key("shift", KeyKind.Shift, 1.5f)) + chars("zxcvbnm") + Key("delete", KeyKind.Delete, 1.5f),
        bottom("123", Numbers),
    )
    private val numberRows: List<List<Key>> = listOf(
        chars("1234567890"),
        chars("-/:;()$&@\""),
        listOf(Key("#+=", KeyKind.Layer, 1.5f, target = Symbols)) + ".,?!'".map { Key(it.toString(), units = 1.4f) } + Key("delete", KeyKind.Delete, 1.5f),
        bottom("ABC", Letters),
    )
    private val symbolRows: List<List<Key>> = listOf(
        chars("[]{}#%^*+="),
        chars("_\\|~<>€£¥•"),
        listOf(Key("123", KeyKind.Layer, 1.5f, target = Numbers)) + ".,?!'".map { Key(it.toString(), units = 1.4f) } + Key("delete", KeyKind.Delete, 1.5f),
        bottom("ABC", Letters),
    )

    /** The emoji keyboard's bottom bar: ABC, the categories, delete. */
    val emojiBar: List<Key> = listOf(Key("ABC", KeyKind.Layer, 1f, target = Letters)) +
        EmojiData.categories.indices.map { Key("cat", KeyKind.EmojiCategory, 1f, target = it) } +
        Key("delete", KeyKind.Delete, 1f)

    fun of(layer: Int): List<List<Key>> = when (layer) {
        Numbers -> numberRows
        Symbols -> symbolRows
        Emoji -> listOf(emojiBar)
        else -> letterRows
    }

    /** Row starts in key units: the second letter row is indented half a key, like iOS. */
    fun indent(layer: Int, row: Int): Float = if (layer == Letters && row == 1) 0.5f else 0f

    /** Horizontal centre (in units) of [col] in [row]. */
    fun centre(layer: Int, row: Int, col: Int): Float {
        val keys = of(layer)[row]
        var x = indent(layer, row)
        for (i in 0 until col.coerceAtMost(keys.size - 1)) x += keys[i].units
        return x + keys[col.coerceIn(0, keys.size - 1)].units / 2f
    }

    /** The key in [row] nearest to [x] units. */
    fun nearest(layer: Int, row: Int, x: Float): Int {
        val keys = of(layer)[row]
        return keys.indices.minByOrNull { kotlin.math.abs(centre(layer, row, it) - x) } ?: 0
    }
}

/** Emoji in the categories iOS uses (Unicode 11 and earlier, so they draw on every Android TV). */
object EmojiData {
    class Category(val name: String, val icon: ImageVector, val emoji: List<String>)

    private fun list(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    val categories: List<Category> = listOf(
        Category("Frequently Used", Icons.Rounded.Schedule, emptyList()),
        Category("Smileys", Icons.Rounded.EmojiEmotions, list("""
            😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩
            🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔
            🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👻
            💀 👽 🤖 💩 😺 😸 😹 😻 😼 😽 🙀 😿 😾
        """)),
        Category("People", Icons.Rounded.EmojiPeople, list("""
            👋 🤚 ✋ 🖖 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 💪 👀 👂
            👃 🧠 👅 👄 💋 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🙇 🤦 🤷 👮 💂 👷 🤴
            👸 👳 👲 🧕 🤵 👰 🤰 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 🧝 🧞 🧟 💆 💇 🚶 🏃 💃 🕺 👯 🧖 🧘 👪
        """)),
        Category("Animals & Nature", Icons.Rounded.EmojiNature, list("""
            🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄
            🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦏 🦛
            🐪 🦒 🦘 🐄 🐎 🐖 🐑 🦙 🐐 🦌 🐕 🐈 🐓 🦃 🦚 🦜 🦢 🐇 🦝 🦔 🌵 🎄 🌲 🌳 🌴 🌱 🌿 🍀 🍁 🍄
            🌷 🌹 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌚 🌕 🌙 🌎 ⭐ 🌟 ✨ ⚡ 🔥 🌈 ☀️ ⛅ ☁️ ❄️ ⛄ 💧 🌊
        """)),
        Category("Food & Drink", Icons.Rounded.EmojiFoodBeverage, list("""
            🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌽 🥕 🥔 🥐 🥯 🍞 🥖 🥨
            🧀 🥚 🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🥙 🌮 🌯 🥗 🥘 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍥 🥠
            🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥜 🍯 🥛 ☕ 🍵 🥤 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾
        """)),
        Category("Activity & Travel", Icons.Rounded.EmojiEvents, list("""
            ⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🏒 🏏 ⛳ 🏹 🎣 🥊 🥋 🛹 ⛸ 🎿 🏂 🏄 🏊 🚴 🏆 🥇 🥈 🥉
            🏅 🎫 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 🧩 🎯 🎳 🎮 🚗 🚕 🚙 🚌 🏎 🚓 🚑 🚒 🚚 🚜
            🛴 🚲 🛵 🏍 🚨 ✈️ 🚀 🛸 🚁 ⛵ 🚤 🚢 ⚓ 🗽 🗼 🏰 🏯 🎡 🎢 🎠 ⛲ 🏖 🏝 🌋 🏔 🗻 🏕 🏠 🏡 🌃
        """)),
        Category("Objects", Icons.Rounded.EmojiObjects, list("""
            ⌚ 📱 💻 ⌨️ 🖥 🖨 🕹 💾 💿 📷 📸 📹 🎥 📞 📺 📻 🎙 ⏰ ⏳ 📡 🔋 🔌 💡 🔦 🕯 💸 💵 💰 💳 💎
            🔧 🔨 🛠 🔩 ⚙️ 🧲 🧨 🔪 🛡 🔮 💈 🔭 🔬 💊 💉 🧬 🧪 🌡 🧹 🧺 🧻 🚿 🛁 🧼 🔑 🚪 🛋 🛏 🧸 🖼
            🛍 🛒 🎁 🎈 🎀 🎊 🎉 ✉️ 📩 📦 📮 📜 📄 📊 📈 📉 📅 📋 📁 📰 📓 📚 📖 🔖 🔗 📎 📌 📍 ✂️ 📝
        """)),
        Category("Symbols", Icons.Rounded.EmojiSymbols, list("""
            ❤️ 🧡 💛 💚 💙 💜 🖤 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ☮️ ☯️ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓
            🆔 ⚛️ 🆚 💯 💢 ❌ ⭕ 🛑 ⛔ 🚫 ❗ ❓ ‼️ ⁉️ ⚠️ ♻️ ✅ ❎ 🌐 💤 🆗 🆙 🆒 🆕 🆓 🔟 ▶️ ⏸ ⏹ ⏺
            ⏭ ⏮ ⏩ ⏪ 🔀 🔁 🔂 🔄 🎵 🎶 ➕ ➖ ➗ ✖️ 💲 ™️ ©️ ®️ ✔️ ☑️ 🔘 🔴 🔵 ⚪ ⚫ 🔺 🔻 🔸 🔹 🔶
        """)),
    )
}

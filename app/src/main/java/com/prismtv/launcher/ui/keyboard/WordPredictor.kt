package com.prismtv.launcher.ui.keyboard

import android.content.Context
import com.prismtv.launcher.R
import kotlin.math.ln

/**
 * Suggestions and auto-correction for Prism Keyboard, from a bundled list of 56,000 English words in
 * frequency order (see tools/keyboard). On a TV the usual slip is landing on the key next to the one you
 * meant, so neighbouring-key mistakes are the cheapest corrections.
 */
class WordPredictor private constructor(private val context: Context) {
    /** Lower-case word -> frequency rank (0 = most common). */
    private val rank = HashMap<String, Int>(80_000)
    /** Known words that are never suggested. */
    private val quiet = HashSet<String>()
    /** Every suggestible word, alphabetical, for prefix look-ups. */
    private lateinit var sorted: Array<String>
    private val userWords = HashSet<String>()
    private val sp = context.getSharedPreferences("prism_keyboard", Context.MODE_PRIVATE)

    private fun load() {
        context.resources.openRawResource(R.raw.en_words).bufferedReader().useLines { lines ->
            var i = 0
            lines.forEach { raw ->
                if (raw.isBlank()) return@forEach
                val q = raw.startsWith("!")
                val w = if (q) raw.substring(1) else raw
                if (!rank.containsKey(w)) rank[w] = i
                if (q) quiet += w
                i++
            }
        }
        sorted = rank.keys.filter { it !in quiet }.sorted().toTypedArray()
        userWords += sp.getStringSet("userWords", emptySet()).orEmpty()
    }

    fun isWord(word: String): Boolean {
        val w = word.lowercase()
        return rank.containsKey(w) || w in userWords || w.all { it.isDigit() }
    }

    /** Remembers a word the user kept as typed, so it's never corrected again. */
    fun learn(word: String) {
        val w = word.lowercase()
        if (w.length < 2 || isWord(w) || w.length > 30) return
        userWords += w
        sp.edit().putStringSet("userWords", HashSet(userWords)).apply()
    }

    private fun score(w: String): Double = ln((rank[w] ?: 200_000) + 10.0)

    /** Most common words starting with [prefix] (not the prefix itself). */
    fun complete(prefix: String, n: Int): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        var lo = sorted.binarySearch(p).let { if (it < 0) -it - 1 else it }
        val found = ArrayList<String>()
        while (lo < sorted.size && sorted[lo].startsWith(p)) {
            if (sorted[lo] != p) found += sorted[lo]
            lo++
        }
        userWords.filter { it.startsWith(p) && it != p }.forEach { found += it }
        return found.sortedBy { rank[it] ?: 150_000 }.take(n)
    }

    /**
     * Likely intended words for [typed], best first: one slip away (a neighbouring key, two keys swapped,
     * a doubled or a missed letter), weighted by how common each candidate is.
     */
    fun corrections(typed: String, n: Int): List<String> {
        val w = typed.lowercase()
        if (w.length < 2) return emptyList()
        Contractions[w]?.let { return listOf(it) }
        val best = HashMap<String, Double>()
        fun offer(c: String, cost: Double) {
            if (c == w || !rank.containsKey(c) || c in quiet) return
            val s = score(c) + cost
            if (s < (best[c] ?: Double.MAX_VALUE)) best[c] = s
        }
        for (i in 0..w.length) {
            val a = w.substring(0, i)
            val b = w.substring(i)
            if (b.isNotEmpty()) {
                // An extra letter (often a doubled press).
                offer(a + b.substring(1), if (i > 0 && w[i - 1] == b[0]) 0.7 else 1.4)
                for (ch in 'a'..'z') {
                    if (ch == b[0]) continue
                    offer(a + ch + b.substring(1), if (neighbours(b[0], ch)) 0.6 else 2.2)
                }
            }
            if (b.length > 1) offer(a + b[1] + b[0] + b.substring(2), 1.0)
            // A missed letter.
            for (ch in 'a'..'z') offer(a + ch + b, 1.3)
        }
        return best.entries.sortedBy { it.value }.take(n).map { it.key }
    }

    /**
     * The correction auto-correct would make for [typed], or null. Only for words it doesn't know, and
     * only when the fix is clearly likely (a common word one slip away).
     */
    fun autoCorrection(typed: String): String? {
        val w = typed.lowercase()
        if (w == "i") return "I"
        if (w.length < 2 || isWord(w) || w.any { !it.isLetter() && it != '\'' }) return null
        Contractions[w]?.let { return it }
        val c = corrections(w, 1).firstOrNull() ?: return null
        return c.takeIf { (rank[it] ?: Int.MAX_VALUE) < 30_000 }
    }

    companion object {
        @Volatile private var instance: WordPredictor? = null

        /** Loads the word list the first time (about 100 ms); call off the main thread. */
        fun get(context: Context): WordPredictor =
            instance ?: synchronized(this) {
                instance ?: WordPredictor(context.applicationContext).also { it.load(); instance = it }
            }

        fun peek(): WordPredictor? = instance

        /** Apostrophe-less contractions, as iOS fixes them. */
        private val Contractions = mapOf(
            "im" to "I'm", "ive" to "I've", "id" to "I'd", "ill" to "I'll",
            "dont" to "don't", "cant" to "can't", "wont" to "won't", "didnt" to "didn't", "doesnt" to "doesn't",
            "isnt" to "isn't", "wasnt" to "wasn't", "arent" to "aren't", "werent" to "weren't", "havent" to "haven't",
            "hasnt" to "hasn't", "hadnt" to "hadn't", "wouldnt" to "wouldn't", "couldnt" to "couldn't",
            "shouldnt" to "shouldn't", "thats" to "that's", "whats" to "what's", "youre" to "you're",
            "theyre" to "they're", "youve" to "you've", "youll" to "you'll", "theyve" to "they've",
            "theyll" to "they'll", "theres" to "there's", "heres" to "here's", "wheres" to "where's",
            "hes" to "he's", "shes" to "she's", "weve" to "we've", "aint" to "ain't", "yall" to "y'all",
        ).filterKeys { it != "id" && it != "ill" }

        private val Rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

        /** Keys touching each other on the keyboard (same row, or the row above / below). */
        fun neighbours(a: Char, b: Char): Boolean {
            val ra = Rows.indexOfFirst { a in it }
            val rb = Rows.indexOfFirst { b in it }
            if (ra < 0 || rb < 0 || kotlin.math.abs(ra - rb) > 1) return false
            // Row starts are offset by about half a key each row down.
            val xa = Rows[ra].indexOf(a) + ra * 0.5f
            val xb = Rows[rb].indexOf(b) + rb * 0.5f
            return kotlin.math.abs(xa - xb) <= 1.01f
        }
    }
}

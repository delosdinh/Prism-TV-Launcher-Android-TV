package com.prismtv.launcher.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every switch in Settings is saved and read back (a new one missing from save() or load() fails here). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsStoreTest {
    private val cls = LauncherPrefs::class.java
    private val defaults = LauncherPrefs()
    private val components = cls.methods.filter { it.name.matches(Regex("component\\d+")) }
        .sortedBy { it.name.removePrefix("component").toInt() }
    private val names = cls.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }

    @Test
    fun everySwitchSurvivesASaveAndLoad() {
        val values = components.map { it.invoke(defaults) }.toTypedArray()
        val flipped = values.map { if (it is Boolean) !it else it }.toTypedArray()
        val ctor = cls.constructors.first { it.parameterCount == values.size }
        val changed = ctor.newInstance(*flipped) as LauncherPrefs
        val store = PrefsStore(ApplicationProvider.getApplicationContext())
        store.save(changed)
        val back = store.load()
        components.forEachIndexed { i, m ->
            if (values[i] is Boolean) assertEquals(names.getOrElse(i) { m.name }, m.invoke(changed), m.invoke(back))
        }
    }

    @Test
    fun defaultsSurviveASaveAndLoad() {
        val store = PrefsStore(ApplicationProvider.getApplicationContext())
        store.save(defaults)
        assertEquals(defaults, store.load())
    }
}

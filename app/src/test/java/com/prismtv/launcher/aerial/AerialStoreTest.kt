package com.prismtv.launcher.aerial

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Save on This TV: Home keeps the same set across starts, shrinks it to a smaller choice, and Off deletes it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AerialStoreTest {
    // Hosts that don't exist: each size comes from the estimate (about 250 MB for a 1080p shot).
    private fun video(i: Int) = AerialVideo("id$i", "Shot $i", "https://aerials.invalid/v$i.mov", "url-1080-SDR")

    @Test
    fun keepsTheSetShrinksItAndDeletesItWhenOff() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val all = (1..20).map(::video)

        val first = AerialStore.homeSet(ctx, all, 6)
        // Six estimated shots need about 6 GB free here (a quarter of it is the most they may use).
        assumeTrue("not enough free space on this machine", first != null)
        val ids = first!!.map { it.id }.toSet()
        assertEquals(6, ids.size)
        assertTrue(all.map { it.id }.containsAll(ids))

        // The next start plays the same shots (in another order), not a new pick.
        assertEquals(ids, AerialStore.homeSet(ctx, all, 6)!!.map { it.id }.toSet())

        // A smaller choice keeps shots it already has.
        val fewer = AerialStore.homeSet(ctx, all, 3)!!.map { it.id }.toSet()
        assertEquals(3, fewer.size)
        assertTrue(ids.containsAll(fewer))

        // Off: nothing kept.
        AerialStore.clear(ctx)
        assertFalse(File(ctx.filesDir, "aerial-saved.json").exists())
    }
}

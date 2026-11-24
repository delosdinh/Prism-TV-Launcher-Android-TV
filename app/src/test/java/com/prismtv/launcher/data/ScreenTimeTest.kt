package com.prismtv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ScreenTimeTest {
    @Test
    fun labels() {
        assertEquals("Off", ScreenTime.limitLabel(0))
        assertEquals("45 Min", ScreenTime.limitLabel(45))
        assertEquals("1 Hr", ScreenTime.limitLabel(60))
        assertEquals("1 Hr 30 Min", ScreenTime.limitLabel(90))
        assertEquals("3 Hrs", ScreenTime.limitLabel(180))
        assertEquals("Off", ScreenTime.downtimeLabel(""))
        assertEquals("9 PM – 7 AM", ScreenTime.downtimeLabel("21:00-07:00"))
    }

    private fun at(hour: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 24, hour, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun downtimeEndsAtTheNextMorning() {
        val p = ProfileSpec(id = "p", name = "Kids", avatar = "preset:1", downtime = "21:00-07:00")
        val ends = Calendar.getInstance()
        ends.timeInMillis = ScreenTime.downtimeEnds(p, at(22))
        assertEquals(25, ends.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, ends.get(Calendar.HOUR_OF_DAY))
        ends.timeInMillis = ScreenTime.downtimeEnds(p, at(6))
        assertEquals(24, ends.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, ends.get(Calendar.HOUR_OF_DAY))
    }
}

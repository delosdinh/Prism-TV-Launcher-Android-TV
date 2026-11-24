package com.prismtv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The time pill's weather: WMO codes to glyphs, and the temperature as shown. */
class WeatherTest {
    private fun w(tempC: Double, code: Int = 0) = Weather(tempC, code, isDay = true, place = "Brisbane")

    @Test
    fun codesMapToConditions() {
        assertEquals(WeatherKind.Clear, w(20.0, 0).kind())
        assertEquals(WeatherKind.PartlyCloudy, w(20.0, 2).kind())
        assertEquals(WeatherKind.Fog, w(20.0, 45).kind())
        assertEquals(WeatherKind.Drizzle, w(20.0, 53).kind())
        assertEquals(WeatherKind.Rain, w(20.0, 63).kind())
        assertEquals(WeatherKind.Rain, w(20.0, 81).kind())
        assertEquals(WeatherKind.Snow, w(20.0, 75).kind())
        assertEquals(WeatherKind.Storm, w(20.0, 96).kind())
        // Anything unknown reads as cloudy rather than failing.
        assertEquals(WeatherKind.Cloudy, w(20.0, 999).kind())
    }

    @Test
    fun temperaturesRoundAndConvert() {
        assertEquals("24°", w(23.6).label(TemperatureUnit.Celsius))
        assertEquals("0°", w(-0.4).label(TemperatureUnit.Celsius))
        assertEquals("68°", w(20.0).label(TemperatureUnit.Fahrenheit))
        assertEquals("-4°", w(-20.0).label(TemperatureUnit.Fahrenheit))
    }

    @Test
    fun automaticFollowsTheTvsRegion() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertTrue(useFahrenheit(TemperatureUnit.Auto))
            Locale.setDefault(Locale.forLanguageTag("en-AU"))
            assertFalse(useFahrenheit(TemperatureUnit.Auto))
        } finally {
            Locale.setDefault(before)
        }
    }
}

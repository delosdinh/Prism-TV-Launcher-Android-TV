package com.prismtv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Open-Meteo's answer as the screensaver shows it: conditions in words and today's high and low. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherForecastTest {
    private val answer = """
        {"current":{"time":"2026-11-27T14:30","temperature_2m":23.6,"weather_code":2,"is_day":1},
         "daily":{"time":["2026-11-27"],"temperature_2m_max":[27.4],"temperature_2m_min":[18.2]}}
    """.trimIndent()

    @Test
    fun readsTheCurrentConditionsAndToday() {
        val w = parseOpenMeteo(answer, "Brisbane")
        assertEquals(23.6, w.tempC, 0.001)
        assertEquals(WeatherKind.PartlyCloudy, w.kind())
        assertEquals("Partly Cloudy", w.conditions())
        assertEquals("H 27°  L 18°", w.highLow(TemperatureUnit.Celsius))
        assertEquals("H 81°  L 65°", w.highLow(TemperatureUnit.Fahrenheit))
        assertEquals("Brisbane", w.place)
    }

    @Test
    fun anAnswerWithoutTodayStillGivesTheWeather() {
        val w = parseOpenMeteo("""{"current":{"temperature_2m":-3.0,"weather_code":73,"is_day":0}}""", "")
        assertEquals("Snow", w.conditions())
        assertEquals("-3°", w.label(TemperatureUnit.Celsius))
        assertNull(w.highLow(TemperatureUnit.Celsius))
    }

    @Test
    fun conditionsReadNaturally() {
        fun w(code: Int, day: Boolean = true) = Weather(20.0, code, day, "")
        assertEquals("Sunny", w(0).conditions())
        assertEquals("Clear", w(0, day = false).conditions())
        assertEquals("Showers", w(81).conditions())
        assertEquals("Rain", w(63).conditions())
        assertEquals("Thunderstorms", w(95).conditions())
    }
}

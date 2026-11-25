package com.prismtv.launcher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Current conditions for the Home Screen pill. [code] is a WMO weather code. */
data class Weather(val tempC: Double, val code: Int, val isDay: Boolean, val place: String)

enum class WeatherKind { Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Snow, Storm }

fun Weather.kind(): WeatherKind = when (code) {
    0 -> WeatherKind.Clear
    1, 2 -> WeatherKind.PartlyCloudy
    3 -> WeatherKind.Cloudy
    45, 48 -> WeatherKind.Fog
    in 51..57 -> WeatherKind.Drizzle
    in 61..67, in 80..82 -> WeatherKind.Rain
    in 71..77, 85, 86 -> WeatherKind.Snow
    in 95..99 -> WeatherKind.Storm
    else -> WeatherKind.Cloudy
}

/** Fahrenheit where it's the everyday unit; Celsius everywhere else. */
fun useFahrenheit(unit: TemperatureUnit): Boolean = when (unit) {
    TemperatureUnit.Celsius -> false
    TemperatureUnit.Fahrenheit -> true
    TemperatureUnit.Auto -> Locale.getDefault().country.uppercase() in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
}

fun Weather.label(unit: TemperatureUnit): String {
    val t = if (useFahrenheit(unit)) tempC * 9.0 / 5.0 + 32.0 else tempC
    return "${Math.round(t)}°"
}

/**
 * Android TV has no weather service apps can read, so Prism TV asks Open-Meteo (free, no account)
 * for the current conditions. The location comes from the TV's internet connection (approximate,
 * city level), looked up once a day; the weather is refreshed every 30 minutes.
 */
class WeatherRepository(context: Context) {
    private val sp = context.getSharedPreferences("prism_weather", Context.MODE_PRIVATE)

    fun cached(): Weather? {
        if (!sp.contains("tempC")) return null
        return Weather(
            sp.getFloat("tempC", 0f).toDouble(),
            sp.getInt("code", 0),
            sp.getBoolean("isDay", true),
            sp.getString("place", "") ?: "",
        )
    }

    val fetchedAt: Long get() = sp.getLong("fetchedAt", 0L)

    suspend fun refresh(): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val loc = location() ?: return@runCatching null
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.lat}&longitude=${loc.lon}" +
                "&current=temperature_2m,weather_code,is_day&timezone=auto"
            val cur = JSONObject(get(url)).getJSONObject("current")
            val w = Weather(
                cur.getDouble("temperature_2m"),
                cur.getInt("weather_code"),
                cur.optInt("is_day", 1) == 1,
                loc.city,
            )
            sp.edit()
                .putFloat("tempC", w.tempC.toFloat())
                .putInt("code", w.code)
                .putBoolean("isDay", w.isDay)
                .putString("place", w.place)
                .putLong("fetchedAt", System.currentTimeMillis())
                .apply()
            w
        }.onFailure { Log.w("PrismWeather", "Weather refresh failed", it) }.getOrNull()
    }

    private data class Loc(val lat: Double, val lon: Double, val city: String)

    private fun location(): Loc? {
        val age = System.currentTimeMillis() - sp.getLong("locAt", 0L)
        if (sp.contains("lat") && age < 24L * 60 * 60 * 1000) {
            return Loc(sp.getFloat("lat", 0f).toDouble(), sp.getFloat("lon", 0f).toDouble(), sp.getString("city", "") ?: "")
        }
        val fresh = runCatching {
            val j = JSONObject(get("https://ipapi.co/json/"))
            Loc(j.getDouble("latitude"), j.getDouble("longitude"), j.optString("city"))
        }.recoverCatching {
            val j = JSONObject(get("https://ipwho.is/"))
            Loc(j.getDouble("latitude"), j.getDouble("longitude"), j.optString("city"))
        }.getOrNull()
        if (fresh != null) {
            sp.edit()
                .putFloat("lat", fresh.lat.toFloat())
                .putFloat("lon", fresh.lon.toFloat())
                .putString("city", fresh.city)
                .putLong("locAt", System.currentTimeMillis())
                .apply()
            return fresh
        }
        // Offline: keep using the last known place.
        return if (sp.contains("lat")) {
            Loc(sp.getFloat("lat", 0f).toDouble(), sp.getFloat("lon", 0f).toDouble(), sp.getString("city", "") ?: "")
        } else null
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "PrismTV")
        return try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

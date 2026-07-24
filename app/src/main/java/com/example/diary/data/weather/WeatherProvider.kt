package com.example.diary.data.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.LocalDate

/**
 * Open-Meteo weather lookup, code -> emoji.
 *
 * Two endpoints, picked by date:
 *  - **forecast** (`api.open-meteo.com/v1/forecast`): today through ~5 days back.
 *    Authoritative for "right now" and the near past.
 *  - **archive** (`archive-api.open-meteo.com/v1/archive`): 5+ days back. The
 *    archive endpoint won't return data for dates inside its cutoff window, so
 *    we always bias to forecast when the date is recent enough.
 *  - Future dates: not supported by either endpoint — return null.
 *
 * Pure I/O — call from a coroutine; internally switches to [Dispatchers.IO].
 */
object WeatherProvider {

    private const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    private const val ARCHIVE_ENDPOINT = "https://archive-api.open-meteo.com/v1/archive"

    // Open-Meteo's archive lags by ~5 days; ask forecast for anything inside that window.
    private const val ARCHIVE_CUTOFF_DAYS = 5

    /**
     * Returns a weather emoji like "☀️" or "🌧", or null when unavailable.
     */
    suspend fun fetchEmoji(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        clock: Clock = Clock.systemUTC()
    ): String? = withContext(Dispatchers.IO) {
        val today = LocalDate.now(clock)
        // Future dates are out of scope for both endpoints.
        if (date.isAfter(today)) return@withContext null
        val endpoint = if (date.isBefore(today.minusDays(ARCHIVE_CUTOFF_DAYS.toLong()))) {
            ARCHIVE_ENDPOINT
        } else {
            FORECAST_ENDPOINT
        }
        var conn: HttpURLConnection? = null
        try {
            val url = URL(
                "$endpoint?latitude=$latitude&longitude=$longitude" +
                    "&start_date=$date&end_date=$date&daily=weather_code&timezone=auto"
            )
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val code = JSONObject(body)
                .getJSONObject("daily")
                .getJSONArray("weather_code")
                .opt(0) as? Int ?: return@withContext null
            codeToEmoji(code)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * WMO weather interpretation codes (subset used by Open-Meteo).
     * https://open-meteo.com/en/docs
     */
    private fun codeToEmoji(code: Int): String? = when (code) {
        0 -> "☀️"           // Clear
        1, 2 -> "🌤"        // Mainly clear / partly cloudy
        3 -> "☁️"          // Overcast
        45, 48 -> "🌫"      // Fog
        51, 53, 55, 56, 57 -> "🌦"  // Drizzle
        61, 63, 65, 66, 67 -> "🌧"  // Rain
        71, 73, 75, 77 -> "🌨"      // Snow
        80, 81, 82 -> "🌧"          // Rain showers
        85, 86 -> "🌨"              // Snow showers
        95, 96, 99 -> "⛈"          // Thunderstorm
        else -> null
    }
}

package com.example.yesmin.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WeatherReport(
    val locationLabel: String,
    val temperatureC: Double,
    val windSpeedKmh: Double,
    val conditionDescription: String
)

class WeatherClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeather(cityQuery: String): Result<WeatherReport> = withContext(Dispatchers.IO) {
        try {
            val query = cityQuery.trim().ifBlank { "Dhaka" }
            val encoded = URLEncoder.encode(query, "UTF-8")
            val geoReq = Request.Builder()
                .url("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json")
                .get()
                .build()

            var lat = 23.8103
            var lon = 90.4125
            var resolvedName = query

            client.newCall(geoReq).execute().use { geoRes ->
                if (geoRes.isSuccessful) {
                    val geoJson = JSONObject(geoRes.body?.string().orEmpty())
                    val results = geoJson.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val first = results.getJSONObject(0)
                        lat = first.getDouble("latitude")
                        lon = first.getDouble("longitude")
                        val name = first.optString("name", query)
                        val country = first.optString("country", "")
                        resolvedName = if (country.isNotBlank()) "$name, $country" else name
                    }
                }
            }

            val weatherReq = Request.Builder()
                .url("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
                .get()
                .build()

            client.newCall(weatherReq).execute().use { res ->
                if (!res.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Weather service error HTTP ${res.code}"))
                }
                val json = JSONObject(res.body?.string().orEmpty())
                val current = json.optJSONObject("current_weather")
                    ?: return@withContext Result.failure(IllegalStateException("Weather data unavailable"))
                val temp = current.optDouble("temperature")
                val wind = current.optDouble("windspeed")
                val code = current.optInt("weathercode", 0)
                Result.success(
                    WeatherReport(
                        locationLabel = resolvedName,
                        temperatureC = temp,
                        windSpeedKmh = wind,
                        conditionDescription = mapWmoCode(code)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapWmoCode(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rainy"
        71, 73, 75 -> "Snowfall"
        80, 81, 82 -> "Rain showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Variable conditions"
    }
}

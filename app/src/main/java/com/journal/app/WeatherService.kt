package com.journal.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WeatherService {

    companion object {
        private const val GEO_URL = "https://geocoding-api.open-meteo.com/v1/search"
        private const val WEATHER_URL = "https://api.open-meteo.com/v1/forecast"

        private val WMO_CODES = mapOf(
            0 to "Clear sky", 1 to "Mainly clear", 2 to "Partly cloudy", 3 to "Overcast",
            45 to "Foggy", 48 to "Depositing rime fog",
            51 to "Light drizzle", 53 to "Moderate drizzle", 55 to "Dense drizzle",
            56 to "Light freezing drizzle", 57 to "Dense freezing drizzle",
            61 to "Slight rain", 63 to "Moderate rain", 65 to "Heavy rain",
            66 to "Light freezing rain", 67 to "Heavy freezing rain",
            71 to "Slight snowfall", 73 to "Moderate snowfall", 75 to "Heavy snowfall",
            77 to "Snow grains",
            80 to "Slight rain showers", 81 to "Moderate rain showers", 82 to "Violent rain showers",
            85 to "Slight snow showers", 86 to "Heavy snow showers",
            95 to "Thunderstorm", 96 to "Thunderstorm with slight hail", 99 to "Thunderstorm with heavy hail"
        )

        private val WMO_ICONS = mapOf(
            0 to "sunny", 1 to "mostly_sunny", 2 to "partly_cloudy", 3 to "cloudy",
            45 to "fog", 48 to "fog",
            51 to "drizzle", 53 to "drizzle", 55 to "drizzle",
            56 to "freezing_drizzle", 57 to "freezing_drizzle",
            61 to "rain", 63 to "rain", 65 to "heavy_rain",
            66 to "freezing_rain", 67 to "freezing_rain",
            71 to "snow", 73 to "snow", 75 to "heavy_snow",
            77 to "snow", 80 to "showers", 81 to "showers", 82 to "showers",
            85 to "snow_showers", 86 to "snow_showers",
            95 to "thunderstorm", 96 to "thunderstorm", 99 to "thunderstorm"
        )
    }

    fun searchCity(query: String): String {
        if (query.isBlank() || query.trim().length < 2) return "[]"
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$GEO_URL?name=$encoded&count=5&language=en&format=json"
        val json = httpGet(url)
        val data = JSONObject(json)
        val results = data.optJSONArray("results") ?: return "[]"

        val arr = JSONArray()
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val obj = JSONObject()
            obj.put("name", r.optString("name", ""))
            obj.put("country", r.optString("country", ""))
            obj.put("admin1", r.optString("admin1", ""))
            obj.put("lat", r.optDouble("latitude"))
            obj.put("lng", r.optDouble("longitude"))
            arr.put(obj)
        }
        return arr.toString()
    }

    fun fetchCurrent(lat: Double, lng: Double, tempUnit: String): String {
        val unit = if (tempUnit == "fahrenheit") "fahrenheit" else "celsius"
        val url = "$WEATHER_URL?latitude=$lat&longitude=$lng" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&temperature_unit=$unit&wind_speed_unit=mph"
        val json = httpGet(url)
        val data = JSONObject(json)
        val c = data.getJSONObject("current")
        val code = c.getInt("weather_code")

        val result = JSONObject()
        result.put("temp", c.getDouble("temperature_2m"))
        result.put("feelsLike", c.getDouble("apparent_temperature"))
        result.put("humidity", c.getDouble("relative_humidity_2m"))
        result.put("windSpeed", c.getDouble("wind_speed_10m"))
        result.put("description", WMO_CODES[code] ?: "Unknown")
        result.put("icon", WMO_ICONS[code] ?: "unknown")
        result.put("code", code)
        result.put("unit", if (unit == "fahrenheit") "F" else "C")
        return result.toString()
    }

    private fun httpGet(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        try {
            if (conn.responseCode != 200) {
                throw RuntimeException("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}

package com.ridetrack.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HourWeather(val hour: Int, val temp: Float, val rainChance: Int, val code: Int)

data class WeatherInfo(
    val temp: Float,
    val windKmh: Float,
    val code: Int,
    // Mulai dari jam sekarang, maksimal 24 jam ke depan.
    val hours: List<HourWeather>,
    val lat: Double,
    val lon: Double,
    val fetchedAt: Long
)

// Data cuaca dari Open-Meteo.com (gratis, tanpa API key). Hasil terakhir disimpan untuk mode offline.
object WeatherStore {
    var info by mutableStateOf<WeatherInfo?>(null)
    var failed by mutableStateOf(false)
    var loading by mutableStateOf(false)

    private fun cacheFile(context: Context) = File(context.filesDir, "weather.json")

    fun loadCache(context: Context) {
        if (info != null) return
        runCatching {
            val wrapper = JSONObject(cacheFile(context).readText())
            info = parse(
                wrapper.getString("data"),
                wrapper.getDouble("lat"),
                wrapper.getDouble("lon"),
                wrapper.getLong("fetchedAt")
            )
        }
    }

    suspend fun refresh(context: Context, lat: Double, lon: Double) {
        loading = true
        val raw = withContext(Dispatchers.IO) { runCatching { download(lat, lon) }.getOrNull() }
        val parsed = raw?.let {
            runCatching { parse(it, lat, lon, System.currentTimeMillis()) }.getOrNull()
        }
        if (raw != null && parsed != null) {
            info = parsed
            failed = false
            withContext(Dispatchers.IO) {
                runCatching {
                    val wrapper = JSONObject()
                        .put("data", raw)
                        .put("lat", lat)
                        .put("lon", lon)
                        .put("fetchedAt", parsed.fetchedAt)
                    cacheFile(context).writeText(wrapper.toString())
                }
            }
        } else {
            failed = true
        }
        loading = false
    }

    private fun download(lat: Double, lon: Double): String {
        val address = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,precipitation_probability,weather_code" +
                "&forecast_days=2&timezone=auto&wind_speed_unit=kmh",
            lat, lon
        )
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(raw: String, lat: Double, lon: Double, fetchedAt: Long): WeatherInfo {
        val json = JSONObject(raw)
        val current = json.getJSONObject("current")
        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val chances = hourly.getJSONArray("precipitation_probability")
        val codes = hourly.getJSONArray("weather_code")

        // Contoh waktu: "2026-11-26T16:00". Cari jam yang sama dengan sekarang.
        val nowHour = current.getString("time").substring(0, 13)
        val start = (0 until times.length()).firstOrNull { times.getString(it) >= nowHour } ?: 0
        val end = minOf(start + 24, times.length())

        val hours = (start until end).map {
            HourWeather(
                hour = times.getString(it).substring(11, 13).toInt(),
                temp = temps.optDouble(it, 0.0).toFloat(),
                rainChance = chances.optInt(it, 0),
                code = codes.optInt(it, 0)
            )
        }
        return WeatherInfo(
            temp = current.getDouble("temperature_2m").toFloat(),
            windKmh = current.getDouble("wind_speed_10m").toFloat(),
            code = current.getInt("weather_code"),
            hours = hours,
            lat = lat,
            lon = lon,
            fetchedAt = fetchedAt
        )
    }
}

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

// Posisi untuk cuaca: titik terakhir saat merekam, lalu lokasi terakhir HP, lalu lokasi cuaca tersimpan.
@SuppressLint("MissingPermission")
fun weatherLocation(context: Context): Pair<Double, Double>? {
    val live = RideState.live
    if (live.active) {
        live.ride.points.lastOrNull()?.let { return it.lat to it.lon }
    }
    if (hasLocationPermission(context)) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = runCatching { manager.getProviders(true) }.getOrNull().orEmpty()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (best != null) return best.latitude to best.longitude
    }
    return WeatherStore.info?.let { it.lat to it.lon }
}

private fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67 -> "🌧️"
    in 71..77, 85, 86 -> "❄️"
    in 80..82 -> "🌦️"
    in 95..99 -> "⛈️"
    else -> "🌡️"
}

private fun weatherText(code: Int): String = when (code) {
    0 -> "Cerah"
    1, 2 -> "Cerah berawan"
    3 -> "Berawan"
    45, 48 -> "Berkabut"
    in 51..57 -> "Gerimis"
    in 61..67 -> "Hujan"
    in 71..77, 85, 86 -> "Salju"
    in 80..82 -> "Hujan sesaat"
    in 95..99 -> "Badai petir"
    else -> "Cuaca"
}

private const val RAIN_THRESHOLD = 50

// Ringkasan 12 jam ke depan, misalnya "Hujan diperkirakan: 06.00–08.00".
private fun rainSummary(hours: List<HourWeather>): String {
    val rainy = hours.take(12).filter { it.rainChance >= RAIN_THRESHOLD }
    if (rainy.isEmpty()) return "Tidak ada hujan diperkirakan 12 jam ke depan"

    val windows = mutableListOf<String>()
    var start = rainy.first().hour
    var previous = start
    for (item in rainy.drop(1)) {
        if (item.hour == (previous + 1) % 24) {
            previous = item.hour
        } else {
            windows += window(start, previous)
            start = item.hour
            previous = item.hour
        }
    }
    windows += window(start, previous)
    return "Hujan diperkirakan: " + windows.joinToString(", ")
}

private fun window(start: Int, end: Int): String =
    if (start == end) "%02d.00".format(start) else "%02d.00–%02d.00".format(start, end)

@Composable
fun WeatherCard(modifier: Modifier = Modifier) {
    val info = WeatherStore.info
    val uriHandler = LocalUriHandler.current
    val rainColor = Color(0xFF4FC3F7)

    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1C), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text("CUACA", color = Color.Gray, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))

        if (info == null) {
            Text(
                if (WeatherStore.failed) {
                    "Cuaca belum tersedia. Cek internet dan izin lokasi."
                } else {
                    "Memuat cuaca..."
                },
                color = Color.Gray,
                fontSize = 14.sp
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weatherEmoji(info.code), fontSize = 36.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "%.0f°C".format(info.temp),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(weatherText(info.code), color = Color.Gray, fontSize = 13.sp)
                }
                Text("💨 %.0f km/j".format(info.windKmh), color = Color.White, fontSize = 14.sp)
            }

            Spacer(Modifier.height(12.dp))
            val summary = rainSummary(info.hours)
            Text(
                summary,
                color = if (summary.startsWith("Hujan")) rainColor else Color(0xFF81C784),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                info.hours.forEach { hour ->
                    Column(
                        Modifier.width(54.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("%02d.00".format(hour.hour), color = Color.Gray, fontSize = 11.sp)
                        Text(weatherEmoji(hour.code), fontSize = 20.sp)
                        Text(
                            "%.0f°".format(hour.temp),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            "${hour.rainChance}%",
                            color = if (hour.rainChance >= RAIN_THRESHOLD) rainColor else Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = if (hour.rainChance >= RAIN_THRESHOLD) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                }
            }

            // Data lebih dari 30 menit dianggap lama (misalnya saat offline).
            val ageMinutes = (System.currentTimeMillis() - info.fetchedAt) / 60_000
            if (ageMinutes > 30) {
                Spacer(Modifier.height(8.dp))
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(info.fetchedAt))
                Text("Data terakhir pukul $time", color = Color(0xFFFFB74D), fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Weather data by Open-Meteo.com",
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.clickable { uriHandler.openUri("https://open-meteo.com/") }
        )
    }
}

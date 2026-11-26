package com.ridetrack.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class RoutePoint(val lat: Double, val lon: Double)

data class Ride(
    val startedAt: Long = 0L,
    val seconds: Long = 0L,
    val movingSeconds: Long = 0L,
    val distance: Float = 0f,
    val maxSpeed: Float = 0f,
    val elevationGain: Float = 0f,
    val maxElevation: Float = 0f,
    val points: List<RoutePoint> = emptyList()
) {
    // Rata-rata hanya dihitung dari waktu saat bergerak (berhenti tidak ikut).
    val avgSpeed: Float
        get() = if (movingSeconds > 0) distance / movingSeconds * 3.6f else 0f
}

data class LiveRide(
    val active: Boolean = false,
    val speed: Float = 0f,
    val status: String = "GPS SIAP",
    val ride: Ride = Ride()
)

// Data perjalanan yang sedang berjalan, dipakai bersama oleh layanan dan tampilan.
object RideState {
    var live by mutableStateOf(LiveRide())
}

fun formatDuration(seconds: Long): String = String.format(
    Locale.US, "%02d:%02d:%02d",
    seconds / 3600, seconds / 60 % 60, seconds % 60
)

fun formatNumber(value: Float, digits: Int = 1): String =
    String.format(Locale.US, "%.${digits}f", value)

object RideStore {
    private fun folder(context: Context) =
        File(context.filesDir, "rides").apply { mkdirs() }

    private fun draft(context: Context) = File(context.filesDir, "draft.json")

    private fun write(file: File, ride: Ride) {
        val points = JSONArray()
        ride.points.forEach { points.put(JSONArray().put(it.lat).put(it.lon)) }
        val json = JSONObject()
            .put("startedAt", ride.startedAt)
            .put("seconds", ride.seconds)
            .put("movingSeconds", ride.movingSeconds)
            .put("distance", ride.distance.toDouble())
            .put("maxSpeed", ride.maxSpeed.toDouble())
            .put("elevationGain", ride.elevationGain.toDouble())
            .put("maxElevation", ride.maxElevation.toDouble())
            .put("points", points)

        // Tulis ke file sementara dulu agar data tidak rusak bila aplikasi terhenti.
        val temp = File(file.path + ".tmp")
        temp.writeText(json.toString())
        check(temp.renameTo(file)) { "Gagal menyimpan" }
    }

    private fun read(file: File): Ride? = runCatching {
        val json = JSONObject(file.readText())
        val array = json.getJSONArray("points")
        Ride(
            startedAt = json.getLong("startedAt"),
            seconds = json.getLong("seconds"),
            // Perjalanan lama belum punya waktu gerak, pakai total waktu sebagai gantinya.
            movingSeconds = json.optLong("movingSeconds", json.getLong("seconds")),
            distance = json.getDouble("distance").toFloat(),
            maxSpeed = json.getDouble("maxSpeed").toFloat(),
            // Perjalanan lama belum punya data elevasi, jadi dibaca sebagai 0.
            elevationGain = json.optDouble("elevationGain", 0.0).toFloat(),
            maxElevation = json.optDouble("maxElevation", 0.0).toFloat(),
            points = List(array.length()) {
                val point = array.getJSONArray(it)
                RoutePoint(point.getDouble(0), point.getDouble(1))
            }
        )
    }.getOrNull()

    // Perjalanan yang terlalu singkat dan tanpa jarak tidak disimpan.
    private fun worthSaving(ride: Ride) = ride.distance >= 10f || ride.seconds >= 60

    fun saveDraft(context: Context, ride: Ride) {
        runCatching { write(draft(context), ride) }
    }

    fun finish(context: Context, ride: Ride): Boolean {
        val saved = worthSaving(ride) && runCatching {
            write(File(folder(context), "${ride.startedAt}.json"), ride)
        }.isSuccess
        if (saved || !worthSaving(ride)) draft(context).delete()
        return saved
    }

    // Menyelamatkan perjalanan bila aplikasi sebelumnya dimatikan sistem.
    fun recover(context: Context) {
        val file = draft(context)
        if (file.exists()) read(file)?.let { finish(context, it) }
    }

    fun list(context: Context): List<Ride> =
        folder(context).listFiles { file -> file.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { read(it) }
            .sortedByDescending { it.startedAt }

    fun delete(context: Context, ride: Ride) {
        File(folder(context), "${ride.startedAt}.json").delete()
    }
}

package com.ridetrack.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

// Kartu ringkasan perjalanan (gambar PNG) untuk dibagikan, mirip kartu Strava.
// Rute digambar langsung dari data GPS, jadi tidak butuh internet.
object ShareCard {
    private const val W = 1080
    private const val H = 1400
    private val ORANGE = Color.rgb(255, 107, 0)
    private val GRAY = Color.rgb(150, 150, 150)

    fun render(ride: Ride): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(16, 16, 16))

        val text = Paint(Paint.ANTI_ALIAS_FLAG)

        // Judul dan tanggal
        text.color = ORANGE
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 46f
        text.letterSpacing = 0.15f
        canvas.drawText("RIDETRACK", 80f, 120f, text)

        text.color = GRAY
        text.typeface = Typeface.DEFAULT
        text.textSize = 34f
        text.letterSpacing = 0f
        val date = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("id", "ID"))
            .format(Date(ride.startedAt))
        canvas.drawText(date, 80f, 175f, text)

        // Rute
        drawRoute(canvas, ride.points, RectF(80f, 240f, W - 80f, 840f))

        // Jarak (angka besar)
        text.color = GRAY
        text.textSize = 30f
        text.letterSpacing = 0.1f
        canvas.drawText("JARAK", 80f, 905f, text)

        val distance = formatNumber(ride.distance / 1000f, 2)
        text.color = Color.WHITE
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 150f
        text.letterSpacing = 0f
        canvas.drawText(distance, 80f, 1030f, text)
        val distanceWidth = text.measureText(distance)

        text.color = ORANGE
        text.textSize = 56f
        canvas.drawText("km", 80f + distanceWidth + 16f, 1030f, text)

        // Statistik lain
        val moving = if (ride.movingSeconds > 0) ride.movingSeconds else ride.seconds
        val columns = floatArrayOf(80f, 400f, 720f)
        stat(canvas, text, columns[0], 1130f, "WAKTU GERAK", formatDuration(moving))
        stat(canvas, text, columns[1], 1130f, "RATA-RATA KM/J", formatNumber(ride.avgSpeed))
        stat(canvas, text, columns[2], 1130f, "TERCEPAT KM/J", formatNumber(ride.maxSpeed))
        stat(canvas, text, columns[0], 1260f, "NAIK (M)", formatNumber(ride.elevationGain, 0))
        stat(canvas, text, columns[1], 1260f, "ELEVASI MAKS (M)", formatNumber(ride.maxElevation, 0))
        stat(canvas, text, columns[2], 1260f, "WAKTU TOTAL", formatDuration(ride.seconds))

        return bitmap
    }

    private fun stat(canvas: Canvas, text: Paint, x: Float, y: Float, label: String, value: String) {
        text.color = GRAY
        text.typeface = Typeface.DEFAULT
        text.textSize = 26f
        text.letterSpacing = 0.08f
        canvas.drawText(label, x, y, text)

        text.color = Color.WHITE
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 54f
        text.letterSpacing = 0f
        canvas.drawText(value, x, y + 66f, text)
    }

    private fun drawRoute(canvas: Canvas, points: List<RoutePoint>, area: RectF) {
        if (points.isEmpty()) return

        // Titik terlalu banyak tidak perlu semuanya digambar.
        val step = max(1, points.size / 1500)
        val pts = points.filterIndexed { index, _ -> index % step == 0 || index == points.lastIndex }

        val minLat = pts.minOf { it.lat }
        val maxLat = pts.maxOf { it.lat }
        val minLon = pts.minOf { it.lon }
        val maxLon = pts.maxOf { it.lon }

        // Bujur dikoreksi agar bentuk rute tidak melebar.
        val lonScale = cos(Math.toRadians((minLat + maxLat) / 2))
        val w = (maxLon - minLon) * lonScale
        val h = maxLat - minLat
        val scale = min(area.width() / max(w, 1e-9), area.height() / max(h, 1e-9))

        val originX = area.left + (area.width() - w * scale) / 2
        val originY = area.top + (area.height() - h * scale) / 2
        fun px(p: RoutePoint) = (originX + (p.lon - minLon) * lonScale * scale).toFloat()
        fun py(p: RoutePoint) = (originY + (maxLat - p.lat) * scale).toFloat()

        val path = Path()
        pts.forEachIndexed { index, p ->
            if (index == 0) path.moveTo(px(p), py(p)) else path.lineTo(px(p), py(p))
        }

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        // Lapisan cahaya lembut di belakang garis utama
        line.color = Color.argb(70, 255, 107, 0)
        line.strokeWidth = 28f
        canvas.drawPath(path, line)
        line.color = ORANGE
        line.strokeWidth = 12f
        canvas.drawPath(path, line)

        // Titik awal (hijau) dan akhir (merah)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val first = pts.first()
        val last = pts.last()
        dot.color = Color.WHITE
        canvas.drawCircle(px(first), py(first), 22f, dot)
        canvas.drawCircle(px(last), py(last), 22f, dot)
        dot.color = Color.rgb(76, 175, 80)
        canvas.drawCircle(px(first), py(first), 16f, dot)
        dot.color = Color.rgb(244, 67, 54)
        canvas.drawCircle(px(last), py(last), 16f, dot)
    }

    // Menyimpan ke galeri (Pictures/RideTrack) lalu membuka menu bagikan.
    // Mengembalikan false bila gagal (atau Android di bawah 10).
    suspend fun share(context: Context, ride: Ride): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false

        val uri = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = render(ride)
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "RideTrack_${ride.startedAt}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/RideTrack"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Gagal membuat file")
                try {
                    resolver.openOutputStream(target)!!.use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(target, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(target, null, null)
                    throw e
                }
                target
            }.getOrNull()
        } ?: return false

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Bagikan perjalanan")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return true
    }
}

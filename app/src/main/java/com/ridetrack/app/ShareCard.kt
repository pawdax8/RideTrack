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

// Tiga model kartu bagikan. Latar belakangnya transparan (PNG) supaya bisa ditempel di foto.
enum class CardStyle(val label: String, val width: Int, val height: Int) {
    ROUTE("RUTE", 1080, 1350),
    STRIP("RINGKAS", 1080, 560),
    STATS("STATISTIK", 1080, 1350)
}

object ShareCard {
    private val ORANGE = Color.rgb(255, 107, 0)
    private val YELLOW = Color.rgb(255, 214, 0)
    private val WHITE = Color.WHITE
    private val SOFT = Color.argb(215, 255, 255, 255)

    // scale < 1 dipakai untuk gambar pratinjau yang lebih kecil.
    fun render(ride: Ride, style: CardStyle, scale: Float = 1f): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (style.width * scale).toInt(),
            (style.height * scale).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        when (style) {
            CardStyle.ROUTE -> drawRouteCard(canvas, ride)
            CardStyle.STRIP -> drawStripCard(canvas, ride)
            CardStyle.STATS -> drawStatsCard(canvas, ride)
        }
        return bitmap
    }

    private fun date(ride: Ride): String =
        SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("id", "ID")).format(Date(ride.startedAt))

    private fun movingSeconds(ride: Ride) =
        if (ride.movingSeconds > 0) ride.movingSeconds else ride.seconds

    // Model 1: rute besar, jarak besar, enam statistik di bawah.
    private fun drawRouteCard(canvas: Canvas, ride: Ride) {
        label(canvas, "RIDETRACK", 80f, 110f, 44f, ORANGE, bold = true, spacing = 0.15f)
        label(canvas, date(ride), 80f, 162f, 32f, SOFT)

        drawRoute(canvas, ride.points, RectF(80f, 220f, 1000f, 860f), ORANGE)

        label(canvas, "JARAK", 80f, 925f, 30f, SOFT, spacing = 0.1f)
        val distance = formatNumber(ride.distance / 1000f, 2)
        val width = label(canvas, distance, 80f, 1050f, 160f, WHITE, bold = true)
        label(canvas, "km", 80f + width + 16f, 1050f, 56f, ORANGE, bold = true)

        val x = floatArrayOf(80f, 400f, 720f)
        stat(canvas, x[0], 1130f, "WAKTU GERAK", formatDuration(movingSeconds(ride)), 26f, 54f)
        stat(canvas, x[1], 1130f, "RATA-RATA KM/J", formatNumber(ride.avgSpeed), 26f, 54f)
        stat(canvas, x[2], 1130f, "TERCEPAT KM/J", formatNumber(ride.maxSpeed), 26f, 54f)
        stat(canvas, x[0], 1245f, "NAIK (M)", formatNumber(ride.elevationGain, 0), 26f, 54f)
        stat(canvas, x[1], 1245f, "ELEVASI MAKS (M)", formatNumber(ride.maxElevation, 0), 26f, 54f)
        stat(canvas, x[2], 1245f, "WAKTU TOTAL", formatDuration(ride.seconds), 26f, 54f)
    }

    // Model 2: strip pendek, cocok ditaruh di bagian bawah foto.
    private fun drawStripCard(canvas: Canvas, ride: Ride) {
        drawRoute(canvas, ride.points, RectF(40f, 50f, 470f, 510f), WHITE)

        label(canvas, "RIDETRACK", 540f, 80f, 30f, ORANGE, bold = true, spacing = 0.15f)
        label(canvas, date(ride), 540f, 120f, 26f, SOFT)

        val distance = formatNumber(ride.distance / 1000f, 2)
        val width = label(canvas, distance, 540f, 290f, 150f, WHITE, bold = true)
        label(canvas, "km", 540f + width + 14f, 290f, 52f, ORANGE, bold = true)

        stat(canvas, 540f, 380f, "WAKTU", formatDuration(movingSeconds(ride)), 20f, 38f)
        stat(canvas, 740f, 380f, "RATA-RATA KM/J", formatNumber(ride.avgSpeed), 20f, 38f)
        stat(canvas, 920f, 380f, "NAIK (M)", formatNumber(ride.elevationGain, 0), 20f, 38f)
    }

    // Model 3: daftar statistik di kiri, rute di kanan.
    private fun drawStatsCard(canvas: Canvas, ride: Ride) {
        val items = listOf(
            "JARAK (KM)" to formatNumber(ride.distance / 1000f, 2),
            "WAKTU GERAK" to formatDuration(movingSeconds(ride)),
            "RATA-RATA (KM/J)" to formatNumber(ride.avgSpeed),
            "TERCEPAT (KM/J)" to formatNumber(ride.maxSpeed),
            "KENAIKAN (M)" to formatNumber(ride.elevationGain, 0),
            "ELEVASI MAKS (M)" to formatNumber(ride.maxElevation, 0)
        )
        items.forEachIndexed { index, (name, value) ->
            stat(canvas, 80f, 130f + index * 190f, name, value, 28f, 84f)
        }

        drawRoute(canvas, ride.points, RectF(560f, 200f, 1000f, 1100f), YELLOW)

        label(canvas, "RIDETRACK", 80f, 1290f, 40f, ORANGE, bold = true, spacing = 0.15f)
        label(canvas, date(ride), 560f, 1290f, 28f, SOFT)
    }

    private fun stat(
        canvas: Canvas, x: Float, y: Float, name: String, value: String,
        nameSize: Float, valueSize: Float
    ) {
        label(canvas, name, x, y, nameSize, SOFT, spacing = 0.08f)
        label(canvas, value, x, y + valueSize * 1.2f, valueSize, WHITE, bold = true)
    }

    // Teks putih dengan bayangan tipis agar tetap terbaca di atas foto terang. Mengembalikan lebar teks.
    private fun label(
        canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int,
        bold: Boolean = false, spacing: Float = 0f
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textSize = size
            letterSpacing = spacing
            setShadowLayer(8f, 0f, 3f, Color.argb(170, 0, 0, 0))
        }
        canvas.drawText(text, x, y, paint)
        return paint.measureText(text)
    }

    private fun drawRoute(canvas: Canvas, points: List<RoutePoint>, area: RectF, accent: Int) {
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
        // Garis gelap tipis di belakang agar rute tetap jelas di foto terang maupun gelap.
        line.color = Color.argb(110, 0, 0, 0)
        line.strokeWidth = 24f
        canvas.drawPath(path, line)
        line.color = accent
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
    suspend fun share(context: Context, ride: Ride, style: CardStyle): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false

        val uri = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = render(ride, style)
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        "RideTrack_${ride.startedAt}_${style.name.lowercase()}.png"
                    )
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

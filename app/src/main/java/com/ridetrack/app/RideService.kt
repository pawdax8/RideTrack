package com.ridetrack.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Layanan latar depan: perekaman tetap berjalan saat layar mati.
class RideService : Service() {
    private var tracker: GpsTracker? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var startElapsed = 0L
    private var anchor: Location? = null
    private var lastFix = 0L
    private var pausedTotal = 0L
    private var pauseStart = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (paused) {
                handler.postDelayed(this, 1000)
                return
            }
            val now = SystemClock.elapsedRealtime()
            val seconds = (now - startElapsed - pausedTotal) / 1000
            val live = RideState.live
            // Kecepatan dinolkan bila sudah lama tidak ada sinyal.
            val stale = lastFix > 0 && now - lastFix > 5_000
            RideState.live = live.copy(
                speed = if (stale) 0f else live.speed,
                status = if (stale) "SINYAL GPS LEMAH" else live.status,
                ride = live.ride.copy(seconds = seconds)
            )
            if (seconds % 15 == 0L) {
                RideStore.saveDraft(this@RideService, RideState.live.ride)
                notificationManager().notify(NOTIFICATION_ID, buildNotification())
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            else -> {
                end()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        if (tracker != null) return

        notificationManager().createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Perekaman perjalanan",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        startElapsed = SystemClock.elapsedRealtime()
        pausedTotal = 0L
        pauseStart = 0L
        paused = false
        anchor = null
        lastFix = 0L
        RideState.live = LiveRide(
            active = true,
            status = "MENCARI SATELIT GPS",
            ride = Ride(startedAt = System.currentTimeMillis())
        )

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            tracker = GpsTracker(this) { onLocation(it) }.also { it.start() }
        } catch (e: SecurityException) {
            RideState.live = LiveRide(status = "IZIN GPS DITOLAK")
            stopSelf()
            return
        }

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideTrack:ride")
            .apply { acquire(12 * 60 * 60 * 1000L) }

        handler.postDelayed(ticker, 1000)
    }

    private fun pause() {
        if (tracker == null || paused) return
        paused = true
        pauseStart = SystemClock.elapsedRealtime()
        RideState.live = RideState.live.copy(speed = 0f, status = "DIJEDA")
        RideStore.saveDraft(this, RideState.live.ride)
        notificationManager().notify(NOTIFICATION_ID, buildNotification())
    }

    private fun resume() {
        if (tracker == null || !paused) return
        pausedTotal += SystemClock.elapsedRealtime() - pauseStart
        // Titik pembanding direset agar jarak selama jeda tidak ikut dihitung.
        anchor = null
        lastFix = 0L
        paused = false
        RideState.live = RideState.live.copy(status = "MENCARI SATELIT GPS")
        notificationManager().notify(NOTIFICATION_ID, buildNotification())
    }

    private fun onLocation(location: Location) {
        val live = RideState.live
        if (!live.active || paused) return

        // Abaikan titik yang tidak akurat.
        if (location.hasAccuracy() && location.accuracy > 25f) {
            RideState.live = live.copy(status = "SINYAL GPS LEMAH")
            return
        }
        lastFix = SystemClock.elapsedRealtime()

        val metersPerSecond = if (location.hasSpeed()) location.speed else 0f
        var ride = live.ride
        val previous = anchor

        if (previous == null) {
            anchor = location
            ride = ride.copy(points = ride.points + location.toPoint())
        } else {
            val moved = previous.distanceTo(location)
            val elapsed =
                (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1e9
            val moving = !location.hasSpeed() || metersPerSecond > 0.5f

            if (elapsed > 0 && moved >= 3f && moving) {
                anchor = location
                // Lompatan yang mustahil untuk sepeda (di atas 120 km/jam) tidak dihitung.
                if (moved / elapsed <= MAX_METERS_PER_SECOND) {
                    ride = ride.copy(
                        distance = ride.distance + moved,
                        points = ride.points + location.toPoint()
                    )
                }
            }
        }

        ride = ride.copy(maxSpeed = maxOf(ride.maxSpeed, metersPerSecond * 3.6f))

        RideState.live = live.copy(
            speed = metersPerSecond,
            status = "MEREKAM",
            ride = ride
        )
    }

    private fun end() {
        handler.removeCallbacks(ticker)
        tracker?.stop()
        tracker = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        paused = false

        // Simpan rute ke riwayat (perjalanan yang terlalu singkat dilewati oleh RideStore).
        val saved = RideStore.finish(this, RideState.live.ride)
        RideState.live = RideState.live.copy(
            active = false,
            speed = 0f,
            status = if (saved) "TERSIMPAN" else "TIDAK DISIMPAN (TERLALU SINGKAT)"
        )
        stopForeground(true)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val title = if (paused) "RideTrack dijeda" else "RideTrack merekam perjalanan"
        return builder
            .setContentTitle(title)
            .setContentText("Jarak %.2f km".format(RideState.live.ride.distance / 1000f))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    // Sesuaikan jika kelas titik rute kamu bernama lain.
    private fun Location.toPoint() = RoutePoint(latitude, longitude)

    companion object {
        const val ACTION_START = "com.ridetrack.app.ACTION_START"
        const val ACTION_STOP = "com.ridetrack.app.ACTION_STOP"
        const val ACTION_PAUSE = "com.ridetrack.app.ACTION_PAUSE"
        const val ACTION_RESUME = "com.ridetrack.app.ACTION_RESUME"
        private const val CHANNEL_ID = "ridetrack_ride"
        private const val NOTIFICATION_ID = 1001
        // 120 km/jam dalam meter per detik.
        private const val MAX_METERS_PER_SECOND = 120.0 / 3.6

        // Status jeda, bisa dibaca langsung oleh layar (Compose).
        var paused by mutableStateOf(false)
            private set

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, RideService::class.java).setAction(action))
        }

        fun start(context: Context) {
            val intent = Intent(context, RideService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)
    }
}

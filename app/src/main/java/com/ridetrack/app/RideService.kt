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

// Layanan latar depan: perekaman tetap berjalan saat layar mati.
class RideService : Service() {
    private var tracker: GpsTracker? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var startElapsed = 0L
    private var anchor: Location? = null
    private var lastFix = 0L

    private val ticker = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val seconds = (now - startElapsed) / 1000
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
        if (intent?.action == ACTION_START) {
            begin()
        } else {
            end()
            stopSelf()
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

    private fun onLocation(location: Location) {
        val live = RideState.live
        if (!live.active) return

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
                        distance = ride.distance +

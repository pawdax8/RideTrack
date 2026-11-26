
package com.ridetrack.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*

class GpsTracker(
    context: Context,
    private val onUpdate: (Location) -> Unit
) {
    private val client =
        LocationServices.getFusedLocationProviderClient(context)

    private val request = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1000L
    )
        .setMinUpdateIntervalMillis(1000L)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // Proses semua titik, karena saat layar mati titik bisa datang berkelompok.
            result.locations.forEach(onUpdate)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        client.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )
    }

    fun stop() {
        client.removeLocationUpdates(callback)
    }
}

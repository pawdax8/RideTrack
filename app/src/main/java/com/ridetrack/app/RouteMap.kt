package com.ridetrack.app

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File

// follow = true: peta mengikuti posisi terakhir. false: seluruh rute, bisa digeser.
@SuppressLint("ClickableViewAccessibility")
@Composable
fun RouteMap(points: List<RoutePoint>, follow: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val map = remember {
        Configuration.getInstance().apply {
            setUserAgentValue(context.packageName)
            setOsmdroidBasePath(File(context.cacheDir, "osmdroid"))
            setOsmdroidTileCache(File(context.cacheDir, "osmdroid/tiles"))
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setMultiTouchControls(!follow)
            if (follow) setOnTouchListener { _, _ -> true }
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(-2.5, 118.0))
        }
    }
    val line = remember {
        Polyline(map).apply {
            outlinePaint.color = android.graphics.Color.rgb(255, 107, 0)
            outlinePaint.strokeWidth = 12f
            map.overlays.add(this)
        }
    }

    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause() }
    }

    LaunchedEffect(points) {
        val route = points.map { GeoPoint(it.lat, it.lon) }
        line.setPoints(route)
        if (route.isNotEmpty()) {
            if (follow || route.size < 2) {
                map.controller.setZoom(17.0)

package com.ridetrack.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Dashboard()
        }
    }
}

@Composable
fun Dashboard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val orange = Color(0xFFFF6B00)
    val dark = Color(0xFF101010)

    var riding by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(0f) }
    var distance by remember { mutableFloatStateOf(0f) }
    var seconds by remember { mutableIntStateOf(0) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    var gpsStatus by remember { mutableStateOf("GPS SIAP") }

    val tracker = remember {
        GpsTracker(context) { location ->
            lastLocation?.let { previous ->
                val moved = previous.distanceTo(location)
                if (moved in 1f..100f) {
                    distance += moved
                }
            }
            lastLocation = location

            speed = if (location.hasSpeed()) {
                location.speed * 3.6f
            } else {
                0f
            }
            gpsStatus = "GPS AKTIF"
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted = permissions.values.any { it }

            if (granted) {
                distance = 0f
                speed = 0f
                seconds = 0
                lastLocation = null
                riding = true
                tracker.start()
                gpsStatus = "MENCARI SATELIT GPS"
            } else {
                gpsStatus = "IZIN GPS DITOLAK"
            }
        }

    LaunchedEffect(riding) {
        while (riding) {
            delay(1000)
            seconds++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tracker.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(dark)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(25.dp))

        Text(
            "RIDE TRACK",
            color = orange,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text("CYCLING TRACKER", color = Color.Gray)

        Spacer(Modifier.height(35.dp))

        Text("CURRENT SPEED", color = Color.Gray)

        Text(
            String.format(Locale.US, "%.1f", speed),
            color = Color.White,
            fontSize = 76.sp,
            fontWeight = FontWeight.Bold
        )

        Text("KM/H", color = orange)

        Spacer(Modifier.height(30.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                "DISTANCE",
                String.format(Locale.US, "%.2f", distance / 1000f),
                "KM"
            )

            StatCard(
                "DURATION",
                String.format(
                    Locale.US,
                    "%02d:%02d",
                    seconds / 60,
                    seconds % 60
                ),
                "MIN"
            )
        }

        Spacer(Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Color(0xFF252525),
                    RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                gpsStatus,
                color = Color.LightGray
            )
        }

        Spacer(Modifier.height(30.dp))

        Button(
            onClick = {
                if (riding) {
                    tracker.stop()
                    riding = false
                    speed = 0f
                    gpsStatus = "PERJALANAN SELESAI"
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (riding) {
                    Color(0xFFD32F2F)
                } else {
                    orange
                }
            )
        ) {
            Text(
                if (riding) "STOP RIDE" else "START RIDE",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(15.dp))

        Text(
            if (riding) "Ride session active" else "Ready to ride?",
            color = Color.Gray
        )
    }
}

@Composable
fun StatCard(title: String, value: String, unit: String) {
    Column(
        modifier = Modifier
            .width(145.dp)
            .background(
                Color(0xFF252525),
                RoundedCornerShape(15.dp)
            )
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.Gray, fontSize = 11.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )
        Text(unit, color = Color(0xFFFF6B00), fontSize = 11.sp)
    }
}

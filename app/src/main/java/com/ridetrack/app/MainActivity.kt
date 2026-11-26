package com.ridetrack.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!RideState.live.active) RideStore.recover(this)
        setContent {
            var showHistory by remember { mutableStateOf(false) }
            if (showHistory) {
                HistoryScreen(onClose = { showHistory = false })
            } else {
                Dashboard(onHistory = { showHistory = true })
            }
        }
    }
}

private fun clock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
fun Dashboard(onHistory: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val orange = Color(0xFFFF6B00)
    val dark = Color(0xFF101010)

    val live = RideState.live
    val ride = live.ride
    val riding = live.active

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                RideService.start(context)
            } else {
                RideState.live = LiveRide(status = "AKTIFKAN IZIN LOKASI AKURAT")
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(dark)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
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

        Text("CYCLING TRACKER", color = Color.Gray, fontSize = 12.sp)

        Spacer(Modifier.height(8.dp))

        Text(
            if (live.status.isNotEmpty()) live.status else if (riding) "MEREKAM" else "SIAP",
            color = Color.Gray,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "%.1f".format(live.speed * 3.6f),
            color = Color.White,
            fontSize = 88.sp,
            fontWeight = FontWeight.Bold
        )
        Text("KM/JAM", color = Color.Gray, fontSize = 14.sp)

        Spacer(Modifier.height(24.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("JARAK", color = Color.Gray, fontSize = 11.sp)
                Text(
                    "%.2f km".format(ride.distance / 1000f),
                    color = orange,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("WAKTU", color = Color.Gray, fontSize = 11.sp)
                Text(
                    clock(ride.seconds),
                    color = orange,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (riding) {
            RouteMap(
                points = ride.points,
                follow = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Spacer(Modifier.height(24.dp))
        }

        Button(
            onClick = {
                if (riding) {
                    RideService.stop(context)
                } else {
                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= 33) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (riding) Color(0xFFD32F2F) else orange
            )
        ) {
            Text(
                if (riding) "SELESAI" else "MULAI",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onHistory) {
            Text("RIWAYAT", color = orange)
        }
    }
}

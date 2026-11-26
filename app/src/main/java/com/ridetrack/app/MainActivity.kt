
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

        Text("CYCLING TRACKER", color =

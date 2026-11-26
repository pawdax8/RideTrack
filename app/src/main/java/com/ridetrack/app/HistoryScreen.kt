package com.ridetrack.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDate(time: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(time))

@Composable
fun HistoryScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var rides by remember { mutableStateOf<List<Ride>?>(null) }
    var selected by remember { mutableStateOf<Ride?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        rides = withContext(Dispatchers.IO) { RideStore.list(context) }
    }

    BackHandler {
        if (selected != null) selected = null else onClose()
    }

    val ride = selected
    if (ride != null) {
        RideDetail(
            ride,
            onBack = { selected = null },
            onDelete = {
                RideStore.delete(context, ride)
                selected = null
                reload++
            }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        TextButton(onClick = onClose) { Text("← KEMBALI") }
        Text("RIWAYAT", color = Color(0xFFFF6B00), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        val list = rides
        when {
            list == null -> Text("Memuat...", col

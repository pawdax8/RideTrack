package com.ridetrack.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDate(time: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(time))

// Jarak diasumsikan dalam meter.
private fun formatKm(meters: Float): String = "%.2f km".format(meters / 1000f)

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
            list == null -> Text("Memuat...", color = Color.Gray)
            list.isEmpty() -> Text("Belum ada perjalanan.", color = Color.Gray)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list) { item ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C1C1C), RoundedCornerShape(12.dp))
                            .clickable { selected = item }
                            .padding(16.dp)
                    ) {
                        Text(formatDate(item.startedAt), color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatKm(item.distance)}  •  ${formatDuration(item.seconds)}",
                            color = Color(0xFFFF6B00),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Rata-rata ${formatNumber(item.avgSpeed)} km/j  •  " +
                                "Tercepat ${formatNumber(item.maxSpeed)} km/j",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Text(
                            "Naik ${formatNumber(item.elevationGain, 0)} m  •  " +
                                "Maks ${formatNumber(item.maxElevation, 0)} m",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RideDetail(ride: Ride, onBack: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← KEMBALI") }
            Row {
                TextButton(onClick = { picking = true }) { Text("BAGIKAN", color = Color(0xFFFF6B00)) }
                TextButton(onClick = { confirm = true }) { Text("HAPUS", color = Color(0xFFFF5252)) }
            }
        }
        Text(formatDate(ride.startedAt), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("JARAK", formatKm(ride.distance))
            Stat("WAKTU TOTAL", formatDuration(ride.seconds))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("WAKTU GERAK", formatDuration(ride.movingSeconds))
            Stat("RATA-RATA", formatNumber(ride.avgSpeed) + " km/j")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("TERCEPAT", formatNumber(ride.maxSpeed) + " km/j")
            Stat("KENAIKAN", formatNumber(ride.elevationGain, 0) + " m")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("ELEVASI MAKS", formatNumber(ride.maxElevation, 0) + " m")
            Stat("TITIK RUTE", ride.points.size.toString())
        }
        Spacer(Modifier.height(16.dp))

        RouteMap(
            points = ride.points,
            follow = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }

    if (picking) {
        var previews by remember { mutableStateOf<List<ImageBitmap>?>(null) }
        LaunchedEffect(Unit) {
            previews = withContext(Dispatchers.Default) {
                CardStyle.values().map { ShareCard.render(ride, it, 0.3f).asImageBitmap() }
            }
        }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Pilih kartu") },
            text = {
                val list = previews
                if (list == null) {
                    Text("Membuat pratinjau...")
                } else {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CardStyle.values().forEachIndexed { index, style ->
                            Column(
                                Modifier.clickable {
                                    picking = false
                                    scope.launch {
                                        val ok = ShareCard.share(context, ride, style)
                                        if (!ok) {
                                            Toast.makeText(
                                                context,
                                                "Gagal membuat kartu (butuh Android 10 atau lebih baru)",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier
                                        .background(Color(0xFF5B6B7B), RoundedCornerShape(8.dp))
                                        .padding(6.dp)
                                ) {
                                    Image(
                                        bitmap = list[index],
                                        contentDescription = style.label,
                                        modifier = Modifier
                                            .height(200.dp)
                                            .aspectRatio(list[index].width.toFloat() / list[index].height)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(style.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) { Text("BATAL") }
            }
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Hapus perjalanan?") },
            text = { Text("Data perjalanan ini akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }) { Text("HAPUS") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("BATAL") }
            }
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(value, color = Color(0xFFFF6B00), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

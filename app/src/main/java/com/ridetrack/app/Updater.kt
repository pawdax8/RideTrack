package com.ridetrack.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val build: Int, val apkUrl: String)

// Mengambil rilis terbaru dari GitHub. Hasil null berarti belum ada versi lebih baru.
suspend fun findUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
    val url = URL(
        "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
    )
    val connection = url.openConnection() as HttpURLConnection

    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "RideTrack")

        val code = connection.responseCode
        if (code == 404) error("Rilis belum ada atau repo masih private")
        if (code != 200) error("Server membalas kode $code")

        val release = JSONObject(
            connection.inputStream.bufferedReader().use { it.readText() }
        )
        val build = release.getString("tag_name").removePrefix("v").toIntOrNull()
            ?: error("Nomor versi rilis tidak dikenali")

        if (build <= BuildConfig.VERSION_CODE) return@withContext null

        val assets = release.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) {
                return@withContext UpdateInfo(
                    build,
                    asset.getString("browser_download_url")
                )
            }
        }
        error("File APK tidak ada di rilis")
    } finally {
        connection.disconnect()
    }
}

@Composable
fun UpdateSection(riding: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    fun check() {
        if (checking) return
        checking = true
        message = "Memeriksa update..."
        scope.launch {
            try {
                update = findUpdate()
                message = if (update == null) {
                    "Aplikasi sudah versi terbaru"
                } else {
                    "Update tersedia"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Gagal cek update: ${e.message}"
            } finally {
                checking = false
            }
        }
    }

    // Cek otomatis saat aplikasi dibuka.
    LaunchedEffect(Unit) { check() }

    // Sembunyikan saat bersepeda agar tidak mengganggu.
    if (riding) return

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { check() }, enabled = !checking) {
            Text("CEK UPDATE")
        }
        Text("Versi ${BuildConfig.VERSION_NAME}", color = Color.Gray, fontSize = 12.sp)
        Text(message, color = Color.Gray, fontSize = 12.sp)
    }

    update?.let { info ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Update tersedia") },
            text = {
                Text(
                    "Versi 1.0.${info.build} sudah ada. " +
                        "Setelah APK selesai diunduh, buka file tersebut untuk memasang."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl))
                        )
                    } catch (e: ActivityNotFoundException) {
                        message = "Tidak ada browser untuk mengunduh"
                    }
                    update = null
                }) { Text("UNDUH") }
            },
            dismissButton = {
                TextButton(onClick = { update = null }) { Text("NANTI") }
            }
        )
    }
}

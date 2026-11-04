package dev.rpcdroid.nr.ui

import android.content.Intent
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import dev.rpcdroid.nr.DebugLog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.rpcdroid.nr.LoginActivity
import dev.rpcdroid.nr.Prefs
import dev.rpcdroid.nr.ShizukuBridge
import dev.rpcdroid.nr.UiModel

@Composable
fun SettingsScreen(m: UiModel, modifier: Modifier) {
    val ctx = LocalContext.current
    var sliderVal by remember { mutableFloatStateOf(m.linger.toFloat()) }
    var bgVal by remember { mutableFloatStateOf(m.bgMin.toFloat()) }
    var showToken by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))

        SectionCard("Metode deteksi game") {
            ModeOption(
                "Accessibility", "Event-driven, tanpa notifikasi. Paling hemat baterai.",
                m.mode == Prefs.MODE_A11Y, m.a11yOn,
            ) { m.setMode(Prefs.MODE_A11Y) }
            ModeOption(
                "Usage Access", "Polling 4 detik saat layar menyala, ada notifikasi kecil.",
                m.mode == Prefs.MODE_USAGE, m.usageOn,
            ) { m.setMode(Prefs.MODE_USAGE) }
            ModeOption(
                "Shizuku", "Deteksi buka/tutup game paling akurat. Perlu Shizuku berjalan.",
                m.mode == Prefs.MODE_SHIZUKU, m.shizuku == ShizukuBridge.Status.READY,
            ) { m.setMode(Prefs.MODE_SHIZUKU) }
            if (m.mode == Prefs.MODE_SHIZUKU) {
                if (!m.detectorReady) ShizukuPanel(m)
            } else if (!m.detectorReady) {
                Button(
                    {
                        ctx.safeStart(
                            Intent(if (m.mode == Prefs.MODE_USAGE) Settings.ACTION_USAGE_ACCESS_SETTINGS else Settings.ACTION_ACCESSIBILITY_SETTINGS),
                        )
                    },
                    Modifier.fillMaxWidth(),
                ) { Text("Aktifkan izin metode ini") }
            }
        }

        if (m.mode == Prefs.MODE_SHIZUKU) {
            SectionCard("Game di latar belakang") {
                Text("${bgVal.toInt()} menit", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Slider(
                    bgVal, { bgVal = it }, valueRange = 1f..60f,
                    onValueChangeFinished = { m.setBgMin(bgVal.toInt()) },
                )
                Text(
                    "Status dihapus seketika saat game ditutup. Jika game hanya berpindah ke latar belakang (proses masih hidup), status dipertahankan selama batas ini.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SectionCard("Jeda sebelum status dihapus") {
                Text("${sliderVal.toInt()} detik", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Slider(
                    sliderVal, { sliderVal = it }, valueRange = 10f..180f, steps = 16,
                    onValueChangeFinished = { m.setLinger(sliderVal.toInt()) },
                )
                Text(
                    "Tanpa Shizuku, \"game ditutup\" dideteksi dari game meninggalkan layar depan. Jeda ini menjaga status tetap ada saat kamu berpindah app sebentar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard("Akun Discord") {
            Text(
                if (m.hasToken) "Token tersimpan terenkripsi di perangkat ini." else "Token belum diisi.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button({ showToken = true }, Modifier.fillMaxWidth()) { Text(if (m.hasToken) "Ganti token" else "Isi token") }
            OutlinedButton({ ctx.startActivity(Intent(ctx, LoginActivity::class.java)) }, Modifier.fillMaxWidth()) {
                Text("Login lewat Discord")
            }
            if (m.hasToken) TextButton({ confirmClear = true }, Modifier.fillMaxWidth()) { Text("Hapus token") }
            WarningCard("Memakai token akun (self-bot) melanggar ketentuan layanan Discord. Gunakan akun sekunder.")
        }

        SectionCard("Agar tidak dimatikan sistem") {
            Text(
                "Beberapa ROM (MIUI, ColorOS, OriginOS, One UI) mematikan service di latar belakang. Set aplikasi ke \"Tanpa batasan\" pada baterai, dan izinkan autostart jika ada.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                { ctx.safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                Modifier.fillMaxWidth(),
            ) { Text("Pengaturan baterai") }
            OutlinedButton({ ctx.safeStart(ctx.appInfoIntent()) }, Modifier.fillMaxWidth()) { Text("Info aplikasi") }
        }

        SectionCard("Diagnostik") {
            Text(
                "Jika status Discord tidak hilang setelah game ditutup: coba ulangi, buka log ini, salin, dan kirim. " +
                    "Log hanya di memori, tanpa token, tapi berisi nama package app yang kamu buka.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton({ showLog = true }, Modifier.fillMaxWidth()) { Text("Lihat log") }
        }

        SectionCard("Tentang") {
            val ver = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
            Text(
                "RPCDroid NR ${ver ?: ""}\nVersi non-root dari RPCDroid. Lisensi GPL-3.0.\nDaftar game dan protokol Gateway diadaptasi dari RPCDroid (pandjiSTR).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showToken) {
        var t by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showToken = false },
            title = { Text("Token Discord") },
            text = {
                OutlinedTextField(
                    t, { t = it }, singleLine = true, label = { Text("Token") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton({ m.saveToken(t); showToken = false }, enabled = t.isNotBlank()) { Text("Simpan") } },
            dismissButton = { TextButton({ showToken = false }) { Text("Batal") } },
        )
    }
    if (showLog) {
        var text by remember { mutableStateOf(DebugLog.dump()) }
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Log diagnostik (terbaru di atas)") },
            text = {
                Column {
                    Row {
                        TextButton({ text = DebugLog.dump() }) { Text("Muat ulang") }
                        TextButton({ DebugLog.clear(); text = "" }) { Text("Bersihkan") }
                    }
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        Text(text.ifEmpty { "(kosong)" }, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            },
            confirmButton = {
                TextButton({
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("rpcnr-log", text))
                    Toast.makeText(ctx, "Log disalin", Toast.LENGTH_SHORT).show()
                }) { Text("Salin") }
            },
            dismissButton = { TextButton({ showLog = false }) { Text("Tutup") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus token?") },
            text = { Text("Status Discord berhenti sampai kamu mengisi token lagi.") },
            confirmButton = { TextButton({ m.clearToken(); confirmClear = false }) { Text("Hapus") } },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Batal") } },
        )
    }
}

package dev.rpcdroid.nr.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.rpcdroid.nr.LoginActivity
import dev.rpcdroid.nr.Prefs
import dev.rpcdroid.nr.ShizukuBridge
import dev.rpcdroid.nr.UiModel

/** Langkah ditentukan murni oleh state (token ada? izin aktif?), jadi otomatis maju saat user kembali dari pengaturan. */
@Composable
fun OnboardingScreen(m: UiModel) {
    val step = when {
        !m.hasToken -> 0
        !m.detectorReady -> 1
        else -> 2
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("RPCDroid NR", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Tampilkan game yang kamu mainkan sebagai status Discord, tanpa root.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StepIndicator(step, listOf("Token", "Izin", "Siap"))
            AnimatedContent(step, label = "onboarding-step") { s ->
                when (s) {
                    0 -> TokenStep(m)
                    1 -> PermissionStep(m)
                    else -> DoneStep(m)
                }
            }
        }
    }
}

@Composable
private fun TokenStep(m: UiModel) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Masukkan token Discord", style = MaterialTheme.typography.titleLarge)
        Text(
            "Dipakai untuk mengirim status. Disimpan terenkripsi di perangkat ini dan hanya dikirim ke Discord.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Token") },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { TextButton({ show = !show }) { Text(if (show) "Sembunyi" else "Lihat") } },
            modifier = Modifier.fillMaxWidth(),
        )
        Button({ m.saveToken(text); text = "" }, Modifier.fillMaxWidth(), enabled = text.isNotBlank()) { Text("Simpan & lanjut") }
        OutlinedButton({ ctx.startActivity(Intent(ctx, LoginActivity::class.java)) }, Modifier.fillMaxWidth()) {
            Text("Login lewat Discord")
        }
        WarningCard("Ini memakai token akun (self-bot) yang melanggar ketentuan layanan Discord. Gunakan akun sekunder.")
    }
}

@Composable
private fun PermissionStep(m: UiModel) {
    val ctx = LocalContext.current
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Izinkan deteksi game", style = MaterialTheme.typography.titleLarge)
        Text(
            "Aplikasi perlu tahu app apa yang sedang di layar depan. Hanya nama package yang dibaca, bukan isi layar.",
            style = MaterialTheme.typography.bodyMedium,
        )
        ModeOption(
            "Accessibility (disarankan)", "Langsung saat app berganti, tanpa notifikasi permanen.",
            m.mode == Prefs.MODE_A11Y, m.a11yOn,
        ) { m.setMode(Prefs.MODE_A11Y) }
        ModeOption(
            "Usage Access", "Memeriksa tiap 4 detik saat layar menyala. Ada notifikasi kecil.",
            m.mode == Prefs.MODE_USAGE, m.usageOn,
        ) { m.setMode(Prefs.MODE_USAGE) }
        ModeOption(
            "Shizuku (paling akurat)", "Tahu persis kapan game dibuka dan ditutup. Perlu Shizuku, dijalankan ulang tiap reboot.",
            m.mode == Prefs.MODE_SHIZUKU, m.shizuku == ShizukuBridge.Status.READY,
        ) { m.setMode(Prefs.MODE_SHIZUKU) }

        if (m.mode == Prefs.MODE_SHIZUKU) {
            ShizukuPanel(m)
        } else if (m.mode == Prefs.MODE_A11Y) {
            Text(
                "1. Buka pengaturan, pilih RPCDroid NR, lalu aktifkan.\n" +
                    "Android 13+ (APK dari luar Play Store): jika tombolnya abu-abu, buka Info aplikasi → ⋮ → Izinkan setelan terbatas, lalu ulangi.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button({ ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, Modifier.fillMaxWidth()) {
                Text("Buka pengaturan Accessibility")
            }
            TextButton({ ctx.safeStart(ctx.appInfoIntent()) }, Modifier.fillMaxWidth()) { Text("Buka Info aplikasi") }
        } else {
            Button({ ctx.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, Modifier.fillMaxWidth()) {
                Text("Buka pengaturan Usage Access")
            }
            if (Build.VERSION.SDK_INT >= 33) {
                OutlinedButton({ notif.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.fillMaxWidth()) {
                    Text("Izinkan notifikasi")
                }
            }
        }
    }
}

@Composable
private fun DoneStep(m: UiModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.CheckCircle, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Semua siap", style = MaterialTheme.typography.titleLarge)
        Text(
            "Buka game dari daftar mana pun, dan statusnya muncul di Discord otomatis. Status dihapus sesaat setelah kamu keluar dari game.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button({ m.finishOnboarding() }, Modifier.fillMaxWidth()) { Text("Mulai") }
    }
}

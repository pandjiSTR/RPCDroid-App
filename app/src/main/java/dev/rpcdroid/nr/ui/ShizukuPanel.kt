package dev.rpcdroid.nr.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.rpcdroid.nr.ShizukuBridge
import dev.rpcdroid.nr.ShizukuBridge.Status
import dev.rpcdroid.nr.UiModel

/** Panduan bergantung status: pasang -> jalankan -> beri izin -> siap. Dipakai di onboarding dan pengaturan. */
@Composable
fun ShizukuPanel(m: UiModel) {
    val ctx = LocalContext.current
    val small = MaterialTheme.typography.bodySmall
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (m.shizuku) {
            Status.NOT_INSTALLED -> {
                Text("Shizuku belum terpasang di perangkat ini.", style = small)
                Button({ ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuBridge.DOWNLOAD_URL))) }, Modifier.fillMaxWidth()) {
                    Text("Unduh Shizuku")
                }
            }
            Status.NOT_RUNNING -> {
                Text(
                    "Shizuku terpasang tapi belum berjalan. Buka Shizuku lalu mulai lewat Wireless debugging (Android 11+) " +
                        "atau adb dari PC (Android 10). Setelah reboot Shizuku perlu dijalankan lagi; RPCDroid NR akan tersambung sendiri.",
                    style = small,
                )
                Button(
                    { ShizukuBridge.launchIntent(ctx)?.let { ctx.safeStart(it) } },
                    Modifier.fillMaxWidth(),
                ) { Text("Buka Shizuku") }
            }
            Status.NEED_PERMISSION -> {
                Text("Shizuku berjalan. Beri izin kepada RPCDroid NR.", style = small)
                Button({ ShizukuBridge.requestPermission() }, Modifier.fillMaxWidth()) { Text("Beri izin") }
                Text(
                    "Jika dialog tidak muncul: buka Shizuku → Aplikasi yang diotorisasi → aktifkan RPCDroid NR.",
                    style = small, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    { ShizukuBridge.launchIntent(ctx)?.let { ctx.safeStart(it) } },
                    Modifier.fillMaxWidth(),
                ) { Text("Buka Shizuku") }
            }
            Status.UNSUPPORTED -> Text("Versi Shizuku terlalu lama. Perbarui Shizuku ke versi terbaru.", style = small)
            Status.READY -> Text("Shizuku siap. Game dibuka dan ditutup akan terdeteksi seketika.", style = small)
        }
    }
}

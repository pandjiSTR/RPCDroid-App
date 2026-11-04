package dev.rpcdroid.nr.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.rpcdroid.nr.Phase
import dev.rpcdroid.nr.Prefs
import dev.rpcdroid.nr.PresenceEngine
import dev.rpcdroid.nr.ShizukuBridge
import dev.rpcdroid.nr.UiModel

private class StatusUi(
    val container: Color, val onContainer: Color, val icon: ImageVector,
    val title: String, val body: String, val action: Pair<String, () -> Unit>? = null,
)

@Composable
fun HomeScreen(m: UiModel, modifier: Modifier, goSettings: () -> Unit) {
    val ctx = LocalContext.current
    val st = PresenceEngine.state
    val c = MaterialTheme.colorScheme

    val ui = when {
        !m.hasToken -> StatusUi(
            c.errorContainer, c.onErrorContainer, Icons.Filled.Warning, "Token belum diisi",
            "Tanpa token, status tidak bisa dikirim ke Discord.", "Isi token" to goSettings,
        )
        !m.detectorReady -> StatusUi(
            c.errorContainer, c.onErrorContainer, Icons.Filled.Warning, "Deteksi game mati",
            when (m.mode) {
                Prefs.MODE_USAGE -> "Izin Usage Access belum aktif."
                Prefs.MODE_SHIZUKU -> when (m.shizuku) {
                    ShizukuBridge.Status.NOT_INSTALLED -> "Shizuku belum terpasang."
                    ShizukuBridge.Status.NOT_RUNNING -> "Shizuku belum berjalan. Jalankan lagi setelah reboot."
                    ShizukuBridge.Status.NEED_PERMISSION -> "RPCDroid NR belum diberi izin di Shizuku."
                    else -> "Versi Shizuku tidak didukung."
                }
                else -> "Service Accessibility belum aktif. Sistem atau penghemat baterai mungkin mematikannya."
            },
            "Perbaiki" to {
                when (m.mode) {
                    Prefs.MODE_SHIZUKU -> goSettings()
                    Prefs.MODE_USAGE -> ctx.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    else -> ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            },
        )
        st.phase == Phase.AUTH_FAILED -> StatusUi(
            c.errorContainer, c.onErrorContainer, Icons.Filled.Warning, "Token ditolak Discord",
            "Token kedaluwarsa atau salah. Ganti dengan token baru.", "Ganti token" to goSettings,
        )
        !m.enabled -> StatusUi(c.surfaceVariant, c.onSurfaceVariant, Icons.Filled.Info, "Dijeda", "Rich Presence dimatikan.")
        st.phase == Phase.CONNECTED -> StatusUi(
            c.primaryContainer, c.onPrimaryContainer, Icons.Filled.CheckCircle, "Terhubung",
            "Masuk sebagai ${st.user}. Status Discord aktif.",
        )
        st.phase == Phase.CONNECTING || st.phase == Phase.RECONNECTING -> StatusUi(
            c.secondaryContainer, c.onSecondaryContainer, Icons.Filled.Refresh,
            if (st.phase == Phase.CONNECTING) "Menghubungkan…" else "Menyambung ulang…",
            "Sebentar, menghubungi Discord.",
        )
        else -> StatusUi(
            c.surfaceVariant, c.onSurfaceVariant, Icons.Filled.Info, "Siap",
            "Buka salah satu game dan statusnya muncul otomatis.",
        )
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("RPCDroid NR", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ui.container, contentColor = ui.onContainer)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ui.icon, null, Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(ui.title, style = MaterialTheme.typography.titleMedium)
                    Text(ui.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            ui.action?.let { (label, onClick) ->
                Button(
                    onClick, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ui.onContainer, contentColor = ui.container),
                ) { Text(label) }
            }
        }

        val name = st.gameName
        val pkg = st.gamePkg
        if (name != null && pkg != null) {
            SectionCard("Sedang dimainkan") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            rememberElapsed(st.startMs), style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rich Presence", style = MaterialTheme.typography.titleMedium)
                    Text(if (m.enabled) "Aktif" else "Dijeda", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(m.enabled, { m.setEnabled(it) })
            }
        }
    }
}

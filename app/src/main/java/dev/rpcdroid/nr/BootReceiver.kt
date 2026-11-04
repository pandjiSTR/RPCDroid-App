package dev.rpcdroid.nr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Mode a11y tidak butuh ini (sistem mengikat ulang service sendiri). Untuk mode UsageStats dan Shizuku. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        val p = Prefs(c)
        if (!p.onboarded) return
        when (p.mode) {
            Prefs.MODE_USAGE -> runCatching { c.startForegroundService(Intent(c, PollService::class.java)) }
            // Menunggu Shizuku dijalankan; tersambung otomatis begitu binder-nya muncul
            Prefs.MODE_SHIZUKU -> runCatching { c.startForegroundService(Intent(c, ShizukuService::class.java)) }
        }
    }
}

package dev.rpcdroid.nr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build

/** Notifikasi foreground minimal (IMPORTANCE_MIN) untuk mode UsageStats dan Shizuku. */
object Fgs {
    private const val CH = "detect"
    private const val ID = 1

    private fun build(ctx: Context, text: String): Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Deteksi game", NotificationManager.IMPORTANCE_MIN))
        return Notification.Builder(ctx, CH).setContentTitle("RPCDroid NR").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true).build()
    }

    fun start(s: Service, text: String) {
        val n = build(s, text)
        if (Build.VERSION.SDK_INT >= 34) s.startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else s.startForeground(ID, n)
    }

    fun update(ctx: Context, text: String) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(ID, build(ctx, text)) }
    }
}

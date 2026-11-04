package dev.rpcdroid.nr

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.view.accessibility.AccessibilityEvent

/**
 * Mode utama. Sistem yang memanggil kita tepat saat window berganti: tidak ada polling, tidak ada
 * foreground service, tidak ada notifikasi permanen. Ini padanan non-root dari IProcessObserver di RPCDroid.
 */
class AppDetectorService : AccessibilityService() {
    private var rx: BroadcastReceiver? = null

    override fun onServiceConnected() {
        PresenceEngine.init(applicationContext)
        rx = ScreenEvents.register(this)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        PresenceEngine.onForeground(pkg, Prefs.MODE_A11Y)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        rx?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }
}

package dev.rpcdroid.nr

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent

/**
 * Mode Accessibility. Event window = petunjuk CEPAT (game mulai); pemeriksaan jendela aktif = sumber KEBENARAN
 * (game masih di depan atau tidak). Event saja tidak cukup: bisa hilang, tergabung, atau tidak pernah datang
 * (mis. game digeser dari recents), dan itu penyebab status "nyangkut" di Discord.
 *
 * Pemeriksaan hanya berjalan selama ada sesi game dan layar menyala, jadi saat idle tetap nol timer.
 */
class AppDetectorService : AccessibilityService() {
    private var rx: BroadcastReceiver? = null
    private lateinit var ht: HandlerThread
    private lateinit var h: Handler

    private val verifyOnce = Runnable { verify() }
    private val verifyLoop = object : Runnable {
        override fun run() {
            if (!PresenceEngine.hasSession) return            // sesi selesai: loop berhenti sendiri
            verify()
            h.postDelayed(this, VERIFY_MS)
        }
    }

    override fun onServiceConnected() {
        PresenceEngine.init(applicationContext)
        ht = HandlerThread("rpcnr-verify").also { it.start() }
        h = Handler(ht.looper)
        val canRead = ((serviceInfo?.capabilities ?: 0) and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0
        DebugLog.d("SVC", "connected, canRetrieveWindowContent=$canRead")
        rx = ScreenEvents.register(this, extraOn = { startLoop() }, extraOff = { stopLoop() })
        PresenceEngine.sessionHook = { active -> if (active) startLoop() else stopLoop() }
        if (PresenceEngine.hasSession) startLoop()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        DebugLog.d("EV", "$pkg ${e.className?.toString()?.substringAfterLast('.') ?: ""}")
        PresenceEngine.onForeground(pkg, Prefs.MODE_A11Y)
        // Setiap perubahan window saat ada sesi: periksa jendela aktif sebentar lagi (di-debounce)
        if (PresenceEngine.hasSession) { h.removeCallbacks(verifyOnce); h.postDelayed(verifyOnce, 1200) }
    }

    @Suppress("DEPRECATION")
    private fun verify() {
        val pkg = try {
            val root = rootInActiveWindow
            val p = root?.packageName?.toString()
            root?.recycle()
            p
        } catch (e: Exception) { null }
        DebugLog.d("VERIFY", "active=$pkg")
        PresenceEngine.onVerified(pkg)
    }

    private fun startLoop() {
        h.removeCallbacks(verifyLoop)
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        if (PresenceEngine.hasSession && interactive) h.postDelayed(verifyLoop, FIRST_MS)
    }

    private fun stopLoop() { h.removeCallbacks(verifyLoop); h.removeCallbacks(verifyOnce) }

    override fun onInterrupt() {}

    override fun onDestroy() {
        PresenceEngine.sessionHook = null
        PresenceEngine.shutdown()                      // service dimatikan: hapus status dan tutup socket
        rx?.let { runCatching { unregisterReceiver(it) } }
        if (::h.isInitialized) { h.removeCallbacksAndMessages(null); ht.quitSafely() }
        super.onDestroy()
    }

    private companion object {
        const val VERIFY_MS = 10_000L
        const val FIRST_MS = 3_000L
    }
}

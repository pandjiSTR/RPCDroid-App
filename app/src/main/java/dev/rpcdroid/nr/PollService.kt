package dev.rpcdroid.nr

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager

/**
 * Mode alternatif tanpa Accessibility. Membaca UsageEvents tiap 4 detik, HANYA saat layar menyala
 * (loop dihentikan saat SCREEN_OFF). Butuh foreground service agar tidak dibunuh sistem.
 */
class PollService : Service() {
    private lateinit var ht: HandlerThread
    private lateinit var h: Handler
    private var rx: BroadcastReceiver? = null
    private var lastQuery = 0L
    private var fg: String? = null

    private val tick = object : Runnable {
        override fun run() { poll(); h.postDelayed(this, POLL_MS) }
    }

    override fun onCreate() {
        PresenceEngine.init(applicationContext)
        ht = HandlerThread("rpcnr-poll").also { it.start() }
        h = Handler(ht.looper)
        rx = ScreenEvents.register(this, extraOn = { startLoop() }, extraOff = { h.removeCallbacks(tick) })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Fgs.start(this, "Mendeteksi game untuk Discord")
        if (getSystemService(PowerManager::class.java).isInteractive) startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        h.removeCallbacks(tick)
        lastQuery = System.currentTimeMillis() - 15_000      // tangkap app yang sedang di depan
        h.post(tick)
    }

    private fun poll() {
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(lastQuery, now)
        val e = UsageEvents.Event()
        var changed = false
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (fg != e.packageName) { fg = e.packageName; changed = true }
                UsageEvents.Event.ACTIVITY_STOPPED -> if (fg == e.packageName) { fg = null; changed = true }
            }
        }
        lastQuery = now - 500                                // overlap kecil; pemrosesan idempotent
        if (changed) PresenceEngine.onForeground(fg, Prefs.MODE_USAGE)
    }

    override fun onDestroy() {
        PresenceEngine.shutdown()
        h.removeCallbacksAndMessages(null); ht.quitSafely()
        rx?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object { private const val POLL_MS = 4000L }
}

package dev.rpcdroid.nr

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import dev.rpcdroid.nr.shizuku.IWatchCallback
import dev.rpcdroid.nr.shizuku.IWatchService
import dev.rpcdroid.nr.shizuku.WatchService
import rikka.shizuku.Shizuku

/**
 * Foreground service tipis: menunggu Shizuku (juga setelah reboot), mengikat WatchService di proses shell,
 * dan meneruskan event game dibuka / proses mati ke [PresenceEngine].
 */
class ShizukuService : Service() {
    private var svc: IWatchService? = null
    private var bound = false
    private var rx: BroadcastReceiver? = null

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, WatchService::class.java.name))
            .daemon(false)                    // ikut mati bersama app, tidak ada proses shell yatim
            .processNameSuffix("watch").debuggable(false).version(SERVICE_VERSION)
    }

    private val cb = object : IWatchCallback.Stub() {
        override fun onResumed(pkg: String) { PresenceEngine.onForeground(pkg, Prefs.MODE_SHIZUKU) }
        override fun onProcDied(proc: String) { PresenceEngine.onProcessDied(proc, Prefs.MODE_SHIZUKU) }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val s = IWatchService.Stub.asInterface(binder)
            svc = s
            runCatching { s.start(cb) }
            Fgs.update(this@ShizukuService, "Aktif · memantau game")
        }
        override fun onServiceDisconnected(name: ComponentName) {
            svc = null; bound = false
            Fgs.update(this@ShizukuService, "Terputus dari Shizuku")
        }
    }

    private val onBinder = Shizuku.OnBinderReceivedListener { tryBind() }
    private val onDead = Shizuku.OnBinderDeadListener {
        svc = null; bound = false
        Fgs.update(this, "Menunggu Shizuku…")
    }
    private val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> tryBind() }

    override fun onCreate() {
        PresenceEngine.init(applicationContext)
        Fgs.start(this, "Menunggu Shizuku…")
        rx = ScreenEvents.register(this)
        Shizuku.addBinderReceivedListenerSticky(onBinder)     // langsung terpanggil bila Shizuku sudah jalan
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        tryBind()
        return START_STICKY
    }

    private fun tryBind() {
        if (bound) return
        try {
            if (!Shizuku.pingBinder()) { Fgs.update(this, "Menunggu Shizuku…"); return }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Fgs.update(this, "Izin Shizuku diperlukan — buka RPCDroid NR"); return
            }
            bound = true
            Shizuku.bindUserService(args, conn)
        } catch (e: Throwable) { bound = false }
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPerm)
        rx?.let { runCatching { unregisterReceiver(it) } }
        runCatching { svc?.stop() }
        if (bound) runCatching { Shizuku.unbindUserService(args, conn, true) }
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object { private const val SERVICE_VERSION = 1 }
}

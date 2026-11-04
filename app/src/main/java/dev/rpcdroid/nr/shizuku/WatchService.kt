package dev.rpcdroid.nr.shizuku

import android.os.RemoteException
import kotlin.system.exitProcess

/**
 * Berjalan di proses shell (uid 2000) yang dibuat Shizuku. TIDAK boleh memakai Context/resource app.
 *
 * Event log sistem dipakai sebagai sumber kebenaran:
 *   wm_set_resumed_activity: [user,com.pkg/.Activity,reason]   -> game dibuka / app lain di depan
 *   am_proc_died:            [user,pid,processName,...]        -> proses mati (processName == package utama)
 * Lebih ringan dan lebih stabil antar-versi daripada mendaftarkan IProcessObserver lewat hidden API.
 */
class WatchService : IWatchService.Stub() {
    @Volatile private var running = false
    @Volatile private var cb: IWatchCallback? = null
    @Volatile private var proc: Process? = null

    override fun destroy() { stop(); exitProcess(0) }

    override fun start(cb: IWatchCallback) {
        stop()
        this.cb = cb
        running = true
        Thread({ run() }, "rpcnr-watch").apply { isDaemon = true }.start()
    }

    override fun stop() {
        running = false
        proc?.destroy(); proc = null
        cb = null
    }

    private fun run() {
        // Sinkronisasi awal: app yang sedang di depan saat ini (best-effort; gagal = tunggu event berikutnya)
        initialResumed()?.let { pkg -> send { onResumed(pkg) } }
        while (running) {
            try {
                val p = ProcessBuilder(
                    "logcat", "-b", "events", "-T", "1", "-v", "brief", "-s",
                    "wm_set_resumed_activity:I", "am_proc_died:I",
                ).redirectErrorStream(true).start()
                proc = p
                p.inputStream.bufferedReader().forEachLine { if (running) handle(it) }
            } catch (e: Exception) { /* diulang di bawah */ }
            if (running) try { Thread.sleep(2000) } catch (e: InterruptedException) { return }
        }
    }

    private fun handle(line: String) {
        RESUMED.find(line)?.let { m -> send { onResumed(m.groupValues[1]) }; return }
        DIED.find(line)?.let { m -> send { onProcDied(m.groupValues[1]) } }
    }

    private fun initialResumed(): String? = try {
        val p = ProcessBuilder("sh", "-c", "dumpsys activity activities | grep -m1 -i ResumedActivity")
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.destroy()
        RESUMED_DUMP.find(out)?.groupValues?.get(1)
    } catch (e: Exception) { null }

    private inline fun send(f: IWatchCallback.() -> Unit) {
        val c = cb ?: return
        try { c.f() } catch (e: RemoteException) { stop() }   // app sudah mati
    }

    private companion object {
        val RESUMED = Regex("""wm_set_resumed_activity\([^)]*\):\s*\[\d+,([^/,\]]+)/""")
        val DIED = Regex("""am_proc_died\([^)]*\):\s*\[\d+,\d+,([^,\]]+)""")
        val RESUMED_DUMP = Regex("""\su\d+\s([\w.]+)/""")
    }
}

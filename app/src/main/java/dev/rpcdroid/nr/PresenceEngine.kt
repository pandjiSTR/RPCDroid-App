package dev.rpcdroid.nr

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

enum class Phase { IDLE, CONNECTING, CONNECTED, RECONNECTING, AUTH_FAILED }

/** State yang dibaca UI. Snapshot state Compose boleh ditulis dari thread mana pun. */
data class EngineState(
    val phase: Phase = Phase.IDLE,
    val user: String? = null,
    val gamePkg: String? = null,
    val gameName: String? = null,
    val startMs: Long = 0L,
)

/**
 * Otak aplikasi, netral terhadap detektor. Dua desain yang membedakannya dari daemon root:
 *
 * 1. LAZY CONNECT: socket Discord hanya hidup selama ada game di layar depan (+ jeda [Prefs.lingerSec]).
 *    Saat idle: 0 koneksi, 0 timer, 0 baterai. Juga mengurangi jumlah IDENTIFY ke Discord.
 * 2. Tanpa info process-death (butuh root/system), "game ditutup" = "game meninggalkan layar depan"
 *    dengan jeda linger agar pindah app sebentar (balas chat, tarik notifikasi) tidak memutus status.
 *
 * Semua logika berjalan di satu thread [exec]; detektor cukup memanggil [onForeground].
 */
object PresenceEngine {
    private lateinit var app: Context
    private lateinit var prefs: Prefs
    private lateinit var catalog: GameCatalog
    private lateinit var ws: OkHttpClient
    private lateinit var rest: OkHttpClient

    private val exec = ScheduledThreadPoolExecutor(1) { r -> Thread(r, "rpcnr-engine").apply { isDaemon = true } }
        .apply { removeOnCancelPolicy = true }

    // state internal: hanya disentuh dari exec
    private var gateway: DiscordGateway? = null
    private var current: GameCatalog.Game? = null
    private var startMs = 0L
    private var lastFg: String? = null
    private var linger: ScheduledFuture<*>? = null
    private var ignoredPkgs: Set<String> = emptySet()
    private var authFailed = false

    /** Dipanggil saat sesi game mulai/selesai; detektor memakainya untuk menyalakan/mematikan pemeriksaan berkala. */
    @Volatile var sessionHook: ((Boolean) -> Unit)? = null
    @Volatile var hasSession = false
        private set

    private fun setSession(active: Boolean) {
        if (hasSession == active) return
        hasSession = active
        DebugLog.d("ENGINE", if (active) "session START ${current?.pkg}" else "session END")
        sessionHook?.invoke(active)
    }

    var state by mutableStateOf(EngineState())
        private set

    @Synchronized
    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        prefs = Prefs(app); catalog = GameCatalog(app, prefs)
        ws = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        rest = ws.newBuilder().readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
        refreshIgnored()
    }

    // ── input dari detektor ─────────────────────────────────────────────────

    fun onForeground(pkg: String?, source: String) {
        if (source != prefs.mode) return
        exec.execute { evaluate(pkg) }
    }

    /** Hanya mode Shizuku: proses utama game mati -> status langsung dihapus, tanpa jeda. */
    fun onProcessDied(proc: String, source: String) {
        if (source != prefs.mode) return
        exec.execute {
            if (lastFg == proc) lastFg = null            // cegah onUserPresent menghidupkan game yang sudah mati
            if (current?.pkg == proc) { cancelLinger(); endSession() }
        }
    }

    fun onScreenOff() = exec.execute { if (current != null) scheduleLinger() }

    /** Setelah unlock, app terakhir di depan hampir pasti masih game tadi; event window belum tentu datang lagi. */
    fun onUserPresent() = exec.execute { evaluate(lastFg) }

    /**
     * Hasil pemeriksaan jendela aktif (mode Accessibility). Ini sumber kebenaran: tidak bergantung pada event
     * yang bisa hilang. Null / systemui / IME = tidak pasti, jangan ubah apa pun.
     */
    fun onVerified(pkg: String?) = exec.execute {
        if (prefs.mode != Prefs.MODE_A11Y) return@execute
        val c = current ?: return@execute
        when {
            pkg == null || isIgnored(pkg) -> {}
            pkg == c.pkg -> cancelLinger()                       // game masih di depan
            else -> { lastFg = pkg; scheduleLinger() }           // jendela aktif bukan game
        }
    }

    /** Service detektor dimatikan / app ditutup rapi: hapus status dan tutup socket. */
    fun shutdown() = exec.execute { cancelLinger(); endSession() }

    /** Token / switch utama / mode berubah: mulai ulang bersih. */
    fun onConfigChanged() = exec.execute {
        authFailed = false
        cancelLinger(); endSession(); refreshIgnored(); evaluate(lastFg)
    }

    /** Daftar game berubah: cukup putus sesi kalau game yang sedang tampil dimatikan. */
    fun onGamesChanged() = exec.execute {
        val c = current
        if (c != null && c.pkg in prefs.disabled) { cancelLinger(); endSession() }
        evaluate(lastFg)
    }

    // ── logika ──────────────────────────────────────────────────────────────

    private fun evaluate(pkg: String?) {
        if (pkg != null && isIgnored(pkg)) { DebugLog.d("ENGINE", "skip ignored $pkg"); return }
        lastFg = pkg
        val game = pkg?.let { catalog.lookup(it) }?.takeIf { prefs.enabled && it.pkg !in prefs.disabled }
        if (game != null) {
            cancelLinger()
            if (current?.pkg != game.pkg) {
                current = game; startMs = System.currentTimeMillis()
                state = state.copy(gamePkg = game.pkg, gameName = game.name, startMs = startMs)
                setSession(true)
            }
            if (ensureGateway()) pushPresence()
        } else if (current != null) {
            scheduleLinger()
        }
    }

    private fun ensureGateway(): Boolean {
        if (authFailed) return false
        if (gateway != null) return true
        val token = prefs.token ?: return false
        state = state.copy(phase = Phase.CONNECTING)
        gateway = DiscordGateway(token, ws, exec, object : DiscordGateway.Events {
            override fun onReady(user: String) { state = state.copy(phase = Phase.CONNECTED, user = user) }
            override fun onFatal(code: Int, reason: String) {
                authFailed = true; gateway = null; current = null; setSession(false)
                state = EngineState(Phase.AUTH_FAILED)
            }
            override fun onState(text: String) { state = state.copy(phase = Phase.RECONNECTING) }
        }).also { it.open() }
        return true
    }

    private fun pushPresence() {
        val g = gateway ?: return
        val c = current ?: return
        val start = startMs
        resolveIcon(c) { icon ->
            exec.execute { if (gateway === g && current === c) g.setPresence(Presence(c.name, start, c.appId, icon)) }
        }
    }

    /**
     * Game terdaftar di Discord -> cukup application_id, ikon dari CDN Discord.
     * Game tidak terdaftar + punya URL ikon -> external-assets (hasil di-cache permanen).
     */
    private fun resolveIcon(c: GameCatalog.Game, cb: (String?) -> Unit) {
        val url = c.icon ?: return cb(null)
        if (!url.startsWith("http")) return cb(url)
        prefs.iconCache(c.pkg)?.let { return cb(it) }
        val token = prefs.token ?: return cb(null)
        ExternalAssets.resolve(rest, token, c.appId, url) { mp ->
            if (mp != null) prefs.putIconCache(c.pkg, mp)
            cb(mp)
        }
    }

    private fun scheduleLinger() {
        if (linger?.isDone == false) return
        DebugLog.d("ENGINE", "linger ${lingerSeconds()}s started")
        linger = exec.schedule({
            // Exception di task terjadwal ditelan diam-diam oleh executor; catat agar tidak tak terlihat
            try { endSession() } catch (e: Exception) { DebugLog.d("ENGINE", "endSession FAILED ${e.javaClass.simpleName}") }
        }, lingerSeconds(), TimeUnit.SECONDS)
    }

    /** Shizuku tahu game mati secara pasti, jadi jeda "di latar belakang" boleh jauh lebih panjang. */
    private fun lingerSeconds(): Long =
        if (prefs.mode == Prefs.MODE_SHIZUKU) prefs.bgTimeoutMin * 60L else prefs.lingerSec.toLong()

    private fun cancelLinger() {
        if (linger != null) DebugLog.d("ENGINE", "linger cancelled")
        linger?.cancel(false); linger = null
    }

    private fun endSession() {
        linger = null
        // Kirim presence kosong dulu agar Discord segera menghapus aktivitas, baru tutup socket.
        gateway?.setPresence(null)
        gateway?.close(); gateway = null
        current = null
        setSession(false)
        state = if (authFailed) EngineState(Phase.AUTH_FAILED) else EngineState()
    }

    /** Window sistem yang sering muncul di atas game dan bukan sinyal "game ditutup". */
    private fun isIgnored(pkg: String) = pkg == app.packageName || pkg in ignoredPkgs

    private fun refreshIgnored() {
        val ime = try {
            (app.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.map { it.packageName }
        } catch (e: Exception) { emptyList() }
        // Contoh overlay umum; tambahkan sesuai ROM kalau ada panel game yang mengganggu.
        ignoredPkgs = (ime + listOf("com.android.systemui", "android", "com.samsung.android.game.gametools")).toSet()
    }
}

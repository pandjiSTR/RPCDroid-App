package dev.rpcdroid.nr

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.jvm.JvmName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Satu-satunya pemilik state UI. Layar hanya membaca properti dan memanggil fungsi di sini. */
class UiModel(private val ctx: Context) {
    val prefs = Prefs(ctx)
    val catalog = GameCatalog(ctx, prefs)

    var enabled by mutableStateOf(prefs.enabled)
        private set
    var mode by mutableStateOf(prefs.mode)
        private set
    var linger by mutableIntStateOf(prefs.lingerSec)
        private set
    var bgMin by mutableIntStateOf(prefs.bgTimeoutMin)
        private set
    var shizuku by mutableStateOf(ShizukuBridge.Status.NOT_RUNNING)
        private set
    var onboarded by mutableStateOf(prefs.onboarded)
        private set
    var disabled by mutableStateOf(prefs.disabled)
        private set
    var custom by mutableStateOf(prefs.custom)
        private set
    var hasToken by mutableStateOf(false)
        private set
    var a11yOn by mutableStateOf(false)
        private set
    var usageOn by mutableStateOf(false)
        private set
    var games by mutableStateOf<List<GameCatalog.Game>>(emptyList())
        private set
    /** Naik tiap refresh; layar yang butuh muat ulang daftar game memakainya sebagai key. */
    var tick by mutableIntStateOf(0)
        private set

    val detectorReady: Boolean
        get() = when (mode) {
            Prefs.MODE_USAGE -> usageOn
            Prefs.MODE_SHIZUKU -> shizuku == ShizukuBridge.Status.READY
            else -> a11yOn
        }

    private val onShizukuChange = Runnable { refreshShizuku() }
    private val onBinder = Shizuku.OnBinderReceivedListener { onShizukuChange.run() }
    private val onDead = Shizuku.OnBinderDeadListener { onShizukuChange.run() }
    private val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> onShizukuChange.run(); applyMode() }

    init {
        refresh()
        // Status Shizuku berubah di luar app (dijalankan / dihentikan / izin diberikan): perbarui UI seketika
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)
    }

    fun dispose() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPerm)
    }

    private fun refreshShizuku() { shizuku = ShizukuBridge.status(ctx) }

    /** Murah; dipanggil tiap ON_RESUME (user baru kembali dari pengaturan sistem / login). */
    fun refresh() {
        hasToken = prefs.token != null
        a11yOn = isA11yOn()
        usageOn = isUsageOn()
        refreshShizuku()
        tick++
    }

    suspend fun loadGames() { games = withContext(Dispatchers.Default) { catalog.installed() } }

    @JvmName("doSetEnabled")
    fun setEnabled(v: Boolean) { prefs.enabled = v; enabled = v; PresenceEngine.onConfigChanged() }

    @JvmName("doSetMode")
    fun setMode(m: String) {
        prefs.mode = m; mode = m; refresh(); applyMode(); PresenceEngine.onConfigChanged()
    }

    @JvmName("doSetLinger")
    fun setLinger(sec: Int) { prefs.lingerSec = sec; linger = sec }      // dibaca engine saat menjadwalkan
    @JvmName("doSetBgMin")
    fun setBgMin(min: Int) { prefs.bgTimeoutMin = min; bgMin = min }

    fun saveToken(t: String) { prefs.token = t; hasToken = prefs.token != null; PresenceEngine.onConfigChanged() }
    fun clearToken() { prefs.token = null; hasToken = false; PresenceEngine.onConfigChanged() }

    fun setGameOn(pkg: String, on: Boolean) {
        prefs.disabled = if (on) prefs.disabled - pkg else prefs.disabled + pkg
        disabled = prefs.disabled; PresenceEngine.onGamesChanged()
    }

    fun addCustom(pkg: String) {
        prefs.custom = prefs.custom + pkg; custom = prefs.custom; tick++; PresenceEngine.onGamesChanged()
    }

    fun removeCustom(pkg: String) {
        prefs.custom = prefs.custom - pkg; prefs.disabled = prefs.disabled - pkg
        custom = prefs.custom; disabled = prefs.disabled; tick++; PresenceEngine.onGamesChanged()
    }

    fun finishOnboarding() { prefs.onboarded = true; onboarded = true; applyMode() }

    /**
     * Usage butuh PollService; Shizuku butuh ShizukuService (sengaja dijalankan walau Shizuku belum siap,
     * supaya tersambung otomatis begitu Shizuku dinyalakan); Accessibility tidak butuh apa pun.
     */
    fun applyMode() {
        val poll = Intent(ctx, PollService::class.java)
        val shz = Intent(ctx, ShizukuService::class.java)
        runCatching {
            if (onboarded && mode == Prefs.MODE_USAGE && usageOn) ctx.startForegroundService(poll) else ctx.stopService(poll)
        }
        runCatching {
            if (onboarded && mode == Prefs.MODE_SHIZUKU) ctx.startForegroundService(shz) else ctx.stopService(shz)
        }
    }

    private fun isA11yOn(): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(ctx, AppDetectorService::class.java)
        return s.split(':').any { it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true) }
    }

    private fun isUsageOn(): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }
}

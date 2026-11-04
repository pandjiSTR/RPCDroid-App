package dev.rpcdroid.nr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Status Shizuku + aksi yang dibutuhkan UI. Tidak menyimpan state sendiri. */
object ShizukuBridge {
    const val PKG = "moe.shizuku.privileged.api"
    const val DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
    private const val REQ_CODE = 1001

    enum class Status { NOT_INSTALLED, NOT_RUNNING, UNSUPPORTED, NEED_PERMISSION, READY }

    fun status(ctx: Context): Status = try {
        if (Shizuku.pingBinder()) when {
            Shizuku.isPreV11() -> Status.UNSUPPORTED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.READY
            else -> Status.NEED_PERMISSION
        } else if (installed(ctx)) Status.NOT_RUNNING else Status.NOT_INSTALLED
    } catch (e: Throwable) { Status.NOT_RUNNING }

    private fun installed(ctx: Context) =
        try { ctx.packageManager.getPackageInfo(PKG, 0); true } catch (e: Exception) { false }

    fun requestPermission() { runCatching { Shizuku.requestPermission(REQ_CODE) } }

    fun launchIntent(ctx: Context): Intent? = ctx.packageManager.getLaunchIntentForPackage(PKG)
}

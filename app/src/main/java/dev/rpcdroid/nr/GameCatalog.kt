package dev.rpcdroid.nr

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** gamelist.txt (478 package) + games.json (nama, app_id Discord, icon) dari RPCDroid. */
class GameCatalog(private val ctx: Context, private val prefs: Prefs) {
    data class Game(val pkg: String, val name: String, val appId: String, val icon: String?)
    data class AppEntry(val pkg: String, val name: String)

    private val known: JSONObject = ctx.assets.open("games.json").bufferedReader().use { JSONObject(it.readText()) }
    private val packages: Set<String> = HashSet<String>().apply {
        ctx.assets.open("gamelist.txt").bufferedReader().forEachLine { l -> l.trim().takeIf { it.isNotEmpty() }?.let(::add) }
        known.keys().forEach(::add)
    }
    private val cache = HashMap<String, Game>()

    /** O(1). Dipanggil di tiap pergantian window, jadi harus murah. */
    @Synchronized
    fun lookup(pkg: String): Game? {
        if (pkg !in packages && pkg !in prefs.custom) return null
        return cache.getOrPut(pkg) {
            val k = known.optJSONObject(pkg)
            Game(
                pkg,
                k?.optString("name")?.ifEmpty { null } ?: label(pkg) ?: pkg,
                k?.optString("appId")?.ifEmpty { null } ?: FALLBACK_APP_ID,
                k?.optString("icon")?.ifEmpty { null },
            )
        }
    }

    /** Game dari katalog yang terpasang di device (untuk layar daftar). */
    fun installed(): List<Game> {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.distinct()
            .mapNotNull { lookup(it) }.sortedBy { it.name.lowercase() }
    }

    fun isBuiltin(pkg: String) = pkg in packages
    fun isKnown(pkg: String) = pkg in packages || pkg in prefs.custom

    /** Semua app ber-launcher (untuk dialog "Tambah app"), tanpa app ini sendiri. */
    fun launchable(): List<AppEntry> {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }.sortedBy { it.name.lowercase() }
    }

    private fun label(pkg: String): String? = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { null }

    companion object { const val FALLBACK_APP_ID = "962990036020756480" }
}

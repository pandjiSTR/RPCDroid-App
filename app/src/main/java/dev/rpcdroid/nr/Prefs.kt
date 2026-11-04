package dev.rpcdroid.nr

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("rpcnr", Context.MODE_PRIVATE)

    var token: String?
        get() = sp.getString("tok", null)?.let { Vault.decrypt(it) }
        set(v) {
            val clean = v?.trim()?.trim('"', '\'', ' ', '\n')
            sp.edit().apply { if (clean.isNullOrEmpty()) remove("tok") else putString("tok", Vault.encrypt(clean)) }.apply()
        }

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) { sp.edit().putBoolean("enabled", v).commit() }

    /** "a11y" (default, event-driven), "usage" (polling UsageStats), atau "shizuku" (event log via Shizuku). */
    var mode: String
        get() = sp.getString("mode", MODE_A11Y) ?: MODE_A11Y
        set(v) = sp.edit().putString("mode", v).apply()

    /** Jeda sebelum presence dihapus setelah game tidak lagi di layar depan. */
    var lingerSec: Int
        get() = sp.getInt("linger", 40)
        set(v) = sp.edit().putInt("linger", v).apply()

    var disabled: Set<String>
        get() = sp.getStringSet("disabled", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("disabled", HashSet(v)).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    /** Package app buatan pengguna (di luar katalog bawaan). */
    var custom: Set<String>
        get() = sp.getStringSet("custom", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("custom", HashSet(v)).apply()

    fun iconCache(pkg: String): String? = sp.getString("icon_$pkg", null)
    fun putIconCache(pkg: String, mp: String) = sp.edit().putString("icon_$pkg", mp).apply()

    /** Mode Shizuku: batas waktu game di latar belakang sebelum status dihapus (menit). Game mati = langsung dihapus. */
    var bgTimeoutMin: Int
        get() = sp.getInt("bg_min", 15)
        set(v) = sp.edit().putInt("bg_min", v).apply()

    companion object { const val MODE_A11Y = "a11y"; const val MODE_USAGE = "usage"; const val MODE_SHIZUKU = "shizuku" }
}

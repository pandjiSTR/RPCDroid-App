package dev.rpcdroid.nr

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * Login lewat halaman resmi discord.com di WebView, lalu token diambil dari localStorage via iframe
 * (klien web menghapus localStorage.token di window utama, tapi iframe baru masih melihatnya).
 * Cara ini rapuh terhadap perubahan klien web; jalur paste manual di MainActivity tetap tersedia.
 */
class LoginActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var wv: WebView
    private var tries = 0

    private val js = "(function(){try{var f=document.createElement('iframe');document.body.appendChild(f);" +
        "var t=f.contentWindow.localStorage.getItem('token');f.remove();return t?JSON.parse(t):''}catch(e){return ''}})()"

    private val poll = object : Runnable {
        override fun run() {
            if (++tries > 120) { finish(); return }                 // ±3 menit
            wv.evaluateJavascript(js) { r ->
                val t = r?.trim('"').orEmpty()
                if (t.length > 30 && t != "null") {
                    Prefs(this@LoginActivity).token = t
                    CookieManager.getInstance().removeAllCookies(null)   // jangan sisakan sesi di WebView
                    WebStorage.getInstance().deleteAllData()
                    PresenceEngine.onConfigChanged()
                    Toast.makeText(this@LoginActivity, "Token tersimpan", Toast.LENGTH_SHORT).show()
                    finish()
                } else h.postDelayed(this, 1500)
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        wv = WebView(this).also { setContentView(it) }
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                if (tries == 0) h.post(poll)
            }
        }
        wv.loadUrl("https://discord.com/login")
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); wv.destroy(); super.onDestroy() }
}

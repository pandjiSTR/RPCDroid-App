package dev.rpcdroid.nr

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class Presence(val name: String, val startMs: Long, val appId: String, val largeImage: String?)

/**
 * Discord Gateway v10 (op 10 Hello, 2 Identify, 6 Resume, 1/11 Heartbeat, 3 Presence Update).
 * Port dari gateway.cpp + websocket.c, tapi TLS/WebSocket diurus OkHttp.
 *
 * Semua state HANYA disentuh dari [exec] (single thread milik engine), jadi tidak butuh lock.
 * Callback OkHttp cuma melempar pekerjaan ke [exec] dan dibuang kalau generasinya sudah basi.
 */
class DiscordGateway(
    private val token: String,
    private val http: OkHttpClient,
    private val exec: ScheduledExecutorService,
    private val events: Events,
) {
    interface Events {
        fun onReady(user: String)
        fun onFatal(code: Int, reason: String)
        fun onState(text: String)
    }

    private var ws: WebSocket? = null
    private var gen = 0
    private var seq: Int? = null
    private var sessionId: String? = null
    private var resumeUrl: String? = null
    private var hbInterval = 0L
    private var hbAcked = true
    private var hbTask: ScheduledFuture<*>? = null
    private var retryTask: ScheduledFuture<*>? = null
    private var attempt = 0
    private var closed = false
    private var ready = false
    private var desired: Presence? = null

    fun open() { closed = false; connect() }

    /** Menutup socket dengan 1000: sesi Discord berakhir, presence otomatis hilang. */
    fun close() {
        closed = true
        hbTask?.cancel(false); retryTask?.cancel(false)
        gen++                       // buang callback socket lama
        ws?.close(1000, "bye"); ws = null
        ready = false
    }

    fun setPresence(p: Presence?) { desired = p; if (ready) sendPresence() }

    // ── koneksi ─────────────────────────────────────────────────────────────

    private fun connect() {
        val myGen = ++gen
        val base = resumeUrl ?: "wss://gateway.discord.gg"
        val req = Request.Builder().url("$base/?v=10&encoding=json").header("User-Agent", UA).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                exec.execute { if (myGen == gen) safe { handle(text) } }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                exec.execute { if (myGen == gen) onDisconnect(code, reason) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                exec.execute { if (myGen == gen) onDisconnect(-1, t.message ?: "network error") }
            }
        })
    }

    private fun onDisconnect(code: Int, reason: String) {
        hbTask?.cancel(false); ready = false
        if (closed) return
        if (code in FATAL) { closed = true; events.onFatal(code, reason); return }
        if (code == 4007 || code == 4009) { sessionId = null; seq = null; resumeUrl = null }
        attempt++
        val backoff = minOf(60_000L, 1000L shl minOf(attempt, 6))
        scheduleReconnect(backoff + (Math.random() * 1000).toLong())
    }

    private fun scheduleReconnect(delayMs: Long) {
        if (closed) return
        hbTask?.cancel(false); ready = false
        val old = ws; ws = null; gen++
        old?.close(4000, "reconnect")      // != 1000 supaya sesi tetap bisa di-resume
        retryTask?.cancel(false)
        retryTask = exec.schedule({ if (!closed) safe { connect() } }, delayMs, TimeUnit.MILLISECONDS)
        events.onState("Menyambung ulang…")
    }

    // ── protokol ────────────────────────────────────────────────────────────

    private fun handle(text: String) {
        val o = JSONObject(text)
        if (!o.isNull("s")) seq = o.getInt("s")
        when (o.getInt("op")) {
            10 -> {
                hbInterval = o.getJSONObject("d").getLong("heartbeat_interval")
                startHeartbeat()
                if (sessionId != null && seq != null) sendResume() else sendIdentify()
            }
            11 -> hbAcked = true
            1 -> sendHeartbeat()
            7 -> scheduleReconnect(500)
            9 -> {
                if (!o.optBoolean("d", false)) { sessionId = null; seq = null; resumeUrl = null }
                scheduleReconnect(1000 + (Math.random() * 4000).toLong())
            }
            0 -> dispatch(o.optString("t"), o.optJSONObject("d"))
        }
    }

    private fun dispatch(t: String, d: JSONObject?) {
        when (t) {
            "READY" -> {
                if (d == null) return
                sessionId = d.optString("session_id").ifEmpty { null }
                resumeUrl = d.optString("resume_gateway_url").ifEmpty { null }
                val u = d.optJSONObject("user")
                val name = u?.optString("username").orEmpty()
                val disc = u?.optString("discriminator", "0").orEmpty()
                ready = true; attempt = 0
                events.onReady(if (disc.isEmpty() || disc == "0") name else "$name#$disc")
                if (desired != null) sendPresence()
            }
            "RESUMED" -> { ready = true; attempt = 0; sendPresence() }
        }
    }

    private fun startHeartbeat() {
        hbTask?.cancel(false); hbAcked = true
        val first = (hbInterval * Math.random()).toLong()        // jitter sesuai dokumentasi
        hbTask = exec.scheduleWithFixedDelay({ safe { beat() } }, first, hbInterval, TimeUnit.MILLISECONDS)
    }

    private fun beat() {
        if (!hbAcked) { scheduleReconnect(500); return }          // koneksi zombie
        hbAcked = false
        sendHeartbeat()
    }

    private fun sendHeartbeat() {
        ws?.send(JSONObject().put("op", 1).put("d", seq ?: JSONObject.NULL).toString())
    }

    private fun sendIdentify() {
        val props = JSONObject().put("os", "Android").put("browser", "Discord Android").put("device", "Android")
        val d = JSONObject().put("token", token).put("capabilities", 65).put("compress", false).put("properties", props)
        ws?.send(JSONObject().put("op", 2).put("d", d).toString())
    }

    private fun sendResume() {
        val d = JSONObject().put("token", token).put("session_id", sessionId).put("seq", seq)
        ws?.send(JSONObject().put("op", 6).put("d", d).toString())
    }

    private fun sendPresence() {
        val p = desired
        val acts = JSONArray()
        if (p != null) {
            val a = JSONObject()
                .put("name", p.name).put("type", 0).put("application_id", p.appId)
                .put("timestamps", JSONObject().put("start", p.startMs))
            // Discord menolak URL http(s) mentah; hanya "mp:..." atau asset key yang valid
            if (p.largeImage != null) a.put("assets", JSONObject().put("large_image", p.largeImage).put("large_text", p.name))
            acts.put(a)
        }
        val d = JSONObject().put("since", p?.startMs ?: 0L).put("activities", acts)
            .put("status", "online").put("afk", false)
        ws?.send(JSONObject().put("op", 3).put("d", d).toString())
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (e: Exception) { android.util.Log.w("RPCNR", "gateway: ${e.javaClass.simpleName}") }
    }

    companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        /** 4004 auth gagal, 4010-4014 konfigurasi tidak valid: reconnect tidak ada gunanya. */
        private val FATAL = setOf(4004, 4010, 4011, 4012, 4013, 4014)
    }
}

package dev.rpcdroid.nr

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ring buffer di memori (tidak disimpan ke disk, tidak berisi token). Untuk diagnostik di Pengaturan. */
object DebugLog {
    private const val MAX = 300
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun d(tag: String, msg: String) {
        if (buf.size >= MAX) buf.removeFirst()
        buf.addLast("${fmt.format(Date())} $tag $msg")
    }

    /** Terbaru di atas. */
    @Synchronized fun dump(): String = buf.asReversed().joinToString("\n")

    @Synchronized fun clear() = buf.clear()
}

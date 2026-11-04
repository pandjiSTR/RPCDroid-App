package dev.rpcdroid.nr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/** Layar mati -> mulai jeda linger. Unlock -> lanjutkan game terakhir. */
object ScreenEvents {
    fun register(ctx: Context, extraOn: (() -> Unit)? = null, extraOff: (() -> Unit)? = null): BroadcastReceiver {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> { PresenceEngine.onScreenOff(); extraOff?.invoke() }
                    Intent.ACTION_USER_PRESENT -> { PresenceEngine.onUserPresent(); extraOn?.invoke() }
                }
            }
        }
        val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else ctx.registerReceiver(r, f)
        return r
    }
}

package dev.rpcdroid.nr

import android.app.Application

class App : Application() {
    override fun onCreate() { super.onCreate(); PresenceEngine.init(this) }
}

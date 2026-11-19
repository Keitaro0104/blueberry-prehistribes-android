package com.keitaro0104.blueberryprehistribes.android

import android.app.Application

/**
 * Installs the crash logger before any activity starts, so even a crash
 * inside the launcher activity itself is captured.
 */
class TribesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

package com.keitaro0104.blueberryprehistribes.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.keitaro0104.blueberryprehistribes.TribesGame

/** Android entry point. Immersive mode so the game uses the whole screen. */
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        CrashLog.log("Launcher onCreate")
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
        }
        CrashLog.log("before initialize")
        initialize(TribesGame(logger = { CrashLog.log(it) }), config)
        CrashLog.log("after initialize")
    }

    override fun onDestroy() {
        CrashLog.cleanExit(this)
        super.onDestroy()
    }
}

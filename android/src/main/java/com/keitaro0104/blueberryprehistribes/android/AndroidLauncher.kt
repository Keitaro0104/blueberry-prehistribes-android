package com.keitaro0104.blueberryprehistribes.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.keitaro0104.blueberryprehistribes.TribesGame

/** Android entry point. Immersive mode so the game uses the whole screen. */
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
        }
        initialize(TribesGame(), config)
    }
}

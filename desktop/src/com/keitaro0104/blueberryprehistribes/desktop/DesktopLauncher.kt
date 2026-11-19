package com.keitaro0104.blueberryprehistribes.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.keitaro0104.blueberryprehistribes.TribesGame

/**
 * Desktop entry point (LWJGL3). Used for local development and for the
 * CI smoke test: `./gradlew :desktop:run --args="--smoke"` boots the game
 * and exits after a few seconds, proving the game loads and renders.
 */
object DesktopLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val smoke = args.contains("--smoke")
        if (smoke) {
            // The application loop blocks this thread, so schedule the exit first.
            Thread {
                try {
                    Thread.sleep(6000)
                } catch (_: InterruptedException) {
                    // ignore — exiting anyway
                }
                Gdx.app?.postRunnable { Gdx.app.exit() }
            }.apply { isDaemon = true }.start()
        }

        val config = Lwjgl3ApplicationConfiguration().apply {
            setTitle("Prehistoric Tribes — M1")
            setWindowedMode(880, 520)
            setForegroundFPS(60)
        }
        Lwjgl3Application(TribesGame(), config)
    }
}

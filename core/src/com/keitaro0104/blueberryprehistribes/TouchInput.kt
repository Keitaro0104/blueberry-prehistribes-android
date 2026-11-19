package com.keitaro0104.blueberryprehistribes

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.input.GestureDetector

/**
 * Translates touch gestures into camera movement (plan §3).
 * There is no D-pad cursor in this port at all — the finger is the pointer.
 *
 * - One-finger drag pans the camera.
 * - Pinch zooms 0.5x–3x.
 * - Double-tap centres the camera on the player's tribe (player_start).
 * - A tap is a tap only if it stays within ~12 dp and ~150 ms; beyond that
 *   it is a pan and the tap is cancelled (handled by GestureDetector).
 */
class TouchInput(private val game: TribesGame) : GestureDetector.GestureListener {

    companion object {
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 3.0f
    }

    private var pinchBaseZoom = 1f
    private var lastInitialDistance = 0f

    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean = true

    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        if (count == 2) {
            game.centerOnPlayerStart()
            return true
        }
        // Single tap: selection is M2 work. M1 does nothing with it.
        return false
    }

    override fun longPress(x: Float, y: Float): Boolean = false

    override fun fling(velocityX: Float, velocityY: Float, button: Int): Boolean = false

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        val camera = game.camera
        val worldPerPixelX = camera.viewportWidth * camera.zoom / Gdx.graphics.width
        val worldPerPixelY = camera.viewportHeight * camera.zoom / Gdx.graphics.height
        camera.translate(-deltaX * worldPerPixelX, deltaY * worldPerPixelY)
        game.clampCamera()
        return true
    }

    override fun panStop(x: Float, y: Float, pointer: Int, button: Int): Boolean = false

    override fun zoom(initialDistance: Float, distance: Float): Boolean {
        if (distance <= 0f) return false
        // A new pinch gesture starts when the initial distance changes.
        if (initialDistance != lastInitialDistance) {
            pinchBaseZoom = game.camera.zoom
            lastInitialDistance = initialDistance
        }
        val newZoom = (pinchBaseZoom * initialDistance / distance).coerceIn(MIN_ZOOM, MAX_ZOOM)
        game.camera.zoom = newZoom
        game.clampCamera()
        return true
    }

    override fun pinch(
        initialPointer1: com.badlogic.gdx.math.Vector2,
        initialPointer2: com.badlogic.gdx.math.Vector2,
        pointer1: com.badlogic.gdx.math.Vector2,
        pointer2: com.badlogic.gdx.math.Vector2
    ): Boolean = false

    override fun pinchStop() {}
}

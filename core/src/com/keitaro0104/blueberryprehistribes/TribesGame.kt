package com.keitaro0104.blueberryprehistribes

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.MathUtils
import kotlin.math.floor

/**
 * M1 — the game skeleton.
 *
 * - Fixed timestep loop at 20 ticks/second (50 ms/tick), like the original
 *   `f.run()`. An accumulator is used so a slow frame never fast-forwards
 *   the simulation.
 * - Renders the bundled Green Valley map from its JSON with placeholder
 *   coloured tiles only (proven tile classes — no invented terrain art).
 *   Real terrain art waits until the tile-ID → sprite mapping is proven.
 * - Camera: drag pans, pinch zooms 0.5x–3x, double-tap centres on the tribe.
 *   Default zoom shows ~11 tiles across, the original game's framing.
 * - HUD shell: resource bar with no live values yet (live values are M2).
 *
 * Known M1 limitations (see README): no fog of war yet (all tiles are
 * shown), no villagers/buildings/combat, no audio, no saves, no mods folder.
 */
class TribesGame : ApplicationAdapter() {

    companion object {
        /** Original tile size in px, reused as world units (MA-MAPS.md). */
        const val TILE_W = 22f
        const val TILE_H = 16f

        /** Fixed timestep: 50 ms per tick, 20 ticks per second. */
        const val TICK_SECONDS = 1f / 20f

        /** Tiles visible across the screen at default zoom (original framing). */
        const val TILES_ACROSS = 11f

        const val MAP_FILE = "mods/green-valley.json"
    }

    lateinit var map: GameMapData
        private set

    lateinit var camera: OrthographicCamera
        private set

    private lateinit var batch: SpriteBatch
    private lateinit var shapes: ShapeRenderer
    private lateinit var font: BitmapFont
    private lateinit var hudCamera: OrthographicCamera

    private var accumulator = 0f
    var tickCount = 0L
        private set

    override fun create() {
        map = MapLoader.load(Gdx.files.internal(MAP_FILE))
        Gdx.app.log("TribesGame", "Loaded map '${map.name}' ${map.width}x${map.height}")

        batch = SpriteBatch()
        shapes = ShapeRenderer()
        font = BitmapFont() // libGDX built-in default font; no asset needed
        font.color = Color.WHITE

        camera = OrthographicCamera(TILES_ACROSS * TILE_W, TILES_ACROSS * TILE_W)
        hudCamera = OrthographicCamera()
        resize(Gdx.graphics.width, Gdx.graphics.height)
        centerOnPlayerStart()

        Gdx.input.inputProcessor = GestureDetector(TouchInput(this))
    }

    override fun resize(width: Int, height: Int) {
        // Keep ~11 tiles across; the vertical count follows the aspect ratio.
        camera.viewportWidth = TILES_ACROSS * TILE_W
        camera.viewportHeight = TILES_ACROSS * TILE_W * height / width.toFloat()
        camera.update()
        hudCamera.setToOrtho(false, width.toFloat(), height.toFloat())
        hudCamera.update()
        clampCamera()
    }

    /** Centre the camera on the player's tribe (player_start). */
    fun centerOnPlayerStart() {
        camera.position.set(
            (map.playerStart.col + 0.5f) * TILE_W,
            (map.height - 1 - map.playerStart.row + 0.5f) * TILE_H,
            0f
        )
        clampCamera()
    }

    /** Keep the camera view inside the map (centred when the map is smaller than the view). */
    fun clampCamera() {
        val mapPixelW = map.width * TILE_W
        val mapPixelH = map.height * TILE_H
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f
        camera.position.x =
            if (halfW * 2f >= mapPixelW) mapPixelW / 2f
            else camera.position.x.coerceIn(halfW, mapPixelW - halfW)
        camera.position.y =
            if (halfH * 2f >= mapPixelH) mapPixelH / 2f
            else camera.position.y.coerceIn(halfH, mapPixelH - halfH)
        camera.update()
    }

    /** One simulation tick. M1 has no simulation yet — the counter proves the loop runs at 20 Hz. */
    private fun tick() {
        tickCount++
    }

    override fun render() {
        // Fixed timestep: never fast-forward after a slow frame.
        accumulator += Math.min(Gdx.graphics.deltaTime, 0.25f)
        while (accumulator >= TICK_SECONDS) {
            tick()
            accumulator -= TICK_SECONDS
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        drawMap()
        drawHud()
    }

    /** Draw only the tiles inside the camera window (plan §6 risk 5: cull to ~200 tiles). */
    private fun drawMap() {
        camera.update()
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f
        val left = camera.position.x - halfW
        val right = camera.position.x + halfW
        val bottom = camera.position.y - halfH
        val top = camera.position.y + halfH

        val colMin = MathUtils.clamp(floor(left / TILE_W).toInt(), 0, map.width - 1)
        val colMax = MathUtils.clamp(floor(right / TILE_W).toInt(), 0, map.width - 1)
        // World y is up; map row 0 is the top, so row = height - 1 - floor(worldY / TILE_H).
        val rowMax = MathUtils.clamp(map.height - 1 - floor(bottom / TILE_H).toInt(), 0, map.height - 1)
        val rowMin = MathUtils.clamp(map.height - 1 - floor(top / TILE_H).toInt(), 0, map.height - 1)

        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        for (row in rowMin..rowMax) {
            for (col in colMin..colMax) {
                val x = col * TILE_W
                val y = (map.height - 1 - row) * TILE_H
                shapes.color = tileColor(map.tileAt(row, col))
                shapes.rect(x, y, TILE_W, TILE_H)
            }
        }
        shapes.end()

        // Spawn markers: a ring + the spawn type number on top of the tile.
        batch.projectionMatrix = camera.combined
        batch.begin()
        for (spawn in map.spawns) {
            val x = spawn.col * TILE_W
            val y = (map.height - 1 - spawn.row) * TILE_H
            font.draw(batch, spawn.type.toString(), x + TILE_W / 2f - 3f, y + TILE_H / 2f + 5f)
        }
        batch.end()
    }

    /** Placeholder colours by proven tile class — not terrain art. */
    private fun tileColor(tileId: Int): Color = when (tileClassOf(tileId)) {
        TileClass.OPEN -> Color(0.23f, 0.55f, 0.28f, 1f) // open ground
        TileClass.OPEN_VARIANT -> Color(0.45f, 0.72f, 0.35f, 1f) // open-ground variant
        TileClass.BLOCKED -> Color(0.45f, 0.42f, 0.38f, 1f) // blocked obstacle
        TileClass.SPAWN -> Color(0.85f, 0.65f, 0.15f, 1f) // spawn marker base
        TileClass.UNKNOWN -> Color(1f, 0f, 1f, 1f) // unproven id — visible on purpose
    }

    /** HUD shell: top resource bar. Values are not live yet (M2). */
    private fun drawHud() {
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        val barH = 30f

        shapes.projectionMatrix = hudCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.65f)
        shapes.rect(0f, h - barH, w, barH)
        shapes.end()

        batch.projectionMatrix = hudCamera.combined
        batch.begin()
        font.draw(batch, "${map.name} - M1 placeholder", 8f, h - 10f)
        font.draw(batch, "FOOD 0   WOOD 0   STONE 0   BONES 0", w / 2f - 130f, h - 10f)
        batch.end()
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        font.dispose()
    }
}

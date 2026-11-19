package com.keitaro0104.blueberryprehistribes

/**
 * Map data as defined by mod format v0.1 (mods/format/MOD-FORMAT.md §5).
 * Plain Kotlin data classes — the original J2ME game stored this in
 * parallel byte arrays; on Android we use named fields instead.
 *
 * Tiles are stored row-major, top row first: tile at (row, col) is
 * tiles[row * width + col]. Row 0 is the top of the map.
 */

enum class TileClass {
    /** Walkable open ground (proven walkable ranges). */
    OPEN,
    /** Walkable open-ground variant (tile 3; base tiles <= 8 get +0..2 jitter at load in the original). */
    OPEN_VARIANT,
    /** Not walkable (proven blocked ranges). */
    BLOCKED,
    /** Spawn tiles 175-179 (types 1-5). What each type places is unproven. */
    SPAWN,
    /** Tile id outside every proven range — rendered visibly so bad data is obvious, never silently treated as ground. */
    UNKNOWN
}

/**
 * Classify a tile id using only the proven ranges from MA-MAPS.md.
 * Spawn is checked first because 175 sits at the edge of a blocked range.
 */
fun tileClassOf(id: Int): TileClass = when (id) {
    in 175..179 -> TileClass.SPAWN
    3 -> TileClass.OPEN_VARIANT
    in 0..5, in 9..28, in 76..114, in 128..134 -> TileClass.OPEN
    in 6..8, in 29..75, in 115..127, in 135..174, in 180..231 -> TileClass.BLOCKED
    else -> TileClass.UNKNOWN
}

data class SpawnPoint(val type: Int, val row: Int, val col: Int)

data class PlayerStart(val row: Int, val col: Int)

data class WinLose(
    val flags: Int,
    val winByDestroyingEnemyBuildings: Boolean,
    val loseIfKeyBuildingDestroyed: Boolean,
    val loseIfTribeWipedOut: Boolean
)

class GameMapData(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val tiles: IntArray,
    val spawns: List<SpawnPoint>,
    val playerStart: PlayerStart,
    val winLose: WinLose
) {
    init {
        require(width in 1..96) { "map width $width out of range 1..96" }
        require(height in 1..96) { "map height $height out of range 1..96" }
        require(tiles.size == width * height) {
            "tiles has ${tiles.size} entries, expected ${width * height}"
        }
    }

    fun tileAt(row: Int, col: Int): Int = tiles[row * width + col]

    fun tileClassAt(row: Int, col: Int): TileClass = tileClassOf(tileAt(row, col))

    fun isWalkable(row: Int, col: Int): Boolean =
        row in 0 until height && col in 0 until width &&
            tileClassAt(row, col) in setOf(TileClass.OPEN, TileClass.OPEN_VARIANT)
}

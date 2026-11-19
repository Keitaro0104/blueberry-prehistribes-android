package com.keitaro0104.blueberryprehistribes

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

/**
 * Loads a v0.1 map JSON file (mods/format/MOD-FORMAT.md §5) into [GameMapData].
 *
 * A bad mod must fail with one clear message and never crash the caller —
 * the game shows the message in the Mods list and keeps running (plan §4.5).
 * Every check below throws [IllegalArgumentException] with that message.
 */
object MapLoader {

    fun load(handle: FileHandle): GameMapData = load(handle.readString(), handle.path())

    fun load(jsonText: String, sourceName: String = "<json>"): GameMapData {
        val json = JsonReader().parse(jsonText)
        fun fail(msg: String): Nothing =
            throw IllegalArgumentException("$sourceName: $msg")

        val version = json.getString("format_version", null)
            ?: fail("missing required field 'format_version'")
        if (version != "0.1") fail("unsupported format_version '$version', expected '0.1'")

        val kind = json.getString("kind", null) ?: fail("missing required field 'kind'")
        if (kind != "map") fail("kind is '$kind', expected 'map'")

        val id = json.getString("id", null) ?: fail("missing required field 'id'")
        val name = json.getString("name", null) ?: fail("missing required field 'name'")
        val width = json.getInt("width", -1)
        val height = json.getInt("height", -1)
        if (width !in 1..96) fail("width $width out of range 1..96")
        if (height !in 1..96) fail("height $height out of range 1..96")

        val tilesNode = json.get("tiles") ?: fail("missing required field 'tiles'")
        if (tilesNode.size != height) {
            fail("tiles has ${tilesNode.size} rows, expected $height")
        }
        val tiles = IntArray(width * height)
        var rowIndex = 0
        for (row in tilesNode) {
            if (row.size != width) {
                fail("tiles row $rowIndex has ${row.size} columns, expected $width")
            }
            for (col in 0 until width) {
                val tile = row.getInt(col)
                if (tile !in 0..255) fail("tile at row $rowIndex col $col is $tile, expected 0..255")
                tiles[rowIndex * width + col] = tile
            }
            rowIndex++
        }

        val spawns = mutableListOf<SpawnPoint>()
        val spawnsNode = json.get("spawns")
        if (spawnsNode != null) {
            for (spawn in spawnsNode) {
                val type = spawn.getInt("type", -1)
                val sRow = spawn.getInt("row", -1)
                val sCol = spawn.getInt("col", -1)
                if (type !in 1..5) fail("spawn type $type out of range 1..5")
                if (sRow !in 0 until height || sCol !in 0 until width) {
                    fail("spawn type $type at row $sRow col $sCol is outside the map")
                }
                if (tiles[sRow * width + sCol] != 174 + type) {
                    fail("spawn type $type at row $sRow col $sCol sits on tile " +
                        "${tiles[sRow * width + sCol]}, expected ${174 + type}")
                }
                spawns.add(SpawnPoint(type, sRow, sCol))
            }
        }

        val startNode = json.get("player_start")
        val playerStart = if (startNode != null) {
            PlayerStart(startNode.getInt("row", -1), startNode.getInt("col", -1))
        } else {
            PlayerStart(0, 0)
        }
        if (playerStart.row !in 0 until height || playerStart.col !in 0 until width) {
            fail("player_start row ${playerStart.row} col ${playerStart.col} is outside the map")
        }
        if (tileClassOf(tiles[playerStart.row * width + playerStart.col]) == TileClass.BLOCKED) {
            fail("player_start row ${playerStart.row} col ${playerStart.col} is on a blocked tile")
        }

        val winNode = json.get("win_lose") ?: fail("missing required field 'win_lose'")
        val winLose = WinLose(
            flags = winNode.getInt("flags", 0),
            winByDestroyingEnemyBuildings = winNode.getBoolean("win_by_destroying_enemy_buildings", false),
            loseIfKeyBuildingDestroyed = winNode.getBoolean("lose_if_key_building_destroyed", false),
            loseIfTribeWipedOut = winNode.getBoolean("lose_if_tribe_wiped_out", true)
        )

        return GameMapData(id, name, width, height, tiles, spawns, playerStart, winLose)
    }
}

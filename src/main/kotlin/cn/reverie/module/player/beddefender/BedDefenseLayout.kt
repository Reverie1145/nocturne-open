package cn.reverie.module.player.beddefender

import kotlin.math.abs
import kotlin.math.max

/** Bed foot at (0, 0, 0), head at (0, 0, 1). Shared by placement and the GUI miniature. */
internal object BedDefenseLayout {
    data class Cell(val x: Int, val y: Int, val z: Int) {
        fun oriented(forwardX: Int, forwardZ: Int): Cell =
            Cell(x * forwardZ + z * forwardX, y, z * forwardZ - x * forwardX)
    }

    val roofShoulders = listOf(Cell(1, 1, 0), Cell(-1, 1, 0), Cell(1, 1, 1), Cell(-1, 1, 1), Cell(0, 1, -1), Cell(0, 1, 2))

    fun cells(layers: Int, topCover: Boolean, shoulder: Cell = roofShoulders.first()): List<Cell> = buildList {
        val depth = layers.coerceIn(1, 3)
        for (y in 0..depth) {
            val radius = depth - y
            for (x in -radius..radius) {
                for (z in -radius..(1 + radius)) {
                    // Distance to the two bed blocks forms nested diamond terraces, tapering by one block per level.
                    val distanceFromBed = abs(x) + max(0, max(-z, z - 1))
                    if (distanceFromBed > radius) continue
                    val aboveBed = x == 0 && z in 0..1
                    if (aboveBed && (y == 0 || !topCover)) continue
                    add(Cell(x, y, z))
                }
            }
        }
        // A one-layer roof has no non-bed neighbor. Build this shoulder from the side wall, then bridge over the bed.
        if (depth == 1 && topCover) add(shoulder.takeIf { it in roofShoulders } ?: roofShoulders.first())
    }
}

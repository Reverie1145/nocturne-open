package cn.reverie.gui.clickgui.beddefender

import cn.reverie.module.player.beddefender.BedDefenseLayout
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Perspective projection of cuboid geometry; independent of the current Minecraft world/camera. */
internal object BedDefensePreviewScene {
    data class Point(val x: Float, val y: Float)
    data class Color(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {
        fun shaded(light: Float, alpha: Float = a) = Color(
            (r * light).coerceIn(0f, 1f), (g * light).coerceIn(0f, 1f), (b * light).coerceIn(0f, 1f), alpha
        )
    }
    data class Face(val points: List<Point>, val fill: Color, val outline: Color, val depth: Double, val bed: Boolean)
    data class Line(val from: Point, val to: Point)
    data class Frame(val floor: List<Face>, val grid: List<Line>, val faces: List<Face>, val blockCount: Int)

    private data class V3(val x: Double, val y: Double, val z: Double) {
        operator fun minus(other: V3) = V3(x - other.x, y - other.y, z - other.z)
        fun dot(other: V3) = x * other.x + y * other.y + z * other.z
    }
    private data class Surface(val points: List<V3>, val color: Color, val floor: Boolean, val bed: Boolean)
    private data class RawPoint(val x: Double, val y: Double, val depth: Double)
    private data class Side(val indices: List<Int>, val normal: V3, val neighbor: BedDefenseLayout.Cell)

    private val sides = listOf(
        Side(listOf(0, 3, 2, 1), V3(0.0, 0.0, -1.0), BedDefenseLayout.Cell(0, 0, -1)),
        Side(listOf(4, 5, 6, 7), V3(0.0, 0.0, 1.0), BedDefenseLayout.Cell(0, 0, 1)),
        Side(listOf(0, 4, 7, 3), V3(-1.0, 0.0, 0.0), BedDefenseLayout.Cell(-1, 0, 0)),
        Side(listOf(1, 2, 6, 5), V3(1.0, 0.0, 0.0), BedDefenseLayout.Cell(1, 0, 0)),
        Side(listOf(0, 1, 5, 4), V3(0.0, -1.0, 0.0), BedDefenseLayout.Cell(0, -1, 0)),
        Side(listOf(3, 7, 6, 2), V3(0.0, 1.0, 0.0), BedDefenseLayout.Cell(0, 1, 0))
    )

    @JvmOverloads
    fun frame(
        layers: Int, topCover: Boolean, material: String,
        yawDegrees: Float, pitchDegrees: Float, xray: Boolean,
        width: Float, height: Float, customRgb: Int? = null, shoulder: BedDefenseLayout.Cell = BedDefenseLayout.roofShoulders.first()
    ): Frame {
        val cells = BedDefenseLayout.cells(layers, topCover, shoulder)
        val layerCount = layers.coerceIn(1, 3)
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.coerceIn(18f, 76f).toDouble())
        val target = V3(0.0, (layerCount + if (topCover) 1.0 else 0.0) * 0.45, 0.0)
        val towardCamera = V3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch))
        val camera = V3(target.x + towardCamera.x * DISTANCE, target.y + towardCamera.y * DISTANCE, target.z + towardCamera.z * DISTANCE)
        val right = V3(cos(yaw), 0.0, -sin(yaw))
        val up = V3(-sin(yaw) * sin(pitch), cos(pitch), -cos(yaw) * sin(pitch))
        val surfaces = ArrayList<Surface>()

        fun cuboid(
            minX: Double, minY: Double, minZ: Double,
            maxX: Double, maxY: Double, maxZ: Double,
            color: Color, floor: Boolean = false, bed: Boolean = false,
            cell: BedDefenseLayout.Cell? = null, occupied: Set<BedDefenseLayout.Cell> = emptySet()
        ) {
            val vertices = listOf(
                V3(minX, minY, minZ), V3(maxX, minY, minZ), V3(maxX, maxY, minZ), V3(minX, maxY, minZ),
                V3(minX, minY, maxZ), V3(maxX, minY, maxZ), V3(maxX, maxY, maxZ), V3(minX, maxY, maxZ)
            )
            for (side in sides) {
                if (cell != null && BedDefenseLayout.Cell(cell.x + side.neighbor.x, cell.y + side.neighbor.y, cell.z + side.neighbor.z) in occupied) continue
                val points = side.indices.map(vertices::get)
                val center = V3(points.sumOf { it.x } / 4.0, points.sumOf { it.y } / 4.0, points.sumOf { it.z } / 4.0)
                if (side.normal.dot(camera - center) <= 0.0) continue
                val light = (0.66 + 0.40 * max(0.0, side.normal.dot(V3(-0.42, 0.82, 0.38)))).toFloat()
                val alpha = if (cell != null && xray) {
                    // Fade the near side and central roof column to expose the bed without changing the build plan.
                    if (cell.x * towardCamera.x + (cell.z - 0.5) * towardCamera.z > -0.15 || (cell.x == 0 && cell.z in 0..1)) 0.19f else 0.55f
                } else color.a
                surfaces += Surface(points, color.shaded(light, alpha), floor, bed)
            }
        }

        val floorHalfX = layerCount + 0.86
        val floorHalfZ = layerCount + 1.36
        cuboid(-floorHalfX, -0.20, -floorHalfZ, floorHalfX, -0.01, floorHalfZ, Color(0.22f, 0.26f, 0.32f), floor = true)

        // Minecraft proportions: four wooden legs, frame, red mattress/blanket, and a raised white pillow.
        val wood = Color(0.59f, 0.37f, 0.21f)
        for (x in listOf(-0.43, 0.30)) for (z in listOf(-0.92, 0.79)) {
            cuboid(x, 0.0, z, x + 0.13, 0.26, z + 0.13, wood, bed = true)
        }
        cuboid(-0.50, 0.19, -1.0, 0.50, 0.30, 1.0, wood, bed = true)
        cuboid(-0.50, 0.30, -1.0, 0.50, 0.50, 1.0, Color(0.80f, 0.17f, 0.24f), bed = true)
        cuboid(-0.48, 0.50, -0.98, 0.48, 0.515, 0.43, Color(0.96f, 0.27f, 0.32f), bed = true)
        cuboid(-0.41, 0.51, 0.49, 0.41, 0.59, 0.90, Color(0.94f, 0.94f, 0.90f), bed = true)
        cuboid(-0.47, 0.515, -0.82, 0.47, 0.525, -0.72, Color(0.98f, 0.41f, 0.44f), bed = true)

        val occupied = cells.toSet()
        val baseColor = customRgb?.let { Color(((it shr 16) and 255) / 255f, ((it shr 8) and 255) / 255f, (it and 255) / 255f) } ?: materialColor(material)
        for (cell in cells) {
            val tint = 1f + 0.065f * cell.y
            cuboid(
                cell.x - 0.482, cell.y + 0.018, cell.z - 0.982,
                cell.x + 0.482, cell.y + 0.982, cell.z - 0.018,
                baseColor.shaded(tint), cell = cell, occupied = occupied
            )
        }

        fun raw(point: V3): RawPoint {
            val offset = point - target
            val depth = DISTANCE - offset.dot(towardCamera)
            return RawPoint(offset.dot(right) * DISTANCE / depth, -offset.dot(up) * DISTANCE / depth, depth)
        }

        val rawPoints = surfaces.flatMap { it.points }.map(::raw)
        val left = rawPoints.minOf { it.x }
        val rightEdge = rawPoints.maxOf { it.x }
        val top = rawPoints.minOf { it.y }
        val bottom = rawPoints.maxOf { it.y }
        val scale = min((width - 16f).coerceAtLeast(1f) / (rightEdge - left), (height - 16f).coerceAtLeast(1f) / (bottom - top))
        val centerX = (left + rightEdge) * 0.5
        val centerY = (top + bottom) * 0.5

        fun project(point: V3): Point {
            val projected = raw(point)
            return Point((width * 0.5 + (projected.x - centerX) * scale).toFloat(), (height * 0.5 + (projected.y - centerY) * scale).toFloat())
        }

        fun faces(floor: Boolean): List<Face> = surfaces.filter { it.floor == floor }.map { surface ->
            val color = surface.color
            Face(
                surface.points.map(::project), color,
                if (surface.bed) color.shaded(0.52f, 0.65f) else color.shaded(0.48f, if (xray && !floor) 0.50f else 0.78f),
                surface.points.sumOf { raw(it).depth } / surface.points.size, surface.bed
            )
        }.sortedByDescending { it.depth }

        val grid = buildList {
            for (x in (-layerCount - 1)..layerCount) {
                add(Line(project(V3(x + 0.5, 0.0, -floorHalfZ + 0.06)), project(V3(x + 0.5, 0.0, floorHalfZ - 0.06))))
            }
            for (z in (-layerCount - 1)..(layerCount + 1)) {
                add(Line(project(V3(-floorHalfX + 0.06, 0.0, z.toDouble())), project(V3(floorHalfX - 0.06, 0.0, z.toDouble()))))
            }
        }
        return Frame(faces(true), grid, faces(false), cells.size)
    }

    private fun materialColor(material: String): Color = when (material) {
        "Obsidian" -> Color(0.38f, 0.29f, 0.53f)
        "End Stone" -> Color(0.80f, 0.79f, 0.53f)
        "Wool" -> Color(0.50f, 0.76f, 0.80f)
        "Planks" -> Color(0.72f, 0.52f, 0.31f)
        else -> Color(0.48f, 0.65f, 0.72f)
    }

    private const val DISTANCE = 10.0
}

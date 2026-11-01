package cn.reverie.module.render

import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.draw.LineRenderer
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.render.Render3DUtil
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector2f
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
object BedESP : Module(
    name = "BedESP",
    description = "Highlight beds through walls",
    category = Category.RENDER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val mode = ModeSetting("Mode", "Bed highlight style", "3D Box", listOf("3D Box", "2D Box"))
    val range = NumberSetting("Range", "Maximum bed highlight distance", 64.0, 8.0, 128.0, 8.0)
    val fill = BooleanSetting("Fill", "Draw a translucent fill inside the projected bed box", true)
    val distance = BooleanSetting("Distance", "Show the distance to each bed", true)

    private val mc: Minecraft get() = Minecraft.getInstance()
    private var cachedLevel: Any? = null
    private var lastScanTime = 0L
    private var cachedBeds: List<BedTarget> = emptyList()

    init {
        listen(Render2DEvent::class.java) { onRender2D() }
    }

    override val suffix: String
        get() = "${mode.value} ${range.getInt()}b"

    override fun onDisable() {
        cachedLevel = null
        cachedBeds = emptyList()
        lastScanTime = 0L
    }

    private fun onRender2D() {
        if (!enabled) return

        val player = mc.player ?: return
        val level = mc.level ?: return
        if (ResolutionHelper.scaledWidth <= 0f || ResolutionHelper.scaledHeight <= 0f) return

        val now = System.currentTimeMillis()
        if (cachedLevel !== level || now - lastScanTime >= SCAN_INTERVAL_MS) {
            cachedBeds = scanBeds(level, player)
            cachedLevel = level
            lastScanTime = now
        }

        val maxDistanceSq = range.getDouble() * range.getDouble()
        for (bed in cachedBeds) {
            val currentChunk = level.chunkSource.getChunkNow(bed.footPos.x shr 4, bed.footPos.z shr 4) ?: continue
            val state = currentChunk.getBlockState(bed.footPos)
            if (state.block !is BedBlock || state.getValue(BedBlock.PART) != BedPart.FOOT) continue

            val bedCenterX = bed.footPos.x + 0.5
            val bedCenterY = bed.footPos.y + 0.25
            val bedCenterZ = bed.footPos.z + 0.5
            if (player.position().distanceToSqr(bedCenterX, bedCenterY, bedCenterZ) > maxDistanceSq) continue
            renderBed(bed, player.position().distanceTo(Vec3(bedCenterX, bedCenterY, bedCenterZ)).toFloat())
        }
    }

    private fun scanBeds(level: net.minecraft.client.multiplayer.ClientLevel, player: net.minecraft.client.player.LocalPlayer): List<BedTarget> {
        val radius = range.getInt()
        val center = player.blockPosition()
        val minX = center.x - radius
        val maxX = center.x + radius
        val minY = max(level.minY, center.y - radius)
        val maxY = min(level.maxY - 1, center.y + radius)
        val minZ = center.z - radius
        val maxZ = center.z + radius
        val minChunkX = minX shr 4
        val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4
        val maxChunkZ = maxZ shr 4
        val radiusSq = radius.toDouble() * radius
        val beds = ArrayList<BedTarget>()
        val playerPosition = player.position()

        // getChunkNow keeps the visual scan limited to chunks already present on the client.
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                val chunk = level.chunkSource.getChunkNow(chunkX, chunkZ) ?: continue
                val chunkBaseX = chunkX shl 4
                val chunkBaseZ = chunkZ shl 4
                val sections = chunk.sections

                for (sectionIndex in sections.indices) {
                    val section = sections[sectionIndex]
                    if (!section.maybeHas { it.block is BedBlock }) continue

                    val sectionY = level.getSectionYFromSectionIndex(sectionIndex)
                    val sectionBaseY = sectionY shl 4
                    val startY = max(minY, sectionBaseY)
                    val endY = min(maxY, sectionBaseY + 15)
                    if (startY > endY) continue

                    for (localX in 0..15) {
                        val x = chunkBaseX + localX
                        if (x !in minX..maxX) continue
                        for (localZ in 0..15) {
                            val z = chunkBaseZ + localZ
                            if (z !in minZ..maxZ) continue
                            for (y in startY..endY) {
                                if (playerPosition.distanceToSqr(x + 0.5, y + 0.25, z + 0.5) > radiusSq) continue
                                val state = section.getBlockState(localX, y - sectionBaseY, localZ)
                                val bedBlock = state.block as? BedBlock ?: continue
                                if (state.getValue(BedBlock.PART) != BedPart.FOOT) continue

                                val footPos = BlockPos(x, y, z)
                                val head = footPos.relative(state.getValue(HorizontalDirectionalBlock.FACING))
                                val color = fromRgb(bedBlock.color.textureDiffuseColor).lighten(0.12f)
                                beds.add(
                                    BedTarget(
                                        footPos = footPos,
                                        box = AABB(
                                            min(x, head.x).toDouble(), y.toDouble(), min(z, head.z).toDouble(),
                                            max(x, head.x) + 1.0, y + BED_HEIGHT, max(z, head.z) + 1.0
                                        ),
                                        color = color
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return beds
    }

    private fun renderBed(bed: BedTarget, distanceMeters: Float) {
        val sw = ResolutionHelper.scaledWidth
        val sh = ResolutionHelper.scaledHeight
        val corners = arrayOf(
            doubleArrayOf(bed.box.minX, bed.box.minY, bed.box.minZ),
            doubleArrayOf(bed.box.minX, bed.box.minY, bed.box.maxZ),
            doubleArrayOf(bed.box.minX, bed.box.maxY, bed.box.minZ),
            doubleArrayOf(bed.box.minX, bed.box.maxY, bed.box.maxZ),
            doubleArrayOf(bed.box.maxX, bed.box.minY, bed.box.minZ),
            doubleArrayOf(bed.box.maxX, bed.box.minY, bed.box.maxZ),
            doubleArrayOf(bed.box.maxX, bed.box.maxY, bed.box.minZ),
            doubleArrayOf(bed.box.maxX, bed.box.maxY, bed.box.maxZ)
        )
        val edges = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 5), intArrayOf(5, 4), intArrayOf(4, 0),
            intArrayOf(2, 3), intArrayOf(3, 7), intArrayOf(7, 6), intArrayOf(6, 2),
            intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(4, 6), intArrayOf(5, 7)
        )
        val clips = Array(8) { i -> Render3DUtil.worldToClip(corners[i][0], corners[i][1], corners[i][2]) }
        val screenPoints = ArrayList<Vector2f>(24)
        val visibleEdges = ArrayList<Pair<Vector2f, Vector2f>>(12)

        for (edge in edges) {
            val c1 = clips[edge[0]]
            val c2 = clips[edge[1]]
            val firstVisible = c1.w > NEAR_CLIP
            val secondVisible = c2.w > NEAR_CLIP
            if (firstVisible && secondVisible) {
                val p1 = Render3DUtil.clipToScreen(c1, sw, sh)
                val p2 = Render3DUtil.clipToScreen(c2, sw, sh)
                screenPoints.add(p1)
                screenPoints.add(p2)
                visibleEdges.add(p1 to p2)
            } else if (firstVisible || secondVisible) {
                val t = ((NEAR_CLIP - c1.w) / (c2.w - c1.w)).coerceIn(0f, 1f)
                val cut = Vector4f(
                    c1.x + t * (c2.x - c1.x),
                    c1.y + t * (c2.y - c1.y),
                    c1.z + t * (c2.z - c1.z),
                    NEAR_CLIP
                )
                val cutScreen = Render3DUtil.clipToScreen(cut, sw, sh)
                val visibleScreen = Render3DUtil.clipToScreen(if (firstVisible) c1 else c2, sw, sh)
                screenPoints.add(cutScreen)
                screenPoints.add(visibleScreen)
                visibleEdges.add(if (firstVisible) visibleScreen to cutScreen else cutScreen to visibleScreen)
            }
        }
        if (screenPoints.isEmpty()) return

        val minX = screenPoints.minOf { it.x }
        val maxX = screenPoints.maxOf { it.x }
        val minY = screenPoints.minOf { it.y }
        val maxY = screenPoints.maxOf { it.y }
        if (maxX < 0f || minX > sw || maxY < 0f || minY > sh) return

        val left = minX.coerceIn(0f, sw)
        val right = maxX.coerceIn(0f, sw)
        val top = minY.coerceIn(0f, sh)
        val bottom = maxY.coerceIn(0f, sh)
        if (right - left < 1f || bottom - top < 1f) return

        val globalAlpha = HUDModule.visualAlpha.getFloat()
        val fillColor = bed.color.withAlpha(0.13f * globalAlpha)
        val outlineColor = bed.color.withAlpha(0.96f * globalAlpha)
        if (fill.value) Draw2D.rect(left, top, right - left, bottom - top, fillColor)

        if (mode.isMode("3D Box")) {
            val shadow = NanoVGColor(0f, 0f, 0f, 0.72f * HUDModule.shadowStrength.getFloat())
            val ctx = Draw2D.context
            for ((p1, p2) in visibleEdges) {
                if (HUDModule.widgetShadow.value) LineRenderer.drawLine(ctx, p1.x, p1.y, p2.x, p2.y, shadow, 2.5f)
                LineRenderer.drawLine(ctx, p1.x, p1.y, p2.x, p2.y, outlineColor, 1.25f)
            }
        } else {
            if (HUDModule.widgetShadow.value) Draw2D.roundedOutline(left - 1f, top - 1f, right - left + 2f, bottom - top + 2f, 1f, NanoVGColor(0f, 0f, 0f, 0.72f * HUDModule.shadowStrength.getFloat()), 2f)
            Draw2D.roundedOutline(left, top, right - left, bottom - top, 1f, outlineColor, 1.25f)
        }

        if (distance.value) {
            HUDModule.withHudFont { RenderEngine.fontRenderer.drawCenteredString(
                "Bed ${distanceMeters.roundToInt()}b",
                (left + right) * 0.5f,
                (top - 10f).coerceAtLeast(1f),
                NanoVGColor.WHITE.multiplyAlpha(HUDModule.visualAlpha.getFloat()),
                8.5f,
                shadow = HUDModule.textShadow.value
            ) }
        }
    }

    private fun fromRgb(rgb: Int): NanoVGColor = NanoVGColor(
        ((rgb ushr 16) and 0xFF) / 255f,
        ((rgb ushr 8) and 0xFF) / 255f,
        (rgb and 0xFF) / 255f,
        1f
    )

    private data class BedTarget(val footPos: BlockPos, val box: AABB, val color: NanoVGColor)

    private const val BED_HEIGHT = 0.5625
    private const val NEAR_CLIP = 0.05f
    private const val SCAN_INTERVAL_MS = 900L
}

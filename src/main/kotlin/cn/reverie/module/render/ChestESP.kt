package cn.reverie.module.render

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ColorSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.draw.LineRenderer
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.render.Render3DUtil
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min
object ChestESP : Module("ChestESP", "Highlights loaded chests through blocks", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN) {
    val box = BooleanSetting("Box", "Draw the chest outline", true)
    val fill = BooleanSetting("Fill", "Draw a transparent fill", true)
    val lineWidth = NumberSetting("Line Width", "Chest outline thickness", 2.0, 1.0, 6.0, 0.5)
    val range = NumberSetting("Range", "Maximum scan distance in blocks", 96.0, 16.0, 192.0, 8.0)
    val updateDelay = NumberSetting("Update Delay", "Time between scans in milliseconds", 500.0, 100.0, 2000.0, 100.0)
    val chests = BooleanSetting("Chests", "Show regular, trapped, and copper chests", true)
    val enderChests = BooleanSetting("Ender Chests", "Show ender chests", true)
    val chestColor = ColorSetting("Chest Color", "Color for regular and trapped chests", NanoVGColor(1f, 0.725f, 0.255f, 0.86f))
    val enderChestColor = ColorSetting("Ender Chest Color", "Color for ender chests", NanoVGColor(0.686f, 0.314f, 1f, 0.86f))
    val openedColor = ColorSetting("Opened Color", "Color for chests opened by any player", NanoVGColor(1f, 0.27f, 0.20f, 0.9f))

    private val mc get() = Minecraft.getInstance()
    private val openedPositions = HashSet<BlockPos>()
    private var currentLevel: Any? = null
    private var lastScan = 0L
    private var targets: List<ChestTarget> = emptyList()

    init {
        listen(PacketEvent::class.java) { onPacket(it) }
        listen(Render2DEvent::class.java) { onRender() }
    }

    override fun onEnable() {
        targets = emptyList()
        lastScan = 0L
    }

    override fun onDisable() {
        targets = emptyList()
        lastScan = 0L
    }

    private fun onPacket(event: PacketEvent) {
        if (!enabled || !event.isReceive || mc.level == null) return
        val packet = event.packet as? ClientboundBlockEventPacket ?: return
        if (packet.getB0() != 1 || packet.getB1() != 1 || !isChest(packet.getBlock())) return
        openedPositions.add(packet.getPos().immutable())
    }

    private fun onRender() {
        if (!enabled) return
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (currentLevel !== level) {
            currentLevel = level
            openedPositions.clear()
            targets = emptyList()
            lastScan = 0L
        }

        val now = System.currentTimeMillis()
        if (now - lastScan >= updateDelay.getDouble().toLong()) {
            targets = scan(level, player.blockPosition())
            lastScan = now
        }

        val sw = ResolutionHelper.scaledWidth
        val sh = ResolutionHelper.scaledHeight
        if (sw <= 0f || sh <= 0f) return
        for (target in targets) {
            val pos = target.pos
            val chunk = level.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4) ?: continue
            if (chunk.getBlockState(pos).block != target.block) continue
            val color = when {
                openedPositions.contains(pos) -> openedColor.sampleColor()
                target.block == Blocks.ENDER_CHEST -> enderChestColor.sampleColor()
                else -> chestColor.sampleColor()
            }
            drawBox(target.box, color, sw, sh)
        }
    }

    private fun scan(level: net.minecraft.client.multiplayer.ClientLevel, center: BlockPos): List<ChestTarget> {
        val blockRange = range.getDouble()
        val rangeSq = blockRange * blockRange
        val chunkRange = ((blockRange + 15.0) / 16.0).toInt()
        val centerX = center.x shr 4
        val centerZ = center.z shr 4
        val result = ArrayList<ChestTarget>()
        for (chunkX in centerX - chunkRange..centerX + chunkRange) {
            for (chunkZ in centerZ - chunkRange..centerZ + chunkRange) {
                val chunk = level.chunkSource.getChunkNow(chunkX, chunkZ) ?: continue
                for (entity in chunk.blockEntities.values) {
                    val block = entity.blockState.block
                    if (!isEnabledChest(block)) continue
                    val pos = entity.blockPos.immutable()
                    if (pos.distToCenterSqr(mc.player!!.position()) > rangeSq) continue
                    result.add(ChestTarget(pos, block, AABB(pos).inflate(0.002)))
                }
            }
        }
        return result
    }

    private fun drawBox(bounds: AABB, color: NanoVGColor, sw: Float, sh: Float) {
        val corners = arrayOf(
            Vec3(bounds.minX, bounds.minY, bounds.minZ), Vec3(bounds.minX, bounds.minY, bounds.maxZ),
            Vec3(bounds.minX, bounds.maxY, bounds.minZ), Vec3(bounds.minX, bounds.maxY, bounds.maxZ),
            Vec3(bounds.maxX, bounds.minY, bounds.minZ), Vec3(bounds.maxX, bounds.minY, bounds.maxZ),
            Vec3(bounds.maxX, bounds.maxY, bounds.minZ), Vec3(bounds.maxX, bounds.maxY, bounds.maxZ)
        )
        val edges = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 5), intArrayOf(5, 4), intArrayOf(4, 0),
            intArrayOf(2, 3), intArrayOf(3, 7), intArrayOf(7, 6), intArrayOf(6, 2),
            intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(4, 6), intArrayOf(5, 7)
        )
        val clips = Array(8) { i -> Render3DUtil.worldToClip(corners[i].x, corners[i].y, corners[i].z) }
        val screenPoints = ArrayList<org.joml.Vector2f>(24)
        val visibleEdges = ArrayList<Pair<org.joml.Vector2f, org.joml.Vector2f>>(12)

        for (edge in edges) {
            val first = clips[edge[0]]
            val second = clips[edge[1]]
            val firstVisible = first.w > NEAR_CLIP
            val secondVisible = second.w > NEAR_CLIP
            if (firstVisible && secondVisible) {
                val p1 = Render3DUtil.clipToScreen(first, sw, sh)
                val p2 = Render3DUtil.clipToScreen(second, sw, sh)
                screenPoints.add(p1); screenPoints.add(p2); visibleEdges.add(p1 to p2)
            } else if (firstVisible || secondVisible) {
                val t = ((NEAR_CLIP - first.w) / (second.w - first.w)).coerceIn(0f, 1f)
                val cut = Vector4f(
                    first.x + t * (second.x - first.x), first.y + t * (second.y - first.y),
                    first.z + t * (second.z - first.z), NEAR_CLIP
                )
                val cutScreen = Render3DUtil.clipToScreen(cut, sw, sh)
                val visibleScreen = Render3DUtil.clipToScreen(if (firstVisible) first else second, sw, sh)
                screenPoints.add(cutScreen); screenPoints.add(visibleScreen)
                visibleEdges.add(if (firstVisible) visibleScreen to cutScreen else cutScreen to visibleScreen)
            }
        }
        if (screenPoints.isEmpty()) return

        val left = max(0f, screenPoints.minOf { it.x })
        val right = min(sw, screenPoints.maxOf { it.x })
        val top = max(0f, screenPoints.minOf { it.y })
        val bottom = min(sh, screenPoints.maxOf { it.y })
        if (right - left < 1f || bottom - top < 1f) return

        if (fill.value) Draw2D.rect(left, top, right - left, bottom - top, color.withAlpha((35f / 255f) * HUDModule.visualAlpha.getFloat()))
        if (box.value) {
            val context = Draw2D.context
            for ((p1, p2) in visibleEdges) {
                if (HUDModule.widgetShadow.value) LineRenderer.drawLine(context, p1.x, p1.y, p2.x, p2.y, NanoVGColor(0f, 0f, 0f, 0.55f * HUDModule.shadowStrength.getFloat()), lineWidth.getDouble().toFloat() + 1f)
                LineRenderer.drawLine(context, p1.x, p1.y, p2.x, p2.y, color.withAlpha(0.94f * HUDModule.visualAlpha.getFloat()), lineWidth.getDouble().toFloat())
            }
        }
    }

    private fun isEnabledChest(block: Block): Boolean = when {
        block == Blocks.ENDER_CHEST -> enderChests.value
        isChest(block) -> chests.value
        else -> false
    }

    private fun isChest(block: Block): Boolean =
        block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.ENDER_CHEST || isCopperChest(block)

    private fun isCopperChest(block: Block): Boolean = block.descriptionId.contains("copper_chest")

    private data class ChestTarget(val pos: BlockPos, val block: Block, val box: AABB)

    private const val NEAR_CLIP = 0.05f
}

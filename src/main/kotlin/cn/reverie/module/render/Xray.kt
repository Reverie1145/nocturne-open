package cn.reverie.module.render

import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.module.*
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.render.Render3DUtil
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

/** Highlights nearby loaded ores through walls without altering server block state. */
object Xray : Module("Xray", "Show nearby ores through walls", Category.RENDER) {
    val range = NumberSetting("Range", "Ore scan radius", 24.0, 8.0, 48.0, 4.0)
    val scanDelay = NumberSetting("Scan Delay", "Milliseconds between scans", 600.0, 200.0, 2000.0, 100.0)
    val diamonds = BooleanSetting("Diamonds", "Show diamond ore", true)
    val emeralds = BooleanSetting("Emeralds", "Show emerald ore", true)
    val gold = BooleanSetting("Gold", "Show gold ore", true)
    val iron = BooleanSetting("Iron", "Show iron ore", true)
    private val mc get() = Minecraft.getInstance()
    private var lastScan = 0L
    private var levelRef: Any? = null
    private var ores: List<Pair<BlockPos, NanoVGColor>> = emptyList()
    init { listen(Render2DEvent::class.java) { render() } }
    override fun onDisable() { ores = emptyList() }
    private fun render() {
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (levelRef !== level) { levelRef = level; ores = emptyList(); lastScan = 0L }
        val now = System.currentTimeMillis()
        if (now - lastScan >= scanDelay.value) {
            lastScan = now
            val center = player.blockPosition()
            val r = range.value.toInt()
            val found = ArrayList<Pair<BlockPos, NanoVGColor>>()
            for (pos in BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                if (player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) > r * r) continue
                if (level.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4) == null) continue
                val block = level.getBlockState(pos).block
                val color = when (block) {
                    Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE -> if (diamonds.value) NanoVGColor(0f, 1f, 1f, 1f) else null
                    Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE -> if (emeralds.value) NanoVGColor(0f, 1f, 0.3f, 1f) else null
                    Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE -> if (gold.value) NanoVGColor(1f, 0.8f, 0f, 1f) else null
                    Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE -> if (iron.value) NanoVGColor(1f, 0.65f, 0.4f, 1f) else null
                    else -> null
                }
                if (color != null) found.add(pos.immutable() to color)
            }
            ores = found
        }
        val sw = ResolutionHelper.scaledWidth
        val sh = ResolutionHelper.scaledHeight
        for ((pos, color) in ores) {
            val clip = Render3DUtil.worldToClip(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
            if (clip.w <= 0.05f) continue
            val screen = Render3DUtil.clipToScreen(clip, sw, sh)
            if (screen.x < 0 || screen.y < 0 || screen.x > sw || screen.y > sh) continue
            Draw2D.rect(screen.x - 2f, screen.y - 2f, 4f, 4f, color)
        }
    }
}

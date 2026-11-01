package cn.reverie.module.misc

import cn.reverie.app.event.input.MouseInputEvent
import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.gui.clickgui.ClickGUIModule
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.render.Render3DUtil
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.Optional
object GhostHand : Module("GhostHand", "Interact with containers through walls", Category.MISC, GLFW.GLFW_KEY_UNKNOWN) {
    val renderTags = BooleanSetting("Render Tags", "Show nearby container labels", true)
    private val mc get() = Minecraft.getInstance()
    private val containers: Set<Block> = setOf(Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.TRAPPED_CHEST, Blocks.SHULKER_BOX)

    init {
        listen(MouseInputEvent::class.java) { onMouse(it) }
        listen(Render2DEvent::class.java) { renderTags(it.partialTicks) }
    }

    private fun onMouse(event: MouseInputEvent) {
        if (!enabled || event.screen != null || event.action != GLFW.GLFW_PRESS || event.event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return
        val player = mc.player ?: return
        val hit = findContainerHit() ?: return
        val gameMode = mc.gameMode ?: return
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        event.cancel()
    }

    private fun findContainerHit(): BlockHitResult? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val eye = player.getEyePosition(1f)
        val end = eye.add(player.getViewVector(1f).scale(player.blockInteractionRange()))
        var best: BlockHitResult? = null
        var bestDistance = Double.MAX_VALUE
        for (be in loadedBlockEntities()) {
            if (!containers.contains(be.blockState.block)) continue
            val box = blockBox(be) ?: continue
            val hit: Optional<Vec3> = box.clip(eye, end)
            if (hit.isEmpty) continue
            val point = hit.get()
            val distance = point.distanceToSqr(eye)
            if (distance < bestDistance) {
                bestDistance = distance
                best = BlockHitResult(point, Direction.UP, be.blockPos, false)
            }
        }
        return best
    }

    private fun blockBox(be: BlockEntity): AABB? {
        if (be !is ChestBlockEntity) return AABB(be.blockPos)
        val state = be.blockState
        if (!state.hasProperty(ChestBlock.TYPE)) return null
        val type = state.getValue(ChestBlock.TYPE)
        if (type == ChestType.LEFT) return null
        var box = AABB(be.blockPos)
        if (type != ChestType.SINGLE) {
            val other = be.blockPos.relative(ChestBlock.getConnectedDirection(state))
            if (mc.level?.getBlockEntity(other) is ChestBlockEntity) box = box.minmax(AABB(other))
        }
        return box
    }

    private fun loadedBlockEntities(): List<BlockEntity> {
        val player = mc.player ?: return emptyList()
        val level = mc.level ?: return emptyList()
        val cx = player.blockPosition().x shr 4
        val cz = player.blockPosition().z shr 4
        val result = ArrayList<BlockEntity>()
        for (x in cx - 1..cx + 1) for (z in cz - 1..cz + 1) {
            val chunk = level.chunkSource.getChunkNow(x, z) ?: continue
            result.addAll(chunk.blockEntities.values)
        }
        return result
    }

    private fun renderTags(partialTicks: Float) {
        if (!enabled || !renderTags.value || mc.player == null || mc.level == null || ClickGUIModule.enabled) return
        val cam = Render3DUtil.lastCameraPos
        val reach = mc.player!!.blockInteractionRange()
        val font = RenderEngine.fontRenderer
        for (be in loadedBlockEntities()) {
            val block = be.blockState.block
            if (!containers.contains(block)) continue
            val pos = be.blockPos
            val x = pos.x + 0.5; val y = pos.y + 1.2; val z = pos.z + 0.5
            if (cam.distanceTo(Vec3(x, y, z)) > reach) continue
            val screen = Render3DUtil.projectToScreen(x, y, z) ?: continue
            val (label, color) = when (block) {
                Blocks.ENDER_CHEST -> "Ender Chest" to NanoVGColor(0.82f, 0.42f, 1f, 1f)
                Blocks.TRAPPED_CHEST -> "Trapped Chest" to NanoVGColor(1f, 0.35f, 0.3f, 1f)
                Blocks.SHULKER_BOX -> "Shulker Box" to NanoVGColor(0.78f, 0.42f, 1f, 1f)
                else -> "Chest" to NanoVGColor(1f, 0.86f, 0.35f, 1f)
            }
            HUDModule.withHudFont {
                font.drawCenteredString("[$label]", screen.x, screen.y, color.multiplyAlpha(HUDModule.visualAlpha.getFloat()), 8.5f, shadow = HUDModule.textShadow.value)
            }
        }
    }
}

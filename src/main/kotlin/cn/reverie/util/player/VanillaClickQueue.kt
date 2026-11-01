package cn.reverie.util.player

import cn.reverie.mixin.input.KeyMappingAccessor
import cn.reverie.mixin.input.MouseHandlerAccessor
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.input.MouseButtonInfo
import org.lwjgl.glfw.GLFW
import net.minecraft.world.phys.HitResult

/** Enqueue the same key-binding click counted by MouseHandler; Minecraft.handleKeybinds consumes it. */
object VanillaClickQueue {
    private var originalHit: HitResult? = null
    private var overridden = false

    /** Supply the silent-rotation hit only while vanilla consumes this tick's click. */
    fun overrideHit(mc: Minecraft, hit: HitResult) {
        if (!overridden) originalHit = mc.hitResult
        overridden = true
        mc.hitResult = hit
    }

    fun restoreHit(mc: Minecraft) {
        if (!overridden) return
        mc.hitResult = originalHit
        originalHit = null
        overridden = false
    }

    fun left(mc: Minecraft, count: Int) {
        click(mc, 0, count)
    }

    fun right(mc: Minecraft, count: Int) {
        click(mc, 1, count)
    }

    private fun click(mc: Minecraft, button: Int, count: Int) {
        if (count <= 0 || mc.gui.screen() != null || mc.gui.overlay() != null) return
        val mapping = if (button == 0) mc.options.keyAttack else mc.options.keyUse
        if (mapping.isDown) return // Never release a button the player is physically holding.
        val mouseKey = InputConstants.Type.MOUSE.getOrCreate(button)
        repeat(count.coerceIn(0, 8)) {
            if (mapping.matches(mouseKey)) {
                val mouse = mc.mouseHandler as MouseHandlerAccessor
                val event = MouseButtonInfo(button, 0)
                mouse.nocturneInvokeOnButton(mc.window.handle(), event, GLFW.GLFW_PRESS)
                mouse.nocturneInvokeOnButton(mc.window.handle(), event, GLFW.GLFW_RELEASE)
            } else {
                KeyMapping.click((mapping as KeyMappingAccessor).nocturneGetKey())
            }
        }
    }
}

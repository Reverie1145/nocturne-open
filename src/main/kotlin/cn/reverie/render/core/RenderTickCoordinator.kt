package cn.reverie.render.core

import cn.reverie.app.config.ConfigManager
import cn.reverie.render.animation.AnimationController
import cn.reverie.app.event.EventBus
import cn.reverie.app.event.render.RenderHudEvent
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL30C.*
internal object RenderTickCoordinator {
    fun onTick(engine: RenderEngine) {
        ConfigManager.onTick()
        if (!engine.isInitialized) return
        AnimationController.update()
        engine.hudRenderer.onTick()
    }

    fun onRenderHud(engine: RenderEngine, graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (!engine.isInitialized) return
        val partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false)
        if (Minecraft.getInstance().gui.screen() == null) {
            engine.hudRenderer.extractRenderState(graphics, deltaTracker)
        }
        EventBus.post(RenderHudEvent(graphics, partialTicks))
    }
}

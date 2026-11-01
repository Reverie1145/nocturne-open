package cn.reverie.app.event.render

import net.minecraft.client.gui.screens.Screen

data class Render2DEvent(val partialTicks: Float)
data class RenderHudEvent(val graphics: net.minecraft.client.gui.GuiGraphicsExtractor, val partialTicks: Float)
data class RenderScreenEvent(val screen: Screen, val mouseX: Float, val mouseY: Float, val partialTicks: Float)
data class ResolutionChangeEvent(val width: Int, val height: Int, val guiScale: Int)

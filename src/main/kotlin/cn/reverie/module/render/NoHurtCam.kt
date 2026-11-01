package cn.reverie.module.render

import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.module.Category
import cn.reverie.module.ColorSetting
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPaint
import cn.reverie.render.nanovg.NanoVGPath
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.system.MemoryStack
object NoHurtCam : Module(
    name = "NoHurtCam",
    description = "Disable hurt camera shake and render damage red border indicator",
    category = Category.RENDER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val style = ModeSetting("Style", "Red indicator appearance style", "Both", listOf("Border", "Vignette", "Both"))
    val borderWidth = NumberSetting("Border Width", "Width of screen border", 6.0, 1.0, 30.0, 1.0, { style.isMode("Border") || style.isMode("Both") })
    val vignetteFeather = NumberSetting("Vignette Feather", "Edge glow feather radius", 60.0, 10.0, 150.0, 5.0, { style.isMode("Vignette") || style.isMode("Both") })
    val color = ColorSetting("Color", "Damage indicator color", NanoVGColor(0.95f, 0.12f, 0.12f, 0.85f))

    init {
        listen(Render2DEvent::class.java) { onRender2D(it.partialTicks) }
    }

    override val suffix: String
        get() = style.value

    private fun onRender2D(partialTicks: Float) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!player.isAlive) return

        val hurtTime = player.hurtTime.toFloat()
        if (hurtTime <= 0f) return

        val hurtDuration = player.hurtDuration.toFloat().coerceAtLeast(1f)
        val progress = ((hurtTime - partialTicks) / hurtDuration).coerceIn(0f, 1f)
        if (progress <= 0f) return

        val sw = ResolutionHelper.scaledWidth
        val sh = ResolutionHelper.scaledHeight
        if (sw <= 0f || sh <= 0f) return

        val currentAlpha = progress * 0.65f * HUDModule.visualAlpha.getFloat()
        val baseColor = color.sampleColor()
        val renderColor = baseColor.withAlpha(currentAlpha)

        if (style.isMode("Vignette") || style.isMode("Both")) {
            MemoryStack.stackPush().use { stack ->
                val paint = NVGPaint.malloc(stack)
                NanoVGPaint.boxGradient(
                    Draw2D.context,
                    0f, 0f, sw, sh,
                    0f, vignetteFeather.getFloat(),
                    baseColor.withAlpha(0f),
                    renderColor,
                    paint
                )
                val path = NanoVGPath(Draw2D.context)
                path.begin()
                path.rect(0f, 0f, sw, sh)
                path.fill(paint)
            }
        }

        if (style.isMode("Border") || style.isMode("Both")) {
            val bw = borderWidth.getFloat()
            Draw2D.rect(0f, 0f, sw, bw, renderColor)
            Draw2D.rect(0f, sh - bw, sw, bw, renderColor)
            Draw2D.rect(0f, bw, bw, sh - 2f * bw, renderColor)
            Draw2D.rect(sw - bw, bw, bw, sh - 2f * bw, renderColor)
        }
    }
}

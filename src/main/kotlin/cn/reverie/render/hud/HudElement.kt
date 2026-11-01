package cn.reverie.render.hud

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.ui.UITheme
import kotlin.math.roundToInt

enum class HudAlignment {
    LEFT,
    RIGHT,
    CENTER
}

enum class HudHorizontalAnchor(val resizeFactor: Float) {
    LEFT(0f),
    CENTER(0.5f),
    RIGHT(1f)
}

enum class HudVerticalAnchor(val resizeFactor: Float) {
    TOP(0f),
    CENTER(0.5f),
    BOTTOM(1f)
}

abstract class HudElement(
    name: String,
    val defaultX: Float,
    val defaultY: Float,
    var width: Float,
    var height: Float,
    val defaultEnabled: Boolean = false,
    horizontalAnchor: HudHorizontalAnchor = HudHorizontalAnchor.LEFT,
    verticalAnchor: HudVerticalAnchor = HudVerticalAnchor.TOP
) : Module(name, "Customizable HUD widget", Category.RENDER) {

    val defaultHorizontalAnchor = horizontalAnchor
    val defaultVerticalAnchor = verticalAnchor
    var horizontalAnchor = horizontalAnchor
    var verticalAnchor = verticalAnchor

    init {
        enabled = defaultEnabled
    }

    val widgetScale = NumberSetting("Scale", "Widget size multiplier", 1.0, 0.5, 3.0, 0.05)
    val scale: Float get() = widgetScale.getFloat()

    val shadowEnabled: Boolean get() = HUDModule.widgetShadow.value

    val shouldBlur: Boolean get() = HUDModule.blur.value

    val bgOpacity: Float get() = HUDModule.bgOpacity.getFloat()
    val textShadow: Boolean get() = HUDModule.textShadow.value
    val isWidgetShadowEnabled: Boolean get() = HUDModule.widgetShadow.value
    val shadowStrength: Float get() = HUDModule.shadowStrength.getFloat()

    fun drawBackdrop(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        bgColor: NanoVGColor,
        blurRadius: Float = HUDModule.blurRadius.getFloat(),
        sampleRegion: Rect2f = Rect2f(x, y, width, height)
    ) {
        RenderEngine.backdropBlurRenderer.renderBackdrop(
            x, y, width, height, radius, bgColor, blurRadius,
            enableBlur = shouldBlur,
            backdropOpacity = bgColor.a,
            region = sampleRegion
        )
    }

    fun drawWidgetBackdrop(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        alphaMultiplier: Float = 1f,
        blurRadius: Float = HUDModule.blurRadius.getFloat()
    ) {
        val alpha = (bgOpacity * alphaMultiplier).coerceIn(0f, 1f)
        if (alpha <= 0.001f && !shouldBlur) return
        val bgColor = NanoVGColor(0f, 0f, 0f, alpha)
        drawBackdrop(x, y, width, height, radius, bgColor, blurRadius)
    }

    fun drawWidgetShadow(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        alpha: Float = 0.45f,
        blur: Float = 8f,
        colorOverride: NanoVGColor? = null
    ) {
        if (!HUDModule.widgetShadow.value) return
        val globalStrength = HUDModule.shadowStrength.getFloat()
        if (globalStrength <= 0.001f) return
        val baseColor = HUDModule.getShadowColor(colorOverride ?: getAccentColor())
        Draw2D.shadow(x, y, width, height, radius, blur, baseColor.withAlpha((alpha * globalStrength).coerceIn(0f, 1f)), 1f)
    }

    var x: Float = defaultX
    var y: Float = defaultY

    val bounds: Rect2f get() = Rect2f(x, y, width * scale, height * scale)
    val scaledWidth: Float get() = width * scale
    val scaledHeight: Float get() = height * scale

    var alignment: HudAlignment = HudAlignment.LEFT
    var theme: UITheme = UITheme.DARK

    open fun contains(px: Float, py: Float): Boolean {
        val s = scale.coerceAtLeast(0.01f)
        val lx = (px - x) / s
        val ly = (py - y) / s
        return lx >= 0f && lx <= width && ly >= 0f && ly <= height
    }

    var isHovered: Boolean = false
    var isDragging: Boolean = false
    var isSelected: Boolean = false
    var dragOffsetX: Float = 0f
    var dragOffsetY: Float = 0f

    val hoverAnim = FloatAnimation(0f, 0f, 160L)
    val selectAnim = FloatAnimation(0f, 0f, 180L)

    abstract fun render(partialTicks: Float, isEditing: Boolean = false)

    open fun extractRenderState(graphics: net.minecraft.client.gui.GuiGraphicsExtractor, partialTicks: Float, isEditing: Boolean = false) {}

    open fun onCanvasResized(widthDelta: Float, heightDelta: Float) {}

    open fun onTick() {
        hoverAnim.animateTo(if (isHovered || isDragging || isSelected) 1f else 0f)
        selectAnim.animateTo(if (isSelected) 1f else 0f)
    }

    fun getAccentColor(offset: Float = 0f): NanoVGColor = HUDModule.sampleColor(offset)

    open fun resetPosition() {
        x = defaultX
        y = defaultY
    }

    open fun renderEditBoundingBox(partialTicks: Float) {
        val anim = hoverAnim.value
        val selAnim = selectAnim.value
        val font: FontRenderer = RenderEngine.fontRenderer
        val accent = getAccentColor()

        val isHighlighted = isDragging || isSelected || isHovered
        if (anim <= 0.01f && selAnim <= 0.01f && !isHighlighted) {
            val dimAlpha = if (enabled) 0.08f else 0.04f
            Draw2D.roundedOutline(x, y, width, height, 5f, NanoVGColor(1f, 1f, 1f, dimAlpha), 1.0f)
            return
        }

        val outlineAlpha = if (isDragging) 0.8f else (0.22f + 0.38f * anim)
        val fillAlpha = if (isDragging) 0.08f else (0.02f + 0.03f * anim)
        val baseColor = NanoVGColor(1f, 1f, 1f, outlineAlpha)
        val finalOutlineColor = baseColor.lerp(accent, (selAnim * 0.9f + if (isDragging) 0.5f else 0f).coerceIn(0f, 1f))

        if (selAnim > 0.01f || isDragging) {
            val glowAlpha = (0.28f * selAnim + if (isDragging) 0.35f else 0f).coerceIn(0f, 0.65f)
            Draw2D.shadow(x, y, width, height, 5f, 10f, accent.withAlpha(glowAlpha), 1f)
        } else if (anim > 0.05f) {
            Draw2D.shadow(x, y, width, height, 5f, 6f, NanoVGColor(0f, 0f, 0f, 0.30f * anim), 1f)
        }

        Draw2D.roundedRect(x, y, width, height, 5f, NanoVGColor(1f, 1f, 1f, fillAlpha))
        Draw2D.roundedOutline(x, y, width, height, 5f, finalOutlineColor, if (isSelected || isDragging) 1.5f else 1.0f)

        // Clean, compact label tag above widget
        val scalePercent = (scale * 100f).roundToInt()
        val label = when {
            !enabled -> "$name (OFF)"
            scale != 1.0f -> "$name · $scalePercent%"
            else -> name
        }

        val textW = font.getStringWidth(label, 8.5f)
        val badgeW = textW + 12f
        val badgeH = 15f
        val badgeX = x + 2f
        val badgeY = (y - badgeH - 3f).coerceAtLeast(2f)

        val badgeBg = NanoVGColor(0.04f, 0.05f, 0.07f, 0.92f)
        Draw2D.shadow(badgeX, badgeY, badgeW, badgeH, 3.5f, 6f, NanoVGColor(0f, 0f, 0f, 0.45f), 1f)
        Draw2D.roundedRect(badgeX, badgeY, badgeW, badgeH, 3.5f, badgeBg)
        Draw2D.roundedOutline(badgeX, badgeY, badgeW, badgeH, 3.5f, finalOutlineColor.withAlpha(0.6f), 0.8f)

        val textColor = when {
            isDragging -> NanoVGColor.WHITE
            isSelected -> NanoVGColor.WHITE.lerp(accent, 0.35f)
            !enabled -> NanoVGColor(0.6f, 0.65f, 0.72f, 0.85f)
            else -> NanoVGColor(0.90f, 0.92f, 0.96f, 0.95f)
        }

        font.drawString(
            label,
            badgeX + badgeW * 0.5f,
            badgeY + badgeH * 0.5f,
            textColor,
            8.5f,
            TextAlignment.MIDDLE_CENTER
        )
    }
}

package cn.reverie.render.ui.notification
import cn.reverie.module.Category
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.nanovg.NanoVGColor
import kotlin.math.abs
enum class ToastType(val symbol: String, val accentColor: NanoVGColor) {
    SUCCESS("✓", NanoVGColor(0.20f, 0.85f, 0.55f, 1f)),
    INFO("i", NanoVGColor(0.38f, 0.65f, 0.98f, 1f)),
    WARNING("!", NanoVGColor(0.98f, 0.72f, 0.22f, 1f)),
    ERROR("✕", NanoVGColor(0.96f, 0.32f, 0.35f, 1f)),
    MODULE_ON("ON", NanoVGColor(0.20f, 0.85f, 0.55f, 1f)),
    MODULE_OFF("OFF", NanoVGColor(0.55f, 0.55f, 0.60f, 1f))
}

class ToastNotification(
    val title: String,
    var message: String,
    val type: ToastType = ToastType.INFO,
    var durationMs: Long = 3000L,
    val category: Category? = null
) {

    var startTime = System.currentTimeMillis()
    private var hoverPauseStart = 0L
    private var totalPausedMs = 0L

    var repeatCount = 1

    var isExpired = false
    var isDismissed = false
    var isHovered = false

    var currentY: Float = Float.NaN
    var targetY: Float = 0f
    var prevRenderY: Float = Float.NaN
    var prevRenderWidth: Float = 26f
    var currentRenderWidth: Float = Float.NaN
    var lastRenderX: Float = 0f
    var lastRenderY: Float = 0f
    var lastWidth: Float = 220f
    var lastHeight: Float = 26f
    var dragOffsetX: Float = 0f
    var dismissStartTime: Long = 0L

    fun calculateTargetWidth(font: FontRenderer, maxAvailableW: Float = 440f): Float {
        val tagText = when (type) {
            ToastType.MODULE_ON, ToastType.MODULE_OFF -> "STATE"
            ToastType.SUCCESS -> "SUCCESS"
            ToastType.WARNING -> "WARN"
            ToastType.ERROR -> "ERROR"
            else -> "INFO"
        }
        val titleW = font.getStringWidth(title, 10.5f)
        val tagW = font.getStringWidth(tagText, 8.5f)
        val hasMsg = message.isNotBlank()
        val msgW = if (hasMsg) font.getStringWidth(message, 10.0f) else 0f
        val repeatW = if (repeatCount > 1) (font.getStringWidth("×$repeatCount", 8.5f) + 14f) else 0f
        val closeW = if (isHovered || hoverAnim.value > 0.05f) 16f else 0f

        val baseContentW = 26f + titleW + 17f + tagW + (if (hasMsg) 6f + msgW else 0f) + repeatW + closeW + 14f
        return baseContentW.coerceIn(150f, maxAvailableW.coerceAtLeast(150f))
    }

    val slideAnim = FloatAnimation(0f, 1f, 320L, CubicBezier.EASE_OUT)
    val pulseAnim = FloatAnimation(1f, 1f, 220L, CubicBezier.EASE_OUT)
    val hoverAnim = FloatAnimation(0f, 0f, 160L, CubicBezier.EASE_OUT)

    init {

        slideAnim.animateTo(1f)
    }

    fun onCoalesce(newMsg: String = message) {
        repeatCount++
        message = newMsg
        startTime = System.currentTimeMillis()
        totalPausedMs = 0L
        hoverPauseStart = 0L
        isDismissed = false
        dismissStartTime = 0L

        slideAnim.animateTo(1f)
        pulseAnim.setImmediate(1.10f)
        pulseAnim.animateTo(1.0f)
    }

    fun dismiss() {
        if (!isDismissed) {
            isDismissed = true
            dismissStartTime = System.currentTimeMillis()
            slideAnim.animateTo(0f)
        }
    }

    fun getElapsedMs(): Long {
        val currentMs = System.currentTimeMillis()
        val currentPause = if (hoverPauseStart > 0L) (currentMs - hoverPauseStart) else 0L
        return (currentMs - startTime - totalPausedMs - currentPause).coerceAtLeast(0L)
    }

    fun getProgress(): Float {
        val effectiveElapsed = getElapsedMs()
        return (1f - (effectiveElapsed.toFloat() / durationMs.toFloat())).coerceIn(0f, 1f)
    }

    fun updateHoverState(hovered: Boolean) {
        if (hovered && !isHovered) {
            isHovered = true
            hoverPauseStart = System.currentTimeMillis()
            hoverAnim.animateTo(1f)
        } else if (!hovered && isHovered) {
            isHovered = false
            if (hoverPauseStart > 0L) {
                totalPausedMs += System.currentTimeMillis() - hoverPauseStart
                hoverPauseStart = 0L
            }
            hoverAnim.animateTo(0f)
        }
    }

    fun tickLifetime() {
        val currentMs = System.currentTimeMillis()
        val effectiveElapsed = getElapsedMs()

        if (!isDismissed && effectiveElapsed >= durationMs) {
            dismiss()
        }

        if (isDismissed) {
            val dismissElapsed = if (dismissStartTime > 0L) currentMs - dismissStartTime else 260L
            if (dismissElapsed >= 260L || (slideAnim.targetValue == 0f && slideAnim.value <= 0.01f)) {
                isExpired = true
            }
        }
    }

    fun render(
        slotTargetY: Float,
        anchorX: Float,
        screenWidth: Float,
        screenHeight: Float,
        renderMode: String,
        isAnchorTop: Boolean,
        isAnchorCenter: Boolean,
        isAnchorLeft: Boolean,
        animSpeed: Float = 1.0f
    ) {
        targetY = slotTargetY
        if (currentY.isNaN()) {
            currentY = targetY + (if (isAnchorTop) -20f else 20f)
        }

        val deltaY = targetY - currentY
        if (abs(deltaY) < 0.05f) {
            currentY = targetY
        } else {
            val factor = (0.22f * animSpeed).coerceIn(0.10f, 0.45f)
            currentY += deltaY * factor
        }

        tickLifetime()
        if (isExpired) return

        val s = slideAnim.value.coerceIn(0f, 1f)
        if (s <= 0.001f && isDismissed) return

        val pulse = pulseAnim.value
        val hover = hoverAnim.value

        when (renderMode) {
            "DynamicIsland" -> this.renderDynamicIsland(screenWidth, s, pulse, hover)
            "FluidCompact" -> this.renderFluidCompact(anchorX, currentY, isAnchorLeft, s, pulse, hover)
            else -> this.renderZincPrecision(anchorX, currentY, isAnchorLeft, isAnchorTop, s, pulse, hover)
        }
    }

    fun hitTest(mouseX: Float, mouseY: Float): Boolean {
        return mouseX in lastRenderX..(lastRenderX + lastWidth) &&
            mouseY in lastRenderY..(lastRenderY + lastHeight)
    }

    fun isCloseButtonHit(mouseX: Float, mouseY: Float): Boolean {
        val closeAreaSize = 22f
        val closeX = lastRenderX + lastWidth - closeAreaSize
        val closeY = lastRenderY + (lastHeight - closeAreaSize) * 0.5f
        return mouseX in closeX..(closeX + closeAreaSize) &&
            mouseY in closeY..(closeY + closeAreaSize)
    }
}

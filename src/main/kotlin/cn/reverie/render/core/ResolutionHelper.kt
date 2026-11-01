package cn.reverie.render.core

import cn.reverie.app.event.EventBus
import cn.reverie.app.event.render.ResolutionChangeEvent
import net.minecraft.client.Minecraft

object ResolutionHelper {

    private const val DESIGN_WIDTH = 960f
    private const val DESIGN_HEIGHT = 540f
    private const val OVERALL_VISUAL_SCALE = 0.75f

    var displayWidth: Int = 1920
        private set
    var displayHeight: Int = 1080
        private set

    var framebufferWidth: Int = 1920
        private set
    var framebufferHeight: Int = 1080
        private set

    private var minecraftScaledWidth: Float = DESIGN_WIDTH
    private var minecraftScaledHeight: Float = DESIGN_HEIGHT

    /** Screen-fit multiplier before the user's overall visual scale is applied. */
    val resolutionScaleFactor: Float
        get() {
            val widthScale = minecraftScaledWidth / DESIGN_WIDTH
            val heightScale = minecraftScaledHeight / DESIGN_HEIGHT
            val scale = minOf(widthScale, heightScale)
            return if (scale.isFinite() && scale > 0f) scale else 1f
        }

    /** Uniform multiplier from custom-render canvas units to Minecraft GUI units. */
    val uiScaleFactor: Float
        get() = resolutionScaleFactor * OVERALL_VISUAL_SCALE

    /** Width and height exposed to custom renderers, in uniform design-canvas units. */
    val scaledWidth: Float
        get() = if (minecraftScaledWidth > 0f) minecraftScaledWidth / uiScaleFactor else 0f
    val scaledHeight: Float
        get() = if (minecraftScaledHeight > 0f) minecraftScaledHeight / uiScaleFactor else 0f

    var guiScale: Float = 2.0f
        private set

    /** Framebuffer pixels per design-canvas unit on each axis. */
    val framebufferScaleX: Float
        get() = if (scaledWidth > 0f && framebufferWidth > 0) framebufferWidth.toFloat() / scaledWidth else guiScale.coerceAtLeast(1f)

    val framebufferScaleY: Float
        get() = if (scaledHeight > 0f && framebufferHeight > 0) framebufferHeight.toFloat() / scaledHeight else guiScale.coerceAtLeast(1f)

    /** NanoVG accepts one pixel ratio, so use the horizontal framebuffer density. */
    val devicePixelRatio: Float
        get() = framebufferScaleX

    /** Average pixel density for effects whose radius must remain visually circular. */
    val averageFramebufferScale: Float
        get() = (framebufferScaleX + framebufferScaleY) * 0.5f

    @JvmStatic fun toCanvasX(x: Double): Double = x / uiScaleFactor
    @JvmStatic fun toCanvasY(y: Double): Double = y / uiScaleFactor
    @JvmStatic fun toCanvasX(x: Float): Float = x / uiScaleFactor
    @JvmStatic fun toCanvasY(y: Float): Float = y / uiScaleFactor
    @JvmStatic fun toCanvasDelta(delta: Double): Double = delta / uiScaleFactor
    @JvmStatic fun toCanvasDelta(delta: Float): Float = delta / uiScaleFactor
    @JvmStatic fun getCanvasWidth(): Float = scaledWidth
    @JvmStatic fun getCanvasHeight(): Float = scaledHeight
    @JvmStatic fun getUiScale(): Float = uiScaleFactor

    fun update(
        windowWidth: Int, windowHeight: Int,
        fbWidth: Int, fbHeight: Int,
        guiScaledW: Int, guiScaledH: Int,
        scale: Int
    ) {
        val nextDisplayWidth = windowWidth.coerceAtLeast(0)
        val nextDisplayHeight = windowHeight.coerceAtLeast(0)
        val nextFramebufferWidth = fbWidth.coerceAtLeast(0)
        val nextFramebufferHeight = fbHeight.coerceAtLeast(0)
        val nextGuiScale = if (scale > 0) scale.toFloat() else 1.0f
        val nextMinecraftScaledWidth = guiScaledW.coerceAtLeast(0).toFloat()
        val nextMinecraftScaledHeight = guiScaledH.coerceAtLeast(0).toFloat()
        val changed = displayWidth != nextDisplayWidth || displayHeight != nextDisplayHeight ||
            framebufferWidth != nextFramebufferWidth || framebufferHeight != nextFramebufferHeight ||
            guiScale != nextGuiScale || minecraftScaledWidth != nextMinecraftScaledWidth ||
            minecraftScaledHeight != nextMinecraftScaledHeight

        displayWidth = nextDisplayWidth
        displayHeight = nextDisplayHeight
        framebufferWidth = nextFramebufferWidth
        framebufferHeight = nextFramebufferHeight
        guiScale = nextGuiScale
        minecraftScaledWidth = nextMinecraftScaledWidth
        minecraftScaledHeight = nextMinecraftScaledHeight

        if (changed) {
            EventBus.post(ResolutionChangeEvent(displayWidth, displayHeight, guiScale.toInt()))
        }
    }

    fun syncWithMinecraft() {
        val window = Minecraft.getInstance().window
        update(
            window.screenWidth, window.screenHeight,
            window.width, window.height,
            window.guiScaledWidth, window.guiScaledHeight,
            window.guiScale
        )
    }
}

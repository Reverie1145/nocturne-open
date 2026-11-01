package cn.reverie.render.nanovg

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.draw.RoundedRect
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.math.Rect2f
import cn.reverie.render.texture.TextureLoader
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

object NanoVGRenderer {

    const val EXTERNAL_ASSETS_BASE = "src/main/resources/assets"

    val context: Long
        get() = RenderEngine.nanoVG.handle

    private val imageCache = ConcurrentHashMap<String, NanoVGImage>()
    private val missingImages = ConcurrentHashMap.newKeySet<String>()

    fun drawRoundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: NanoVGColor) {
        if (width <= 0f || height <= 0f) return
        RoundedRect.draw(context, x, y, width, height, CornerRadii(radius), color)
    }

    fun drawRoundedRect(x: Float, y: Float, width: Float, height: Float, radii: CornerRadii, color: NanoVGColor) {
        if (width <= 0f || height <= 0f) return
        RoundedRect.draw(context, x, y, width, height, radii, color)
    }

    fun drawRoundedOutline(x: Float, y: Float, width: Float, height: Float, radius: Float, color: NanoVGColor, strokeWidth: Float = 1.0f) {
        if (width <= 0f || height <= 0f) return
        RoundedRect.drawOutline(context, x, y, width, height, CornerRadii(radius), color, strokeWidth)
    }

    fun drawGradientRoundedRect(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float,
        startColor: NanoVGColor, endColor: NanoVGColor,
        horizontal: Boolean = false
    ) {
        if (width <= 0f || height <= 0f) return
        Draw2D.gradientLinear(x, y, width, height, radius, startColor, endColor, horizontal)
    }

    fun drawCircle(cx: Float, cy: Float, radius: Float, color: NanoVGColor) {
        val path = NanoVGPath(context)
        path.begin()
        path.circle(cx, cy, radius)
        path.fill(color)
    }

    fun drawCircleOutline(cx: Float, cy: Float, radius: Float, color: NanoVGColor, strokeWidth: Float = 1.0f) {
        Draw2D.circleOutline(cx, cy, radius, color, strokeWidth)
    }

    fun drawShadow(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float, blur: Float,
        color: NanoVGColor = NanoVGColor(0f, 0f, 0f, 0.4f),
        offsetY: Float = 2f,
        offsetX: Float = 0f,
        spread: Float = 0f,
        innerCutout: Boolean = false
    ) {
        Draw2D.shadow(x, y, width, height, radius, blur, color, offsetY, offsetX, spread, innerCutout)
    }

    fun drawElevation(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float,
        elevation: Int = 2,
        color: NanoVGColor = NanoVGColor.BLACK,
        innerCutout: Boolean = false
    ) {
        Draw2D.elevation(x, y, width, height, radius, elevation, color, innerCutout)
    }

    fun drawGlow(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float, blur: Float,
        color: NanoVGColor,
        spread: Float = 0f,
        innerCutout: Boolean = false
    ) {
        Draw2D.glow(x, y, width, height, radius, blur, color, spread, innerCutout)
    }

    fun drawString(
        text: String,
        x: Float,
        y: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 13.0f,
        shadow: Boolean = false,
        blur: Float = 0f
    ): Float {
        val font = RenderEngine.fontRenderer
        return font.drawString(text, x, y, color, fontSize, TextAlignment.TOP_LEFT, shadow, blur)
    }

    fun drawVerticallyCenteredString(
        text: String,
        x: Float,
        centerY: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 13.0f,
        shadow: Boolean = false,
        blur: Float = 0f
    ): Float {
        val font = RenderEngine.fontRenderer
        return font.drawString(text, x, centerY, color, fontSize, TextAlignment.MIDDLE_LEFT, shadow, blur)
    }

    fun drawCenteredString(
        text: String,
        centerX: Float,
        centerY: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 13.0f,
        shadow: Boolean = false,
        blur: Float = 0f
    ): Float {
        val font = RenderEngine.fontRenderer
        return font.drawString(text, centerX, centerY, color, fontSize, TextAlignment.MIDDLE_CENTER, shadow, blur)
    }

    fun drawRightString(
        text: String,
        x: Float,
        y: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 13.0f,
        shadow: Boolean = false,
        blur: Float = 0f
    ): Float {
        val font = RenderEngine.fontRenderer
        return font.drawString(text, x, y, color, fontSize, TextAlignment.TOP_RIGHT, shadow, blur)
    }

    fun drawRightVerticallyCenteredString(
        text: String,
        rightX: Float,
        centerY: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 13.0f,
        shadow: Boolean = false,
        blur: Float = 0f
    ): Float {
        val font = RenderEngine.fontRenderer
        return font.drawString(text, rightX, centerY, color, fontSize, TextAlignment.MIDDLE_RIGHT, shadow, blur)
    }

    fun drawBlurTransitionString(
        text: String,
        x: Float,
        y: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 13.0f,
        progress: Float,
        maxBlur: Float = 5.5f,
        shadow: Boolean = false
    ): Float {
        val p = progress.coerceIn(0f, 1f)
        val blur = cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(p, maxBlur)
        val finalColor = color.multiplyAlpha(p)
        return drawString(text, x, y, finalColor, fontSize, shadow, blur)
    }

    fun drawBlurTransitionVerticallyCentered(
        text: String,
        x: Float,
        centerY: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 13.0f,
        progress: Float,
        maxBlur: Float = 5.5f,
        shadow: Boolean = false
    ): Float {
        val p = progress.coerceIn(0f, 1f)
        val blur = cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(p, maxBlur)
        val finalColor = color.multiplyAlpha(p)
        return drawVerticallyCenteredString(text, x, centerY, finalColor, fontSize, shadow, blur)
    }

    fun drawBlurTransitionCentered(
        text: String,
        centerX: Float,
        centerY: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 13.0f,
        progress: Float,
        maxBlur: Float = 5.5f,
        shadow: Boolean = false
    ): Float {
        val p = progress.coerceIn(0f, 1f)
        val blur = cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(p, maxBlur)
        val finalColor = color.multiplyAlpha(p)
        return drawCenteredString(text, centerX, centerY, finalColor, fontSize, shadow, blur)
    }

    fun drawMarqueeString(
        text: String,
        x: Float,
        centerY: Float,
        maxWidth: Float,
        isHovered: Boolean,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 12.5f,
        shadow: Boolean = false,
        blur: Float = 0f
    ): Float {
        val font = RenderEngine.fontRenderer
        val textWidth = font.getStringWidth(text, fontSize)

        if (textWidth <= maxWidth) {
            return font.drawString(text, x, centerY, color, fontSize, TextAlignment.MIDDLE_LEFT, shadow, blur)
        }

        if (!isHovered) {
            val truncated = font.trimWithEllipsis(text, maxWidth, fontSize)
            return font.drawString(truncated, x, centerY, color, fontSize, TextAlignment.MIDDLE_LEFT, shadow, blur)
        }

        val overflow = textWidth - maxWidth
        val cycleTime = (2500L + (overflow * 30L).toLong()).coerceIn(2000L, 8000L)
        val progress = (System.currentTimeMillis() % cycleTime).toFloat() / cycleTime.toFloat()

        val scrollOffset = when {
            progress < 0.20f -> 0f
            progress < 0.70f -> {
                val t = (progress - 0.20f) / 0.50f
                val smoothT = (Math.sin(t * Math.PI - Math.PI / 2) + 1.0).toFloat() * 0.5f
                smoothT * overflow
            }
            progress < 0.85f -> overflow
            else -> {
                val t = (progress - 0.85f) / 0.15f
                val smoothT = (Math.sin(t * Math.PI - Math.PI / 2) + 1.0).toFloat() * 0.5f
                overflow * (1f - smoothT)
            }
        }

        withScissor(x - 1f, centerY - fontSize * 0.90f, maxWidth + 2f, fontSize * 1.80f) {
            font.drawString(text, x - scrollOffset, centerY, color, fontSize, TextAlignment.MIDDLE_LEFT, shadow, blur)
        }

        return maxWidth
    }

    fun trimWithEllipsis(text: String, maxWidth: Float, fontSize: Float = 13.0f): String {
        return RenderEngine.fontRenderer.trimWithEllipsis(text, maxWidth, fontSize)
    }

    fun getTextWidth(text: String, fontSize: Float = 13.0f): Float {
        return RenderEngine.fontRenderer.getStringWidth(text, fontSize)
    }

    fun getTextHeight(fontSize: Float = 13.0f): Float {
        return RenderEngine.fontRenderer.getStringHeight(fontSize)
    }

    fun loadImage(resourceName: String): NanoVGImage? {
        val cached = imageCache[resourceName]
        if (cached != null && cached.isValid) return cached
        if (missingImages.contains(resourceName)) return null

        var stream: InputStream? = null

        val extFiles = listOf(
            File(EXTERNAL_ASSETS_BASE, "reverie/icons/$resourceName"),
            File(EXTERNAL_ASSETS_BASE, "reverie/$resourceName"),
            File(EXTERNAL_ASSETS_BASE, resourceName),
            File("assets/reverie/icons/$resourceName"),
            File(resourceName)
        )
        for (f in extFiles) {
            if (f.exists() && f.isFile) {
                try {
                    stream = FileInputStream(f)
                    break
                } catch (ignored: Exception) {}
            }
        }

        if (stream == null) {
            val classpathCandidates = listOf(
                "/assets/reverie/icons/$resourceName",
                "/assets/reverie/$resourceName",
                "/assets/$resourceName",
                resourceName
            )
            for (path in classpathCandidates) {
                val s = javaClass.getResourceAsStream(path)
                if (s != null) {
                    stream = s
                    break
                }
            }
        }

        if (stream == null) {
            missingImages.add(resourceName)
            return null
        }

        return try {
            val loaded = stream.use { TextureLoader.loadFromStream(it, generateMipmaps = true, forceWhite = true) }
            if (loaded == null) {
                missingImages.add(resourceName)
                return null
            }
            val nvgImg = NanoVGImage.createFromGLTexture(context, loaded.textureId, loaded.width, loaded.height)
            imageCache[resourceName] = nvgImg
            nvgImg
        } catch (e: Exception) {
            System.err.println("[Nocturne-NanoVG] Error loading texture $resourceName: ${e.message}")
            missingImages.add(resourceName)
            null
        }
    }

    fun drawImage(
        resourceName: String,
        x: Float, y: Float, width: Float, height: Float,
        alpha: Float = 1.0f,
        tintColor: NanoVGColor = NanoVGColor.WHITE
    ) {
        val img = loadImage(resourceName)
        val finalAlpha = (tintColor.a * alpha).coerceIn(0f, 1f)
        if (img != null && img.isValid) {
            MemoryStack.stackPush().use { stack ->
                val paint = NVGPaint.malloc(stack)
                NanoVGPaint.imagePattern(context, x, y, width, height, 0f, img.imageHandle, finalAlpha, paint)
                RoundedRect.draw(context, x, y, width, height, CornerRadii(0f), paint)
            }
        } else {
            drawCircle(x + width * 0.5f, y + height * 0.5f, width * 0.35f, tintColor.multiplyAlpha(alpha))
        }
    }

    fun drawImageRounded(
        resourceName: String,
        x: Float, y: Float, width: Float, height: Float,
        radius: Float,
        alpha: Float = 1.0f,
        tintColor: NanoVGColor = NanoVGColor.WHITE
    ) {
        val img = loadImage(resourceName)
        val finalAlpha = (tintColor.a * alpha).coerceIn(0f, 1f)
        if (img != null && img.isValid) {
            MemoryStack.stackPush().use { stack ->
                val paint = NVGPaint.malloc(stack)
                NanoVGPaint.imagePattern(context, x, y, width, height, 0f, img.imageHandle, finalAlpha, paint)
                RoundedRect.draw(context, x, y, width, height, CornerRadii(radius), paint)
            }
        } else {
            drawRoundedRect(x, y, width, height, radius, tintColor.multiplyAlpha(alpha))
        }
    }

    fun pushScissor(x: Float, y: Float, width: Float, height: Float) {
        RenderEngine.nanoVG.scissor.push(x, y, width, height)
    }

    fun popScissor() {
        RenderEngine.nanoVG.scissor.pop()
    }

    inline fun withScissor(x: Float, y: Float, width: Float, height: Float, block: () -> Unit) {
        pushScissor(x, y, width, height)
        try {
            block()
        } finally {
            popScissor()
        }
    }
}

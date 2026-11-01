package cn.reverie.render.video

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.nanovg.NanoVGPaint
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.stb.STBImage.*
    fun VideoBackgroundPlayer.resolveCoverRectImpl(screenW: Float, screenH: Float): FloatArray {
        val targetAspect = VIDEO_WIDTH.toFloat() / VIDEO_HEIGHT.toFloat()
        val screenAspect = if (screenH > 0f) screenW / screenH else targetAspect

        return if (screenAspect > targetAspect) {
            floatArrayOf(0f, (screenH - screenW / targetAspect) * 0.5f, screenW, screenW / targetAspect)
        } else {
            floatArrayOf((screenW - screenH * targetAspect) * 0.5f, 0f, screenH * targetAspect, screenH)
        }
    }

    fun VideoBackgroundPlayer.renderImpl(screenW: Float, screenH: Float, alpha: Float = 1.0f) {
        if (!isInitialized) init()

        val context = RenderEngine.nanoVG.handle
        if (context == 0L) return

        updateFrame()

        val activeTextureId = if (hasVideoLoaded && glTextureId > 0) glTextureId else fallbackTextureId
        if (activeTextureId <= 0) return

        if (hasVideoLoaded && glTextureId > 0) {
            if (nvgImageHandle <= 0) {
                nvgImageHandle = nvglCreateImageFromHandle(context, glTextureId, VIDEO_WIDTH, VIDEO_HEIGHT, 0)
            }
        } else {
            if (fallbackImageHandle <= 0 && fallbackTextureId > 0) {
                fallbackImageHandle = nvglCreateImageFromHandle(context, fallbackTextureId, VIDEO_WIDTH, VIDEO_HEIGHT, 0)
            }
        }

        val activeImageHandle = if (hasVideoLoaded && nvgImageHandle > 0) nvgImageHandle else fallbackImageHandle
        if (activeImageHandle <= 0) return

        val blurredFbo = videoBlurredFbo
        if (blurredFbo != null && blurredImageHandle <= 0) {
            blurredImageHandle = nvglCreateImageFromHandle(context, blurredFbo.textureId, BLUR_WIDTH, BLUR_HEIGHT, 0)
        }

        val cover = resolveCoverRect(screenW, screenH)
        val drawX = cover[0]
        val drawY = cover[1]
        val drawW = cover[2]
        val drawH = cover[3]

        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.imagePattern(context, drawX, drawY, drawW, drawH, 0f, activeImageHandle, alpha, paint)

            nvgBeginPath(context)
            nvgRect(context, 0f, 0f, screenW, screenH)
            nvgFillPaint(context, paint)
            nvgFill(context)
        }
    }

    fun VideoBackgroundPlayer.renderBlurredAreaImpl(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        screenW: Float,
        screenH: Float,
        alpha: Float = 1.0f,
        rotation: Float = 0f,
        zoom: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f
    ) {
        val context = RenderEngine.nanoVG.handle
        if (context == 0L) return

        val blurredFbo = videoBlurredFbo ?: return
        if (blurredImageHandle <= 0) {
            blurredImageHandle = nvglCreateImageFromHandle(context, blurredFbo.textureId, BLUR_WIDTH, BLUR_HEIGHT, 0)
        }
        val handle = blurredImageHandle
        if (handle <= 0) return

        val targetAspect = VIDEO_WIDTH.toFloat() / VIDEO_HEIGHT.toFloat()
        val screenAspect = if (screenH > 0f) screenW / screenH else targetAspect

        val drawW: Float
        val drawH: Float
        val drawX: Float
        val drawY: Float

        if (screenAspect > targetAspect) {
            drawW = screenW
            drawH = screenW / targetAspect
            drawX = 0f
            drawY = (screenH - drawH) * 0.5f
        } else {
            drawH = screenH
            drawW = screenH * targetAspect
            drawX = (screenW - drawW) * 0.5f
            drawY = 0f
        }

        // Map the cover rectangle through the same transform the backdrop was drawn with.
        val ccx = screenW * 0.5f
        val ccy = screenH * 0.5f
        val mapX = ccx + panX + (drawX - ccx) * zoom
        val mapY = ccy + panY + (drawY - ccy) * zoom
        val mapW = drawW * zoom
        val mapH = drawH * zoom

        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.imagePattern(context, mapX, mapY, mapW, mapH, 0f, handle, alpha, paint)

            val rotated = rotation != 0f
            if (rotated) {
                nvgSave(context)
                val pivotX = x + w * 0.5f
                val pivotY = y + h * 0.5f
                nvgTranslate(context, pivotX, pivotY)
                nvgRotate(context, rotation)
                nvgTranslate(context, -pivotX, -pivotY)
            }

            nvgBeginPath(context)
            nvgRoundedRect(context, x, y, w, h, radius)
            nvgFillPaint(context, paint)
            nvgFill(context)

            if (rotated) {
                nvgRestore(context)
            }
        }
    }

    fun VideoBackgroundPlayer.renderBlurredQuadImpl(
        xs: FloatArray,
        ys: FloatArray,
        count: Int,
        screenW: Float,
        screenH: Float,
        alpha: Float = 1.0f
    ) {
        val context = RenderEngine.nanoVG.handle
        if (context == 0L || count < 3) return

        val blurredFbo = videoBlurredFbo ?: return
        if (blurredImageHandle <= 0) {
            blurredImageHandle = nvglCreateImageFromHandle(context, blurredFbo.textureId, BLUR_WIDTH, BLUR_HEIGHT, 0)
        }
        val handle = blurredImageHandle
        if (handle <= 0) return

        val targetAspect = VIDEO_WIDTH.toFloat() / VIDEO_HEIGHT.toFloat()
        val screenAspect = if (screenH > 0f) screenW / screenH else targetAspect

        val drawW: Float
        val drawH: Float
        val drawX: Float
        val drawY: Float

        if (screenAspect > targetAspect) {
            drawW = screenW
            drawH = screenW / targetAspect
            drawX = 0f
            drawY = (screenH - drawH) * 0.5f
        } else {
            drawH = screenH
            drawW = screenH * targetAspect
            drawX = (screenW - drawW) * 0.5f
            drawY = 0f
        }

        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.imagePattern(context, drawX, drawY, drawW, drawH, 0f, handle, alpha, paint)

            nvgBeginPath(context)
            nvgMoveTo(context, xs[0], ys[0])
            for (i in 1 until count) {
                nvgLineTo(context, xs[i], ys[i])
            }
            nvgClosePath(context)
            nvgFillPaint(context, paint)
            nvgFill(context)
        }
    }

    fun VideoBackgroundPlayer.renderPosterImpl(
        x: Float, y: Float, w: Float, h: Float, radius: Float,
        srcX: Float, srcY: Float, zoom: Float, offX: Float, offY: Float,
        alpha: Float, blurred: Boolean
    ) {
        val context = RenderEngine.nanoVG.handle
        if (context == 0L || w <= 0f || h <= 0f) return

        val handle = when {
            blurred -> blurredImageHandle
            hasVideoLoaded && nvgImageHandle > 0 -> nvgImageHandle
            else -> fallbackImageHandle
        }
        if (handle <= 0) return

        val vw = VIDEO_WIDTH.toFloat()
        val vh = VIDEO_HEIGHT.toFloat()
        val scale = maxOf(w / vw, h / vh) * zoom.coerceAtLeast(1f)
        val iw = vw * scale
        val ih = vh * scale
        var ox = x + w * 0.5f + offX - srcX * iw
        var oy = y + h * 0.5f + offY - srcY * ih
        // Never expose the area outside the frame.
        ox = ox.coerceIn(x + w - iw, x)
        oy = oy.coerceIn(y + h - ih, y)

        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.imagePattern(context, ox, oy, iw, ih, 0f, handle, alpha, paint)
            nvgBeginPath(context)
            nvgRoundedRect(context, x, y, w, h, radius)
            nvgFillPaint(context, paint)
            nvgFill(context)
        }
    }
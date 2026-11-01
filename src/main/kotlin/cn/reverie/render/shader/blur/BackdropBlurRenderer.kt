package cn.reverie.render.shader.blur

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.draw.RoundedRect
import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPaint
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.system.MemoryStack
import kotlin.math.ceil
import kotlin.math.floor

class BackdropBlurRenderer(val blurEngine: BlurEngine) {

    // The FBO owns its color texture; NanoVG must not delete it with the image handle.
    private companion object { const val NVG_IMAGE_NODELETE = 1 shl 16 }

    private data class RegionImage(var textureId: Int = 0, var imageHandle: Int = 0)
    private data class RegionKey(val x: Int, val y: Int, val width: Int, val height: Int, val radiusBits: Int)
    private val regionImages = ArrayList<RegionImage>()
    private val frameRegionSlots = HashMap<RegionKey, Int>()
    private val frameRegionResults = HashMap<RegionKey, BlurEngine.RegionBlurResult>()
    private var regionCursor = 0
    private var fullTextureId = 0
    private var fullImageHandle = 0

    /** Called before NanoVG begins queuing a frame. Previous frame commands are flushed by endFrame. */
    fun beginFrame() {
        blurEngine.beginFrame()
        regionCursor = 0
        frameRegionSlots.clear()
        frameRegionResults.clear()
    }

    fun close() {
        val context = RenderEngine.nanoVG.handle
        regionImages.forEach { if (it.imageHandle > 0) org.lwjgl.nanovg.NanoVG.nvgDeleteImage(context, it.imageHandle) }
        regionImages.clear()
        if (fullImageHandle > 0) org.lwjgl.nanovg.NanoVG.nvgDeleteImage(context, fullImageHandle)
        fullImageHandle = 0
        fullTextureId = 0
    }

    fun renderBackdrop(
        rect: Rect2f,
        radius: Float = 8f,
        tintColor: NanoVGColor = NanoVGColor(0.05f, 0.06f, 0.09f, 0.40f),
        blurRadius: Float = 16f
    ) {
        renderBackdrop(rect.x, rect.y, rect.width, rect.height, radius, tintColor, blurRadius)
    }

    fun withBlurPaint(
        blurRadius: Float = 16f,
        opacity: Float = 1f,
        region: Rect2f? = null,
        block: (NVGPaint) -> Unit
    ): Boolean {
        try {
            val context = RenderEngine.nanoVG.handle
            val fbW = ResolutionHelper.framebufferWidth
            val fbH = ResolutionHelper.framebufferHeight
            val screenW = ResolutionHelper.scaledWidth
            val screenH = ResolutionHelper.scaledHeight
            val scaleX = if (screenW > 0f) fbW / screenW else 1f
            val scaleY = if (screenH > 0f) fbH / screenH else scaleX

            MemoryStack.stackPush().use { stack ->
                val xform = stack.mallocFloat(6)
                org.lwjgl.nanovg.NanoVG.nvgCurrentTransform(context, xform)
                val ta = xform.get(0)
                val tb = xform.get(1)
                val tc = xform.get(2)
                val td = xform.get(3)
                val te = xform.get(4)
                val tf = xform.get(5)
                val det = ta * td - tb * tc
                val identity = ta == 1f && td == 1f && tb == 0f && tc == 0f && te == 0f && tf == 0f
                val ia: Float
                val ib: Float
                val ic: Float
                val idd: Float
                val ie: Float
                val iff: Float
                if (!identity && det != 0f) {
                    ia = td / det
                    ib = -tb / det
                    ic = -tc / det
                    idd = ta / det
                    ie = -(ia * te + ic * tf)
                    iff = -(ib * te + idd * tf)
                } else {
                    ia = 1f; ib = 0f; ic = 0f; idd = 1f; ie = 0f; iff = 0f
                }

                val requested = region ?: Rect2f(0f, 0f, screenW, screenH)
                val sLeft: Float
                val sTop: Float
                val sRight: Float
                val sBottom: Float
                if (!identity) {
                    val ax0 = ta * requested.x + tc * requested.y + te
                    val ay0 = tb * requested.x + td * requested.y + tf
                    val ax1 = ta * (requested.x + requested.width) + tc * requested.y + te
                    val ay1 = tb * (requested.x + requested.width) + td * requested.y + tf
                    val ax2 = ta * requested.x + tc * (requested.y + requested.height) + te
                    val ay2 = tb * requested.x + td * (requested.y + requested.height) + tf
                    val ax3 = ta * (requested.x + requested.width) + tc * (requested.y + requested.height) + te
                    val ay3 = tb * (requested.x + requested.width) + td * (requested.y + requested.height) + tf
                    sLeft = minOf(ax0, ax1, ax2, ax3)
                    sRight = maxOf(ax0, ax1, ax2, ax3)
                    sTop = minOf(ay0, ay1, ay2, ay3)
                    sBottom = maxOf(ay0, ay1, ay2, ay3)
                } else {
                    sLeft = requested.x
                    sTop = requested.y
                    sRight = requested.x + requested.width
                    sBottom = requested.y + requested.height
                }

                val px0 = floor(sLeft * scaleX).toInt().coerceIn(0, fbW)
                val px1 = ceil(sRight * scaleX).toInt().coerceIn(0, fbW)
                // Convert NanoVG top-left coordinates to OpenGL bottom-left coordinates.
                val py0 = (fbH - ceil(sBottom * scaleY).toInt()).coerceIn(0, fbH)
                val py1 = (fbH - floor(sTop * scaleY).toInt()).coerceIn(0, fbH)
                // A current full-screen blur is cheaper than running a separate two-pass
                // Gaussian for every HUD/GUI panel. Keep the local path for callers that
                // render before a full-screen blur has been prepared this frame.
                val useFullScreenBlur = region == null || blurEngine.screenBlurPreparedThisFrame
                val regionKey = if (region == null || useFullScreenBlur) null else RegionKey(px0, py0, px1 - px0, py1 - py0, blurRadius.toBits())
                val regionResult = if (regionKey == null) null else frameRegionResults.getOrPut(regionKey) {
                    val slot = frameRegionSlots.getOrPut(regionKey) { regionCursor++ }
                    blurEngine.prepareRegionBlur(slot, px0, py0, px1 - px0, py1 - py0, blurRadius)
                        ?: return false
                }
                val blurredFbo = regionResult?.fbo
                val textureId = blurredFbo?.textureId ?: if (region == null || useFullScreenBlur) blurEngine.getBlurredScreenTexture(blurRadius) else 0
                if (textureId > 0) {
                    val textureW = blurredFbo?.width ?: fbW
                    val textureH = blurredFbo?.height ?: fbH
                    val image: RegionImage?
                    if (regionResult != null) {
                        val slot = frameRegionSlots.getValue(regionKey!!)
                        while (regionImages.size <= slot) regionImages.add(RegionImage())
                        image = regionImages[slot]
                        if (image.textureId != textureId || image.imageHandle <= 0) {
                            if (image.imageHandle > 0) org.lwjgl.nanovg.NanoVG.nvgDeleteImage(context, image.imageHandle)
                            image.textureId = textureId
                            image.imageHandle = org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle(context, textureId, textureW, textureH, NVG_IMAGE_NODELETE)
                        }
                    } else {
                        image = null
                        if (fullTextureId != textureId || fullImageHandle <= 0) {
                            if (fullImageHandle > 0) org.lwjgl.nanovg.NanoVG.nvgDeleteImage(context, fullImageHandle)
                            fullTextureId = textureId
                            fullImageHandle = org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle(context, textureId, textureW, textureH, NVG_IMAGE_NODELETE)
                        }
                    }
                    val imageHandle = image?.imageHandle ?: fullImageHandle
                    if (imageHandle > 0) {
                        val paint = NVGPaint.malloc(stack)
                        val originX: Float
                        val originY: Float
                        val patternW: Float
                        val patternH: Float
                        if (region == null || useFullScreenBlur) {
                            originX = ie
                            originY = iff
                            patternW = screenW
                            patternH = screenH
                        } else {
                            val result = regionResult!!
                            val sampleScreenX = result.left / scaleX
                            val sampleScreenY = (fbH - result.bottom - result.sampleHeight) / scaleY
                            originX = ia * sampleScreenX + ic * sampleScreenY + ie
                            originY = ib * sampleScreenX + idd * sampleScreenY + iff
                            patternW = result.sampleWidth / scaleX
                            patternH = result.sampleHeight / scaleY
                        }
                        NanoVGPaint.imagePattern(context, originX, originY, patternW, patternH, 0f, imageHandle, opacity.coerceIn(0f, 1f), paint)
                        // Cancel the complete surface transform so the sampled scene stays
                        // aligned with the screen while the glass tilts, scales, or moves.
                        val paintTransform = paint.xform()
                        paintTransform.put(0, ia)
                        paintTransform.put(1, ib)
                        paintTransform.put(2, ic)
                        paintTransform.put(3, idd)
                        block(paint)
                        return true
                    }
                }
            }
        } catch (_: Throwable) {}
        return false
    }

    fun renderBackdrop(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float = 8f,
        tintColor: NanoVGColor = NanoVGColor(0.05f, 0.06f, 0.09f, 0.40f),
        blurRadius: Float = 16f,
        enableBlur: Boolean = true,
        @Suppress("UNUSED_PARAMETER") backdropOpacity: Float = 1f,
        region: Rect2f? = null
    ) {
        val context = RenderEngine.nanoVG.handle
        var drawn = false
        if (enableBlur) {
            drawn = withBlurPaint(blurRadius, region = region ?: Rect2f(x, y, width, height)) { paint ->
                RoundedRect.draw(context, x, y, width, height, CornerRadii(radius), paint)
            }
        }

        val finalTint = if (drawn) tintColor else if (enableBlur) tintColor.copy(a = (tintColor.a + 0.15f).coerceAtMost(0.60f)) else tintColor
        Draw2D.roundedRect(x, y, width, height, radius, finalTint)
    }

    /** Rounded-corner compatibility overload for existing HUD widgets. */
    fun renderBackdrop(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        tintColor: NanoVGColor,
        blurRadius: Float = 16f,
        enableBlur: Boolean = true,
        @Suppress("UNUSED_PARAMETER") backdropOpacity: Float = 1f
    ) {
        val context = RenderEngine.nanoVG.handle
        var drawn = false
        if (enableBlur) {
            drawn = withBlurPaint(blurRadius, region = Rect2f(x, y, width, height)) { paint ->
                RoundedRect.draw(context, x, y, width, height, radii, paint)
            }
        }

        val radius = maxOf(radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft)
        val finalTint = if (drawn) tintColor else if (enableBlur) tintColor.copy(a = (tintColor.a + 0.15f).coerceAtMost(0.60f)) else tintColor
        Draw2D.roundedRect(x, y, width, height, radius, finalTint)
    }

    /** Opacity-controlled variant used by the chat overlay; the shared HUD/GUI path above stays unchanged. */
    fun renderBackdropWithOpacity(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        tintColor: NanoVGColor,
        blurRadius: Float,
        enableBlur: Boolean,
        opacity: Float
    ) {
        val context = RenderEngine.nanoVG.handle
        val alpha = opacity.coerceIn(0f, 1f)
        val drawn = enableBlur && withBlurPaint(blurRadius, alpha, Rect2f(x, y, width, height)) { paint ->
            RoundedRect.draw(context, x, y, width, height, radii, paint)
        }
        if (!drawn && alpha > 0f) {
            RoundedRect.draw(context, x, y, width, height, radii, tintColor.copy(a = alpha))
        }
    }
}

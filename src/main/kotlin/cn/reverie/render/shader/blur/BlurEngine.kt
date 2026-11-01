package cn.reverie.render.shader.blur

import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.gl.GLStateGuard
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL30C.*

class BlurEngine : AutoCloseable {

    data class RegionBlurResult(val fbo: FBO, val left: Int, val bottom: Int, val sampleWidth: Int, val sampleHeight: Int)

    val kawaseBlur = DualKawaseBlur()
    private val localGaussianBlur = LocalGaussianBlur()
    val gaussianBlur = GaussianBlur()
    val frostedGlass = FrostedGlassEffect()
    val backdropRenderer by lazy { BackdropBlurRenderer(this) }

    private var screenCaptureFbo: FBO? = null
    private var blurredScreenFbo: FBO? = null
    private class RegionBuffers {
        var capture: FBO? = null
        var horizontal: FBO? = null
        var blurred: FBO? = null
    }
    private val regionBuffers = ArrayList<RegionBuffers>()
    /** True only after a full-screen blur has been refreshed for the current NanoVG frame. */
    var screenBlurPreparedThisFrame: Boolean = false
        private set

    fun beginFrame() {
        screenBlurPreparedThisFrame = false
    }

    fun prepareScreenBlur(radius: Float = 12f) {
        val width = ResolutionHelper.framebufferWidth
        val height = ResolutionHelper.framebufferHeight
        if (width <= 0 || height <= 0) return

        ensureFbos(width, height)

        GLStateGuard.withStateGuard {
            updateScreenBlur(radius)
        }
        screenBlurPreparedThisFrame = true
    }

    fun getBlurredScreenTexture(radius: Float = 12f): Int {
        if (blurredScreenFbo == null) {
            prepareScreenBlur(radius)
        }
        return blurredScreenFbo?.textureId ?: 0
    }

    /** Capture and blur only the requested physical framebuffer rectangle. */
    fun prepareRegionBlur(slotIndex: Int, x: Int, y: Int, width: Int, height: Int, radius: Float = 12f): RegionBlurResult? {
        val screenW = ResolutionHelper.framebufferWidth
        val screenH = ResolutionHelper.framebufferHeight
        if (screenW <= 0 || screenH <= 0 || width <= 0 || height <= 0) return null

        val downsample = kotlin.math.ceil(radius.coerceAtLeast(1f) / 9f).toInt().coerceIn(2, 4)
        // Keep enough source pixels around the crop for the Gaussian's four-sigma support.
        val padding = kotlin.math.ceil(radius.coerceAtLeast(1f) * 1.5f).toInt().coerceAtLeast(1)
        val left = (x - padding).coerceAtLeast(0)
        val bottom = (y - padding).coerceAtLeast(0)
        val right = (x + width + padding).coerceAtMost(screenW)
        val top = (y + height + padding).coerceAtMost(screenH)
        val sampleWidth = right - left
        val sampleHeight = top - bottom
        if (sampleWidth <= 0 || sampleHeight <= 0) return null
        val regionW = maxOf(1, (sampleWidth + downsample - 1) / downsample)
        val regionH = maxOf(1, (sampleHeight + downsample - 1) / downsample)

        val sourceFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        while (regionBuffers.size <= slotIndex) regionBuffers.add(RegionBuffers())
        val buffers = regionBuffers[slotIndex]
        GLStateGuard.withStateGuard {
            if (buffers.capture == null || buffers.capture?.width != regionW || buffers.capture?.height != regionH) {
                buffers.capture?.close()
                buffers.capture = FBO(regionW, regionH, hasDepthStencil = false)
            }
            if (buffers.blurred == null || buffers.blurred?.width != regionW || buffers.blurred?.height != regionH) {
                buffers.blurred?.close()
                buffers.blurred = FBO(regionW, regionH, hasDepthStencil = false)
            }
            if (buffers.horizontal == null || buffers.horizontal?.width != regionW || buffers.horizontal?.height != regionH) {
                buffers.horizontal?.close()
                buffers.horizontal = FBO(regionW, regionH, hasDepthStencil = false)
            }
            val capture = buffers.capture ?: return@withStateGuard
            val horizontal = buffers.horizontal ?: return@withStateGuard
            val blurred = buffers.blurred ?: return@withStateGuard
            glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebuffer)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, capture.fboId)
            glBlitFramebuffer(
                left, bottom, right, top,
                0, regionH, regionW, 0,
                GL_COLOR_BUFFER_BIT, GL_LINEAR
            )
            localGaussianBlur.process(capture, horizontal, blurred, radius, downsample)
        }
        val output = buffers.blurred ?: return null
        return RegionBlurResult(output, left, bottom, sampleWidth, sampleHeight)
    }

    private fun ensureFbos(width: Int, height: Int) {
        if (screenCaptureFbo == null || screenCaptureFbo?.width != width || screenCaptureFbo?.height != height) {
            screenCaptureFbo?.close()
            screenCaptureFbo = FBO(width, height, hasDepthStencil = false)
        }
        if (blurredScreenFbo == null || blurredScreenFbo?.width != width || blurredScreenFbo?.height != height) {
            blurredScreenFbo?.close()
            blurredScreenFbo = FBO(width, height, hasDepthStencil = false)
        }
    }

    fun prepareScreenBlurFromTexture(sourceTextureId: Int, radius: Float = 12f) {
        val width = ResolutionHelper.framebufferWidth
        val height = ResolutionHelper.framebufferHeight
        if (width <= 0 || height <= 0 || sourceTextureId <= 0) return

        ensureFbos(width, height)

        GLStateGuard.withStateGuard {
            val capture = screenCaptureFbo ?: return@withStateGuard
            val blurred = blurredScreenFbo ?: return@withStateGuard

            glBindTexture(GL_TEXTURE_2D, sourceTextureId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glBindTexture(GL_TEXTURE_2D, 0)

            FBOBlitter.blitTextureFlipped(sourceTextureId, capture)

            val iterations = (radius / 3.0f).toInt().coerceIn(2, 6)
            kawaseBlur.process(capture, blurred, iterations = iterations, offset = radius * 0.2f)
        }
        screenBlurPreparedThisFrame = true
    }

    private fun updateScreenBlur(radius: Float) {
        val capture = screenCaptureFbo ?: return
        val blurred = blurredScreenFbo ?: return

        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, capture.fboId)
        glBlitFramebuffer(
            0, 0, capture.width, capture.height,
            0, capture.height, capture.width, 0,
            GL_COLOR_BUFFER_BIT,
            GL_NEAREST
        )
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0)

        val iterations = (radius / 3.0f).toInt().coerceIn(2, 6)
        kawaseBlur.process(capture, blurred, iterations = iterations, offset = radius * 0.2f)
    }

    override fun close() {
        backdropRenderer.close()
        localGaussianBlur.close()
        kawaseBlur.close()
        gaussianBlur.close()
        frostedGlass.close()
        screenCaptureFbo?.close()
        screenCaptureFbo = null
        blurredScreenFbo?.close()
        blurredScreenFbo = null
        regionBuffers.forEach {
            it.capture?.close()
            it.horizontal?.close()
            it.blurred?.close()
        }
        regionBuffers.clear()
    }
}

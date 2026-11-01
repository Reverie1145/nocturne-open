package cn.reverie.render.framebuffer

import cn.reverie.render.nanovg.NanoVGColor
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL30C.*
import java.nio.ByteBuffer

class FBO(
    var width: Int,
    var height: Int,
    val colorFormat: FBOAttachment = FBOAttachment.RGBA8,
    val hasDepthStencil: Boolean = true,
    val linearFiltering: Boolean = true
) : AutoCloseable {

    var fboId: Int = 0
        private set
    var textureId: Int = 0
        private set
    var depthStencilRboId: Int = 0
        private set

    val isValid: Boolean get() = fboId != 0

    val textureView: FBOTextureView
        get() = FBOTextureView(textureId, width, height)

    init {
        createBuffers()
    }

    private fun createBuffers() {
        if (width <= 0 || height <= 0) return

        fboId = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)

        textureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, textureId)
        val filter = if (linearFiltering) GL_LINEAR else GL_NEAREST
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            colorFormat.internalFormat,
            width,
            height,
            0,
            colorFormat.format,
            colorFormat.type,
            null as ByteBuffer?
        )

        glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D,
            textureId,
            0
        )

        if (hasDepthStencil) {
            depthStencilRboId = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, depthStencilRboId)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height)
            glFramebufferRenderbuffer(
                GL_FRAMEBUFFER,
                GL_DEPTH_STENCIL_ATTACHMENT,
                GL_RENDERBUFFER,
                depthStencilRboId
            )
        }

        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[Nocturne-FBO] Incomplete framebuffer status: $status")
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    fun bind(setViewport: Boolean = true) {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)
        if (setViewport) {
            glViewport(0, 0, width, height)
        }
    }

    fun unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    inline fun use(setViewport: Boolean = true, block: () -> Unit) {
        bind(setViewport)
        try {
            block()
        } finally {
            unbind()
        }
    }

    fun clear(color: NanoVGColor = NanoVGColor.TRANSPARENT, clearDepth: Boolean = true, clearStencil: Boolean = true) {
        bind()
        glClearColor(color.r, color.g, color.b, color.a)
        var flags = GL_COLOR_BUFFER_BIT
        if (clearDepth && hasDepthStencil) flags = flags or GL_DEPTH_BUFFER_BIT
        if (clearStencil && hasDepthStencil) flags = flags or GL_STENCIL_BUFFER_BIT
        glClear(flags)
    }

    fun resize(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        if (newWidth == width && newHeight == height) return

        close()
        width = newWidth
        height = newHeight
        createBuffers()
    }

    fun blitTo(destFbo: FBO?, filter: Int = GL_LINEAR) {
        val destId = destFbo?.fboId ?: 0
        val destW = destFbo?.width ?: width
        val destH = destFbo?.height ?: height

        glBindFramebuffer(GL_READ_FRAMEBUFFER, fboId)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destId)
        glBlitFramebuffer(
            0, 0, width, height,
            0, 0, destW, destH,
            GL_COLOR_BUFFER_BIT,
            filter
        )
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    override fun close() {
        if (fboId != 0) {
            glDeleteFramebuffers(fboId)
            fboId = 0
        }
        if (textureId != 0) {
            glDeleteTextures(textureId)
            textureId = 0
        }
        if (depthStencilRboId != 0) {
            glDeleteRenderbuffers(depthStencilRboId)
            depthStencilRboId = 0
        }
    }
}

package cn.reverie.render.framebuffer

import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture
import org.lwjgl.opengl.GL15C.*
import org.lwjgl.opengl.GL20C.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20C.glVertexAttribPointer
import org.lwjgl.opengl.GL30C.glBindVertexArray
import org.lwjgl.opengl.GL30C.glGenVertexArrays
import org.lwjgl.system.MemoryStack

object FBOBlitter : AutoCloseable {

    private var vao = 0
    private var vbo = 0
    private var vaoFlipped = 0
    private var vboFlipped = 0

    private val passthroughShader: ShaderProgram by lazy {
        ShaderProgram.fromClasspath(
            "passthrough",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/core/passthrough.fsh"
        )
    }

    init {
        initQuad()
        initQuadFlipped()
    }

    private fun initQuad() {
        vao = glGenVertexArrays()
        glBindVertexArray(vao)

        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

        val quadVertices = floatArrayOf(
            -1.0f,  1.0f, 0.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
             1.0f, -1.0f, 1.0f, 0.0f
        )

        MemoryStack.stackPush().use { stack ->
            val buf = stack.floats(*quadVertices)
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW)
        }

        val stride = 4 * Float.SIZE_BYTES

        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L)

        glEnableVertexAttribArray(1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, (2 * Float.SIZE_BYTES).toLong())

        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)
    }

    private fun initQuadFlipped() {
        vaoFlipped = glGenVertexArrays()
        glBindVertexArray(vaoFlipped)

        vboFlipped = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboFlipped)

        val quadVerticesFlipped = floatArrayOf(
            -1.0f,  1.0f, 0.0f, 0.0f,
            -1.0f, -1.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 1.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 1.0f
        )

        MemoryStack.stackPush().use { stack ->
            val buf = stack.floats(*quadVerticesFlipped)
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW)
        }

        val stride = 4 * Float.SIZE_BYTES
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L)
        glEnableVertexAttribArray(1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, (2 * Float.SIZE_BYTES).toLong())

        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)
    }

    fun renderQuad() {
        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glBindVertexArray(0)
    }

    fun renderQuadFlipped() {
        glBindVertexArray(vaoFlipped)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glBindVertexArray(0)
    }

    fun blitTexture(textureId: Int, targetFbo: FBO?, color: NanoVGColor = NanoVGColor.WHITE) {
        targetFbo?.bind() ?: org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0)

        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glDisable(GL_CULL_FACE)
        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_BLEND)
        glColorMask(true, true, true, true)

        passthroughShader.use {
            setSampler("uTexture", 0)
            setColor("uColor", color)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, textureId)
            org.lwjgl.opengl.GL33C.glBindSampler(0, 0)

            renderQuad()

            glBindTexture(GL_TEXTURE_2D, 0)
        }
    }

    fun blitTextureFlipped(textureId: Int, targetFbo: FBO?, color: NanoVGColor = NanoVGColor.WHITE) {
        targetFbo?.bind() ?: org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0)

        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glDisable(GL_CULL_FACE)
        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_BLEND)
        glColorMask(true, true, true, true)

        passthroughShader.use {
            setSampler("uTexture", 0)
            setColor("uColor", color)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, textureId)
            org.lwjgl.opengl.GL33C.glBindSampler(0, 0)

            renderQuadFlipped()

            glBindTexture(GL_TEXTURE_2D, 0)
        }
    }

    override fun close() {
        if (vao != 0) {
            org.lwjgl.opengl.GL30C.glDeleteVertexArrays(vao)
            vao = 0
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo)
            vbo = 0
        }
        if (vaoFlipped != 0) {
            org.lwjgl.opengl.GL30C.glDeleteVertexArrays(vaoFlipped)
            vaoFlipped = 0
        }
        if (vboFlipped != 0) {
            glDeleteBuffers(vboFlipped)
            vboFlipped = 0
        }
    }
}

package cn.reverie.render.shader.blur

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture
import org.lwjgl.opengl.GL33C.glBindSampler

class DualKawaseBlur : AutoCloseable {

    private val downsampleShader by lazy {
        ShaderProgram.fromClasspath(
            "kawase_down",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/blur/kawase_down.fsh"
        )
    }

    private val upsampleShader by lazy {
        ShaderProgram.fromClasspath(
            "kawase_up",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/blur/kawase_up.fsh"
        )
    }

    private val pyramidFbos = ArrayList<FBO>()

    private fun ensurePyramid(width: Int, height: Int, iterations: Int) {
        while (pyramidFbos.size < iterations) {
            pyramidFbos.add(FBO(1, 1, hasDepthStencil = false))
        }

        var w = width
        var h = height
        for (i in 0 until iterations) {
            w = maxOf(1, w / 2)
            h = maxOf(1, h / 2)
            pyramidFbos[i].resize(w, h)
        }
    }

    fun process(input: FBO, output: FBO, iterations: Int = 4, offset: Float = 2.0f) {
        val count = iterations.coerceIn(1, 8)
        ensurePyramid(input.width, input.height, count)

        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glDisable(GL_CULL_FACE)
        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_BLEND)
        glColorMask(true, true, true, true)

        var currentInputTexture = input.textureId
        for (i in 0 until count) {
            val targetFbo = pyramidFbos[i]
            targetFbo.bind()

            downsampleShader.use {
                setSampler("uTexture", 0)
                setVec2("uHalfPixel", 0.5f / targetFbo.width, 0.5f / targetFbo.height)
                setFloat("uOffset", offset)

                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, currentInputTexture)
                glBindSampler(0, 0)

                FBOBlitter.renderQuad()

                glBindTexture(GL_TEXTURE_2D, 0)
            }

            currentInputTexture = targetFbo.textureId
        }

        for (i in count - 1 downTo 1) {
            val targetFbo = pyramidFbos[i - 1]
            targetFbo.bind()

            upsampleShader.use {
                setSampler("uTexture", 0)
                setVec2("uHalfPixel", 0.5f / targetFbo.width, 0.5f / targetFbo.height)
                setFloat("uOffset", offset)

                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, currentInputTexture)
                glBindSampler(0, 0)

                FBOBlitter.renderQuad()

                glBindTexture(GL_TEXTURE_2D, 0)
            }

            currentInputTexture = targetFbo.textureId
        }

        output.bind()
        upsampleShader.use {
            setSampler("uTexture", 0)
            setVec2("uHalfPixel", 0.5f / output.width, 0.5f / output.height)
            setFloat("uOffset", offset)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, currentInputTexture)
            glBindSampler(0, 0)

            FBOBlitter.renderQuad()

            glBindTexture(GL_TEXTURE_2D, 0)
        }
        output.unbind()
    }

    override fun close() {
        pyramidFbos.forEach { it.close() }
        pyramidFbos.clear()
        if (downsampleShader.isValid) downsampleShader.close()
        if (upsampleShader.isValid) upsampleShader.close()
    }
}

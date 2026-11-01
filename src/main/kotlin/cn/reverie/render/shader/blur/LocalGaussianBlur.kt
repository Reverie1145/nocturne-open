package cn.reverie.render.shader.blur

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture
import org.lwjgl.opengl.GL33C.glBindSampler
import kotlin.math.exp

/** Separable Gaussian blur for locally downsampled backdrop regions. */
class LocalGaussianBlur : AutoCloseable {
    private val shader by lazy {
        ShaderProgram.fromClasspath(
            "local_backdrop_blur",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/blur/local_backdrop.fsh"
        )
    }

    fun process(input: FBO, horizontal: FBO, output: FBO, radius: Float, downsample: Int) {
        val sigma = (radius.coerceAtLeast(1f) / (3f * downsample)).coerceAtLeast(0.5f)
        val offsets = FloatArray(4)
        val weights = FloatArray(4)
        for (pair in 0 until 4) {
            val first = (pair * 2 + 1).toFloat()
            val second = first + 1f
            val firstWeight = exp(-0.5f * first * first / (sigma * sigma))
            val secondWeight = exp(-0.5f * second * second / (sigma * sigma))
            val pairWeight = firstWeight + secondWeight
            weights[pair] = pairWeight
            offsets[pair] = if (pairWeight > 1e-8f) {
                (first * firstWeight + second * secondWeight) / pairWeight
            } else first
        }
        pass(input, horizontal, 1f, 0f, offsets, weights)
        pass(horizontal, output, 0f, 1f, offsets, weights)
    }

    private fun pass(input: FBO, output: FBO, dirX: Float, dirY: Float, offsets: FloatArray, weights: FloatArray) {
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glDisable(GL_CULL_FACE)
        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_BLEND)
        glColorMask(true, true, true, true)

        output.bind()
        shader.use {
            setSampler("uTexture", 0)
            setVec2("uTexelSize", 1f / input.width, 1f / input.height)
            setVec2("uDirection", dirX, dirY)
            setVec4("uOffsets", offsets[0], offsets[1], offsets[2], offsets[3])
            setVec4("uWeights", weights[0], weights[1], weights[2], weights[3])
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, input.textureId)
            glBindSampler(0, 0)
            FBOBlitter.renderQuad()
            glBindTexture(GL_TEXTURE_2D, 0)
        }
        output.unbind()
    }

    override fun close() {
        if (shader.isValid) shader.close()
    }
}

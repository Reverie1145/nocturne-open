package cn.reverie.render.shader.blur

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture
import kotlin.math.exp
import kotlin.math.sqrt

class GaussianBlur : AutoCloseable {

    private val shader by lazy {
        ShaderProgram.fromClasspath(
            "gaussian_blur",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/blur/gaussian.fsh"
        )
    }

    private var intermediateFbo: FBO? = null

    private fun calculateWeights(radius: Float, kernelSize: Int): FloatArray {
        val weights = FloatArray(kernelSize)
        val sigma = radius / 3.0f
        val twoSigmaSq = 2.0f * sigma * sigma
        var total = 0.0f

        for (i in 0 until kernelSize) {
            val weight = (1.0f / (sqrt(Math.PI * 2.0).toFloat() * sigma)) * exp(-(i * i).toFloat() / twoSigmaSq)
            weights[i] = weight
            total += if (i == 0) weight else weight * 2.0f
        }

        for (i in 0 until kernelSize) {
            weights[i] /= total
        }
        return weights
    }

    fun process(input: FBO, output: FBO, radius: Float = 8.0f) {
        val kernelSize = minOf(32, (radius * 1.5f).toInt() + 1).coerceAtLeast(3)
        val weights = calculateWeights(radius, kernelSize)

        if (intermediateFbo == null || intermediateFbo?.width != input.width || intermediateFbo?.height != input.height) {
            intermediateFbo?.close()
            intermediateFbo = FBO(input.width, input.height, hasDepthStencil = false)
        }
        val intermediate = intermediateFbo!!

        intermediate.bind()
        shader.use {
            setSampler("uTexture", 0)
            setVec2("uDirection", 1.0f / input.width, 0.0f)
            setFloat("uRadius", 1.0f)
            setInt("uKernelSize", kernelSize)
            setFloatArray("uWeights", weights)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, input.textureId)
            FBOBlitter.renderQuad()
            glBindTexture(GL_TEXTURE_2D, 0)
        }

        output.bind()
        shader.use {
            setSampler("uTexture", 0)
            setVec2("uDirection", 0.0f, 1.0f / input.height)
            setFloat("uRadius", 1.0f)
            setInt("uKernelSize", kernelSize)
            setFloatArray("uWeights", weights)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, intermediate.textureId)
            FBOBlitter.renderQuad()
            glBindTexture(GL_TEXTURE_2D, 0)
        }
        output.unbind()
    }

    override fun close() {
        intermediateFbo?.close()
        intermediateFbo = null
        if (shader.isValid) shader.close()
    }
}

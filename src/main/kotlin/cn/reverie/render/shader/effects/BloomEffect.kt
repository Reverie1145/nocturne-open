package cn.reverie.render.shader.effects

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.shader.ShaderProgram
import cn.reverie.render.shader.blur.DualKawaseBlur
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.*

class BloomEffect : AutoCloseable {

    private val extractShader by lazy {
        ShaderProgram.fromClasspath(
            "bloom_extract",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/effects/bloom_extract.fsh"
        )
    }

    private val compositeShader by lazy {
        ShaderProgram.fromClasspath(
            "bloom_composite",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/effects/bloom_composite.fsh"
        )
    }

    private val blur = DualKawaseBlur()
    private var extractFbo: FBO? = null
    private var blurFbo: FBO? = null

    fun render(input: FBO, output: FBO, threshold: Float = 0.8f, intensity: Float = 1.2f, exposure: Float = 1.0f) {
        if (extractFbo == null || extractFbo?.width != input.width || extractFbo?.height != input.height) {
            extractFbo?.close()
            blurFbo?.close()
            extractFbo = FBO(input.width / 2, input.height / 2, hasDepthStencil = false)
            blurFbo = FBO(input.width / 2, input.height / 2, hasDepthStencil = false)
        }

        val extract = extractFbo!!
        val blurred = blurFbo!!

        extract.bind()
        extractShader.use {
            setSampler("uTexture", 0)
            setFloat("uThreshold", threshold)
            setFloat("uSoftKnee", 0.5f)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, input.textureId)
            FBOBlitter.renderQuad()
            glBindTexture(GL_TEXTURE_2D, 0)
        }

        blur.process(extract, blurred, iterations = 4, offset = 2.5f)

        output.bind()
        compositeShader.use {
            setSampler("uBaseTexture", 0)
            setSampler("uBloomTexture", 1)
            setFloat("uBloomIntensity", intensity)
            setFloat("uExposure", exposure)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, input.textureId)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, blurred.textureId)

            FBOBlitter.renderQuad()

            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, 0)
        }
        output.unbind()
    }

    override fun close() {
        blur.close()
        extractFbo?.close()
        blurFbo?.close()
        if (extractShader.isValid) extractShader.close()
        if (compositeShader.isValid) compositeShader.close()
    }
}

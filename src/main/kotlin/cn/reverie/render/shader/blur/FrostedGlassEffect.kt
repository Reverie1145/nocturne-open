package cn.reverie.render.shader.blur

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.*

class FrostedGlassEffect : AutoCloseable {

    private val shader by lazy {
        ShaderProgram.fromClasspath(
            "frosted_glass",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/blur/frosted_glass.fsh"
        )
    }

    fun render(
        sharpTextureId: Int,
        blurredTextureId: Int,
        targetFbo: FBO?,
        tintColor: NanoVGColor = NanoVGColor(1f, 1f, 1f, 0.85f),
        blurMix: Float = 0.9f,
        noiseIntensity: Float = 0.03f,
        refraction: Float = 0.005f,
        time: Float = 0.0f
    ) {
        targetFbo?.bind() ?: org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0)

        shader.use {
            setSampler("uTexture", 0)
            setSampler("uBlurTexture", 1)
            setColor("uTintColor", tintColor)
            setFloat("uBlurMix", blurMix)
            setFloat("uNoiseIntensity", noiseIntensity)
            setFloat("uRefraction", refraction)
            setFloat("uTime", time)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, sharpTextureId)

            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, blurredTextureId)

            FBOBlitter.renderQuad()

            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, 0)
        }
    }

    override fun close() {
        if (shader.isValid) shader.close()
    }
}

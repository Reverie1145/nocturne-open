package cn.reverie.render.shader.effects

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture

class VignetteEffect : AutoCloseable {

    private val shader by lazy {
        ShaderProgram.fromClasspath(
            "vignette",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/effects/vignette.fsh"
        )
    }

    fun render(input: FBO, output: FBO, radius: Float = 0.75f, softness: Float = 0.45f, color: NanoVGColor = NanoVGColor(0f, 0f, 0f, 0.8f)) {
        output.bind()
        shader.use {
            setSampler("uTexture", 0)
            setVec2("uResolution", input.width.toFloat(), input.height.toFloat())
            setFloat("uRadius", radius)
            setFloat("uSoftness", softness)
            setColor("uVignetteColor", color)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, input.textureId)
            FBOBlitter.renderQuad()
            glBindTexture(GL_TEXTURE_2D, 0)
        }
        output.unbind()
    }

    override fun close() {
        if (shader.isValid) shader.close()
    }
}

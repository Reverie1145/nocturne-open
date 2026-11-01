package cn.reverie.render.shader.blur

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.math.Vec2f
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture

class DirectionalBlur : AutoCloseable {

    private val shader by lazy {
        ShaderProgram.fromClasspath(
            "directional_blur",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/blur/directional.fsh"
        )
    }

    fun process(input: FBO, output: FBO, direction: Vec2f, strength: Float = 0.01f, samples: Int = 10) {
        output.bind()
        shader.use {
            setSampler("uTexture", 0)
            setVec2("uDirection", direction.normalized())
            setInt("uSamples", samples.coerceIn(1, 30))
            setFloat("uStrength", strength)

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

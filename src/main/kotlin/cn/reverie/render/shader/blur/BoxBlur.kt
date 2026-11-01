package cn.reverie.render.shader.blur

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture

class BoxBlur : AutoCloseable {

    private val shader by lazy {
        ShaderProgram.fromClasspath(
            "box_blur",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/blur/directional.fsh"
        )
    }

    private var intermediateFbo: FBO? = null

    fun process(input: FBO, output: FBO, passes: Int = 3, radius: Float = 2.0f) {
        if (intermediateFbo == null || intermediateFbo?.width != input.width || intermediateFbo?.height != input.height) {
            intermediateFbo?.close()
            intermediateFbo = FBO(input.width, input.height, hasDepthStencil = false)
        }
        val intermediate = intermediateFbo!!

        var currentInput = input
        for (i in 0 until passes) {

            intermediate.bind()
            shader.use {
                setSampler("uTexture", 0)
                setVec2("uDirection", 1.0f, 0.0f)
                setInt("uSamples", 5)
                setFloat("uStrength", (radius / input.width) * (i + 1))

                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, currentInput.textureId)
                FBOBlitter.renderQuad()
                glBindTexture(GL_TEXTURE_2D, 0)
            }

            val target = if (i == passes - 1) output else currentInput
            target.bind()
            shader.use {
                setSampler("uTexture", 0)
                setVec2("uDirection", 0.0f, 1.0f)
                setInt("uSamples", 5)
                setFloat("uStrength", (radius / input.height) * (i + 1))

                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, intermediate.textureId)
                FBOBlitter.renderQuad()
                glBindTexture(GL_TEXTURE_2D, 0)
            }
            currentInput = target
        }
        output.unbind()
    }

    override fun close() {
        intermediateFbo?.close()
        intermediateFbo = null
        if (shader.isValid) shader.close()
    }
}

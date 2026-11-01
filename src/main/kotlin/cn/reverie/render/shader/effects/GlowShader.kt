package cn.reverie.render.shader.effects

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture

class GlowShader : AutoCloseable {

    private val vertexSource = """
        #version 330 core
        layout(location = 0) in vec2 aPos;
        layout(location = 1) in vec2 aTexCoord;
        out vec2 vTexCoord;
        void main() {
            vTexCoord = aTexCoord;
            gl_Position = vec4(aPos, 0.0, 1.0);
        }
    """.trimIndent()

    private val fragmentSource = """
        #version 330 core
        in vec2 vTexCoord;
        out vec4 fragColor;
        uniform sampler2D uTexture;
        uniform vec4 uGlowColor;
        uniform float uRadius;
        uniform int uSamples;
        void main() {
            float alpha = 0.0;
            float total = 0.0;
            for (int x = -uSamples; x <= uSamples; ++x) {
                for (int y = -uSamples; y <= uSamples; ++y) {
                    vec2 offset = vec2(float(x), float(y)) * uRadius;
                    float weight = 1.0 - length(vec2(float(x), float(y))) / float(uSamples + 1);
                    if (weight > 0.0) {
                        alpha += texture(uTexture, vTexCoord + offset).a * weight;
                        total += weight;
                    }
                }
            }
            alpha = alpha / total;
            fragColor = vec4(uGlowColor.rgb, uGlowColor.a * alpha);
        }
    """.trimIndent()

    private val shader by lazy {
        ShaderProgram("glow_shader", vertexSource, fragmentSource)
    }

    fun render(input: FBO, output: FBO, color: NanoVGColor, radius: Float = 0.005f, samples: Int = 5) {
        output.bind()
        shader.use {
            setSampler("uTexture", 0)
            setColor("uGlowColor", color)
            setFloat("uRadius", radius)
            setInt("uSamples", samples.coerceIn(1, 10))

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

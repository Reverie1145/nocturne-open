package cn.reverie.render.shader.effects

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture

class NoiseShader : AutoCloseable {

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
        uniform float uIntensity;
        uniform float uTime;
        float rand(vec2 co) {
            return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
        }
        void main() {
            vec4 col = texture(uTexture, vTexCoord);
            float noise = (rand(vTexCoord + vec2(uTime * 0.05)) - 0.5) * uIntensity;
            col.rgb += vec3(noise);
            fragColor = col;
        }
    """.trimIndent()

    private val shader by lazy {
        ShaderProgram("noise_shader", vertexSource, fragmentSource)
    }

    fun render(input: FBO, output: FBO, intensity: Float = 0.04f, time: Float = 0f) {
        output.bind()
        shader.use {
            setSampler("uTexture", 0)
            setFloat("uIntensity", intensity)
            setFloat("uTime", time)

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

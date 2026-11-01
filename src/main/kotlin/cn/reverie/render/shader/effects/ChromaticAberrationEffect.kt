package cn.reverie.render.shader.effects

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.shader.ShaderProgram
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture

class ChromaticAberrationEffect : AutoCloseable {

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
        uniform float uOffset;
        void main() {
            vec2 dir = vTexCoord - vec2(0.5);
            float dist = length(dir);
            vec2 offset = dir * (dist * uOffset);

            float r = texture(uTexture, vTexCoord + offset).r;
            float g = texture(uTexture, vTexCoord).g;
            float b = texture(uTexture, vTexCoord - offset).b;
            float a = texture(uTexture, vTexCoord).a;

            fragColor = vec4(r, g, b, a);
        }
    """.trimIndent()

    private val shader by lazy {
        ShaderProgram("chromatic_aberration", vertexSource, fragmentSource)
    }

    fun render(input: FBO, output: FBO, offset: Float = 0.005f) {
        output.bind()
        shader.use {
            setSampler("uTexture", 0)
            setFloat("uOffset", offset)

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

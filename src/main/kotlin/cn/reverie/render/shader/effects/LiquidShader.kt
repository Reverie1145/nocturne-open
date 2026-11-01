package cn.reverie.render.shader.effects

import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.shader.ShaderProgram

class LiquidShader : AutoCloseable {

    private val shader by lazy {
        ShaderProgram.fromClasspath(
            "liquid",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/effects/liquid.fsh"
        )
    }

    fun render(
        targetFbo: FBO?,
        time: Float,
        c1: NanoVGColor = NanoVGColor(0.12f, 0.15f, 0.35f, 1f),
        c2: NanoVGColor = NanoVGColor(0.35f, 0.12f, 0.30f, 1f),
        c3: NanoVGColor = NanoVGColor(0.05f, 0.05f, 0.12f, 1f)
    ) {
        targetFbo?.bind() ?: org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0)

        shader.use {
            val width = targetFbo?.width ?: ResolutionHelper.framebufferWidth
            val height = targetFbo?.height ?: ResolutionHelper.framebufferHeight
            setVec2("uResolution", width.toFloat(), height.toFloat())
            setFloat("uTime", time)
            setColor("uColor1", c1)
            setColor("uColor2", c2)
            setColor("uColor3", c3)

            FBOBlitter.renderQuad()
        }
    }

    override fun close() {
        if (shader.isValid) shader.close()
    }
}

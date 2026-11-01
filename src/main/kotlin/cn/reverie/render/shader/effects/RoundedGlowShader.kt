package cn.reverie.render.shader.effects

import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.math.Rect2f
import cn.reverie.render.math.Vec4f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.shader.ShaderProgram

class RoundedGlowShader : AutoCloseable {

    private val shader by lazy {
        ShaderProgram.fromClasspath(
            "rounded_glow",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/effects/rounded_glow.fsh"
        )
    }

    fun render(
        targetFbo: FBO?,
        rect: Rect2f,
        radii: Vec4f,
        color: NanoVGColor,
        blurRadius: Float,
        spread: Float = 0.0f
    ) {
        targetFbo?.bind() ?: org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0)

        shader.use {
            val width = targetFbo?.width ?: ResolutionHelper.framebufferWidth
            val height = targetFbo?.height ?: ResolutionHelper.framebufferHeight
            val scaleX = if (ResolutionHelper.scaledWidth > 0f) width / ResolutionHelper.scaledWidth else ResolutionHelper.framebufferScaleX
            val scaleY = if (ResolutionHelper.scaledHeight > 0f) height / ResolutionHelper.scaledHeight else ResolutionHelper.framebufferScaleY
            val radiusScale = (scaleX + scaleY) * 0.5f
            setVec2("uResolution", width.toFloat(), height.toFloat())
            setVec2("uPosition", rect.x * scaleX, (ResolutionHelper.scaledHeight - rect.y2) * scaleY)
            setVec2("uSize", rect.width * scaleX, rect.height * scaleY)
            setVec4("uRadii", radii * radiusScale)
            setColor("uColor", color)
            setFloat("uRadius", blurRadius * radiusScale)
            setFloat("uSpread", spread * radiusScale)

            FBOBlitter.renderQuad()
        }
    }

    override fun close() {
        if (shader.isValid) shader.close()
    }
}

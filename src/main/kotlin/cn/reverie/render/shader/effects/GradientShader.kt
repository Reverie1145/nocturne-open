package cn.reverie.render.shader.effects

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.math.Vec2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.shader.ShaderProgram

class GradientShader : AutoCloseable {

    private val shader by lazy {
        ShaderProgram.fromClasspath(
            "gradient",
            "/assets/reverie/shaders/core/passthrough.vsh",
            "/assets/reverie/shaders/effects/gradient.fsh"
        )
    }

    fun renderLinear(
        targetFbo: FBO?,
        pA: Vec2f, pB: Vec2f,
        cA: NanoVGColor, cB: NanoVGColor, cC: NanoVGColor = cB, cD: NanoVGColor = cC
    ) {
        renderGradient(targetFbo, 0, pA, pB, cA, cB, cC, cD, 0f)
    }

    fun renderRadial(
        targetFbo: FBO?,
        center: Vec2f, edgePoint: Vec2f,
        cA: NanoVGColor, cB: NanoVGColor, cC: NanoVGColor = cB, cD: NanoVGColor = cC
    ) {
        renderGradient(targetFbo, 1, center, edgePoint, cA, cB, cC, cD, 0f)
    }

    fun renderConic(
        targetFbo: FBO?,
        center: Vec2f, angle: Float,
        cA: NanoVGColor, cB: NanoVGColor, cC: NanoVGColor, cD: NanoVGColor
    ) {
        renderGradient(targetFbo, 2, center, Vec2f.ZERO, cA, cB, cC, cD, angle)
    }

    private fun renderGradient(
        targetFbo: FBO?,
        type: Int,
        pA: Vec2f, pB: Vec2f,
        cA: NanoVGColor, cB: NanoVGColor, cC: NanoVGColor, cD: NanoVGColor,
        angle: Float
    ) {
        targetFbo?.bind() ?: org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0)

        shader.use {
            setInt("uGradientType", type)
            setVec2("uPointA", pA)
            setVec2("uPointB", pB)
            setColor("uColorA", cA)
            setColor("uColorB", cB)
            setColor("uColorC", cC)
            setColor("uColorD", cD)
            setFloat("uAngle", angle)

            FBOBlitter.renderQuad()
        }
    }

    override fun close() {
        if (shader.isValid) shader.close()
    }
}

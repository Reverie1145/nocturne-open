package cn.reverie.render.gl

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL14C.*
import org.lwjgl.opengl.GL20C.*

enum class GLBlendMode(
    val srcRgb: Int,
    val dstRgb: Int,
    val srcAlpha: Int,
    val dstAlpha: Int,
    val eqRgb: Int = GL_FUNC_ADD,
    val eqAlpha: Int = GL_FUNC_ADD
) {
    ALPHA(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA),
    PREMULTIPLIED(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA),
    ADDITIVE(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE),
    SUBTRACTIVE(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE, GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_REVERSE_SUBTRACT),
    MULTIPLY(GL_DST_COLOR, GL_ZERO, GL_DST_ALPHA, GL_ZERO),
    SCREEN(GL_ONE, GL_ONE_MINUS_SRC_COLOR, GL_ONE, GL_ONE_MINUS_SRC_ALPHA),
    INVERT(GL_ONE_MINUS_DST_COLOR, GL_ZERO, GL_ONE_MINUS_DST_ALPHA, GL_ZERO),
    OVERWRITE(GL_ONE, GL_ZERO, GL_ONE, GL_ZERO);

    fun apply() {
        glEnable(GL_BLEND)
        glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
        glBlendEquationSeparate(eqRgb, eqAlpha)
    }

    companion object {
        fun disable() {
            glDisable(GL_BLEND)
        }
    }
}

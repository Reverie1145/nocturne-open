package cn.reverie.render.clipping

import org.lwjgl.opengl.GL11C.*

object StencilClip {

    @PublishedApi
    internal var stencilLevel = 0

    inline fun pushMask(drawMaskShape: () -> Unit) {
        if (stencilLevel == 0) {
            glEnable(GL_STENCIL_TEST)
            glClear(GL_STENCIL_BUFFER_BIT)
        }

        stencilLevel++
        glStencilFunc(GL_ALWAYS, stencilLevel, 0xFF)
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE)
        glStencilMask(0xFF)
        glColorMask(false, false, false, false)

        drawMaskShape()

        glColorMask(true, true, true, true)
        glStencilMask(0x00)
        glStencilFunc(GL_EQUAL, stencilLevel, 0xFF)
    }

    fun popMask() {
        if (stencilLevel > 0) {
            stencilLevel--
            if (stencilLevel == 0) {
                glDisable(GL_STENCIL_TEST)
            } else {
                glStencilFunc(GL_EQUAL, stencilLevel, 0xFF)
            }
        }
    }
}

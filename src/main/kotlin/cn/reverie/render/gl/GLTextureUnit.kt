package cn.reverie.render.gl

import org.lwjgl.opengl.GL11C.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11C.glBindTexture
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture

object GLTextureUnit {

    inline fun withUnit(unit: Int, textureId: Int, block: () -> Unit) {
        val targetUnit = GL_TEXTURE0 + unit
        glActiveTexture(targetUnit)
        glBindTexture(GL_TEXTURE_2D, textureId)
        try {
            block()
        } finally {
            glActiveTexture(targetUnit)
            glBindTexture(GL_TEXTURE_2D, 0)
        }
    }

    fun bind(unit: Int, textureId: Int) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, textureId)
    }

    fun unbind(unit: Int) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    fun reset() {
        glActiveTexture(GL_TEXTURE0)
    }
}

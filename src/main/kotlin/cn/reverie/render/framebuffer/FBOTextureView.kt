package cn.reverie.render.framebuffer

import org.lwjgl.opengl.GL11C.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11C.glBindTexture
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture

data class FBOTextureView(
    val textureId: Int,
    val width: Int,
    val height: Int
) {
    val u0: Float = 0f
    val v0: Float = 1f
    val u1: Float = 1f
    val v1: Float = 0f

    fun bind(unit: Int = 0) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, textureId)
    }

    fun unbind(unit: Int = 0) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, 0)
    }
}

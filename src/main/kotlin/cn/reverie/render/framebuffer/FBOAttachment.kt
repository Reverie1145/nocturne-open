package cn.reverie.render.framebuffer

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL30C.*

enum class FBOAttachment(
    val internalFormat: Int,
    val format: Int,
    val type: Int,
    val isDepth: Boolean = false,
    val isStencil: Boolean = false
) {
    RGBA8(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE),
    RGB8(GL_RGB8, GL_RGB, GL_UNSIGNED_BYTE),
    RGBA16F(GL_RGBA16F, GL_RGBA, GL_FLOAT),
    DEPTH24_STENCIL8(GL_DEPTH24_STENCIL8, GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8, isDepth = true, isStencil = true),
    DEPTH32F(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT, isDepth = true, isStencil = false)
}

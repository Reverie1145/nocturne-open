package cn.reverie.render.gl

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL30C.GL_INVALID_FRAMEBUFFER_OPERATION

object GLDebug {

    fun checkError(context: String = "GL Operation"): Boolean {
        val error = glGetError()
        if (error != GL_NO_ERROR) {
            val errorName = when (error) {
                GL_INVALID_ENUM -> "GL_INVALID_ENUM"
                GL_INVALID_VALUE -> "GL_INVALID_VALUE"
                GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
                GL_STACK_OVERFLOW -> "GL_STACK_OVERFLOW"
                GL_STACK_UNDERFLOW -> "GL_STACK_UNDERFLOW"
                GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
                GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
                else -> "UNKNOWN_ERROR_0x${Integer.toHexString(error)}"
            }
            System.err.println("[Nocturne-GL-Error] $context: $errorName (code $error)")
            return false
        }
        return true
    }

    inline fun assertNoError(context: String = "GL Operation", block: () -> Unit) {
        block()
        checkError(context)
    }
}

package cn.reverie.render.gl

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL20C.GL_MAX_TEXTURE_IMAGE_UNITS
import org.lwjgl.opengl.GL30C.GL_MAX_SAMPLES
import org.lwjgl.opengl.GL31C.GL_MAX_UNIFORM_BLOCK_SIZE

object GLCapabilities {

    val maxTextureSize: Int by lazy { glGetInteger(GL_MAX_TEXTURE_SIZE) }
    val maxTextureUnits: Int by lazy { glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS) }
    val maxMsaaSamples: Int by lazy { glGetInteger(GL_MAX_SAMPLES) }
    val maxUniformBlockSize: Int by lazy { glGetInteger(GL_MAX_UNIFORM_BLOCK_SIZE) }

    val supportsMsaa: Boolean get() = maxMsaaSamples > 1
    val supportsDebugOutput: Boolean by lazy { GL.getCapabilities().GL_KHR_debug || GL.getCapabilities().GL_ARB_debug_output }
    val supportsAnisotropy: Boolean by lazy { GL.getCapabilities().GL_EXT_texture_filter_anisotropic }

    fun printCapabilities() {
        println("[Nocturne-GL] Max Texture Size: ${maxTextureSize}x$maxTextureSize")
        println("[Nocturne-GL] Max Texture Units: $maxTextureUnits")
        println("[Nocturne-GL] Max MSAA Samples: $maxMsaaSamples")
        println("[Nocturne-GL] KHR_debug Supported: $supportsDebugOutput")
        println("[Nocturne-GL] Anisotropic Filtering: $supportsAnisotropy")
    }
}

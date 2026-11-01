package cn.reverie.render.gl

import org.lwjgl.opengl.GL11C.GL_RENDERER
import org.lwjgl.opengl.GL11C.GL_VENDOR
import org.lwjgl.opengl.GL11C.GL_VERSION
import org.lwjgl.opengl.GL11C.glGetString
import org.lwjgl.opengl.GL20C.GL_SHADING_LANGUAGE_VERSION

object GLVersion {

    val versionString: String by lazy { glGetString(GL_VERSION) ?: "Unknown" }
    val vendor: String by lazy { glGetString(GL_VENDOR) ?: "Unknown" }
    val renderer: String by lazy { glGetString(GL_RENDERER) ?: "Unknown" }
    val glslVersion: String by lazy { glGetString(GL_SHADING_LANGUAGE_VERSION) ?: "Unknown" }

    val majorVersion: Int by lazy {
        val parts = versionString.split(" ", ".").filter { it.all { c -> c.isDigit() } }
        parts.getOrNull(0)?.toIntOrNull() ?: 3
    }

    val minorVersion: Int by lazy {
        val parts = versionString.split(" ", ".").filter { it.all { c -> c.isDigit() } }
        parts.getOrNull(1)?.toIntOrNull() ?: 3
    }

    val isNvidia: Boolean by lazy { vendor.contains("NVIDIA", ignoreCase = true) }
    val isAmd: Boolean by lazy { vendor.contains("AMD", ignoreCase = true) || vendor.contains("ATI", ignoreCase = true) }
    val isIntel: Boolean by lazy { vendor.contains("Intel", ignoreCase = true) }

    fun isAtLeast(major: Int, minor: Int): Boolean {
        if (majorVersion > major) return true
        if (majorVersion == major) return minorVersion >= minor
        return false
    }
}

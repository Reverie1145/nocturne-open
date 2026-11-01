package cn.reverie.render.shader

import cn.reverie.render.math.Vec2f
import cn.reverie.render.math.Vec4f
import cn.reverie.render.nanovg.NanoVGColor
import org.lwjgl.opengl.GL20C.*
import org.lwjgl.system.MemoryStack
import java.nio.FloatBuffer

class ShaderUniform(val programId: Int) {

    private val uniformLocationCache = HashMap<String, Int>()

    fun getLocation(name: String): Int {
        return uniformLocationCache.getOrPut(name) {
            glGetUniformLocation(programId, name)
        }
    }

    fun setInt(name: String, value: Int) {
        val loc = getLocation(name)
        if (loc != -1) glUniform1i(loc, value)
    }

    fun setFloat(name: String, value: Float) {
        val loc = getLocation(name)
        if (loc != -1) glUniform1f(loc, value)
    }

    fun setVec2(name: String, x: Float, y: Float) {
        val loc = getLocation(name)
        if (loc != -1) glUniform2f(loc, x, y)
    }

    fun setVec2(name: String, vec: Vec2f) = setVec2(name, vec.x, vec.y)

    fun setVec3(name: String, x: Float, y: Float, z: Float) {
        val loc = getLocation(name)
        if (loc != -1) glUniform3f(loc, x, y, z)
    }

    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        val loc = getLocation(name)
        if (loc != -1) glUniform4f(loc, x, y, z, w)
    }

    fun setVec4(name: String, vec: Vec4f) = setVec4(name, vec.x, vec.y, vec.z, vec.w)

    fun setColor(name: String, color: NanoVGColor) = setVec4(name, color.r, color.g, color.b, color.a)

    fun setFloatArray(name: String, values: FloatArray) {
        val loc = getLocation(name)
        if (loc != -1) glUniform1fv(loc, values)
    }

    fun setMat4(name: String, matrix: FloatBuffer) {
        val loc = getLocation(name)
        if (loc != -1) glUniformMatrix4fv(loc, false, matrix)
    }

    fun setMat4(name: String, matrix: FloatArray) {
        MemoryStack.stackPush().use { stack ->
            val buf = stack.floats(*matrix)
            setMat4(name, buf)
        }
    }

    fun setSampler(name: String, unit: Int) = setInt(name, unit)
}

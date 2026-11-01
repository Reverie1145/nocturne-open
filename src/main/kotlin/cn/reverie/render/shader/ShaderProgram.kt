package cn.reverie.render.shader

import org.lwjgl.opengl.GL11C.GL_TRUE
import org.lwjgl.opengl.GL20C.*

class ShaderProgram(
    val name: String,
    val vertexSource: String,
    val fragmentSource: String
) : AutoCloseable {

    var programId: Int = 0
        private set

    val uniforms: ShaderUniform by lazy { ShaderUniform(programId) }
    val isValid: Boolean get() = programId != 0

    init {
        compileAndLink()
    }

    private fun compileAndLink() {
        val vertId = compileShader(GL_VERTEX_SHADER, vertexSource, "$name-vert")
        val fragId = compileShader(GL_FRAGMENT_SHADER, fragmentSource, "$name-frag")

        programId = glCreateProgram()
        glAttachShader(programId, vertId)
        glAttachShader(programId, fragId)
        glLinkProgram(programId)

        val linkStatus = glGetProgrami(programId, GL_LINK_STATUS)
        if (linkStatus != GL_TRUE) {
            val log = glGetProgramInfoLog(programId)
            glDeleteShader(vertId)
            glDeleteShader(fragId)
            glDeleteProgram(programId)
            programId = 0
            throw ShaderException("Link", name, log)
        }

        glDetachShader(programId, vertId)
        glDetachShader(programId, fragId)
        glDeleteShader(vertId)
        glDeleteShader(fragId)
    }

    private fun compileShader(type: Int, source: String, stageName: String): Int {
        val shaderId = glCreateShader(type)
        glShaderSource(shaderId, source)
        glCompileShader(shaderId)

        val status = glGetShaderi(shaderId, GL_COMPILE_STATUS)
        if (status != GL_TRUE) {
            val log = glGetShaderInfoLog(shaderId)
            glDeleteShader(shaderId)
            val typeStr = if (type == GL_VERTEX_SHADER) "Vertex" else "Fragment"
            throw ShaderException("Compile $typeStr", stageName, log, source)
        }
        return shaderId
    }

    fun bind() {
        if (isValid) {
            glUseProgram(programId)
        }
    }

    fun unbind() {
        glUseProgram(0)
    }

    inline fun use(block: ShaderUniform.() -> Unit) {
        bind()
        try {
            uniforms.block()
        } finally {
            unbind()
        }
    }

    override fun close() {
        if (isValid) {
            glDeleteProgram(programId)
            programId = 0
        }
    }

    companion object {
        fun fromClasspath(name: String, vertPath: String, fragPath: String): ShaderProgram {
            val vertSource = ShaderSource.loadFromClasspath(vertPath)
            val fragSource = ShaderSource.loadFromClasspath(fragPath)
            return ShaderProgram(name, vertSource, fragSource)
        }
    }
}

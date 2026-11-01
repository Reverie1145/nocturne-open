package cn.reverie.render.shader

import java.io.InputStreamReader

object ShaderSource {

    fun loadFromClasspath(path: String): String {
        val stream = javaClass.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Shader resource not found: $path")

        return stream.use { s ->
            InputStreamReader(s, Charsets.UTF_8).use { reader ->
                preprocess(reader.readText())
            }
        }
    }

    fun preprocess(source: String): String {
        val lines = source.lines()
        val processed = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#include")) {
                val includePath = trimmed.substringAfter("#include").trim().removeSurrounding("\"", "\"").removeSurrounding("<", ">")
                processed.appendLine("// BEGIN INCLUDE: $includePath")
                processed.appendLine(loadFromClasspath(includePath))
                processed.appendLine("// END INCLUDE: $includePath")
            } else {
                processed.appendLine(line)
            }
        }

        return processed.toString()
    }
}

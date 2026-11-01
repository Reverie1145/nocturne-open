package cn.reverie.render.shader

class ShaderException(
    val stage: String,
    val sourceName: String,
    val infoLog: String,
    val sourceCode: String? = null
) : RuntimeException(buildMessage(stage, sourceName, infoLog, sourceCode)) {

    companion object {
        private fun buildMessage(stage: String, name: String, log: String, source: String?): String {
            val sb = StringBuilder()
            sb.appendLine("GLSL $stage Error in shader [$name]:")
            sb.appendLine(log.trim())
            if (source != null) {
                sb.appendLine("--- Source Code with Line Numbers ---")
                source.lines().forEachIndexed { index, line ->
                    sb.appendLine(String.format("%4d | %s", index + 1, line))
                }
            }
            return sb.toString()
        }
    }
}

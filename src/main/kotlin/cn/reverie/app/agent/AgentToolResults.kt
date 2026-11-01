package cn.reverie.app.agent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.util.UUID

/** Immutable, session-scoped outputs. Paging never re-executes the original tool. */
internal class AgentToolResults(private val files: AgentFiles, private val maxResults: Int = 32,
                                private val maxSessionBytes: Long = 16_000_000) {
    @Synchronized fun archive(sessionId: String, call: AgentToolCall, text: String, error: Boolean, json: Boolean): JsonObject {
        val id = UUID.randomUUID().toString()
        val metadata = jsonObject("sessionId" to sessionId, "resultId" to id, "callId" to call.id, "tool" to call.name,
            "createdAt" to System.currentTimeMillis(), "format" to if (json) "JSON" else "TEXT",
            "hash" to AgentTools.sourceHash(text), "totalChars" to text.length, "error" to error)
        val record = metadata.deepCopy().apply { addProperty("text", text) }
        val content = record.toString()
        require(content.toByteArray(Charsets.UTF_8).size <= minOf(FILE_LIMIT.toLong(), maxSessionBytes)) { "完整工具结果超过本地存档容量" }
        files.write(path(sessionId, id), content, FILE_LIMIT)
        trim(sessionId, id)
        return metadata.apply {
            remove("sessionId")
            addProperty("resultState", "archived")
            addProperty("preview", prefix(text, 1200))
            add("readWith", jsonObject("tool" to "read_tool_result", "arguments" to jsonObject("resultId" to id, "offset" to 0, "limit" to 8000)))
            addProperty("note", if (error) "本次调用返回错误，完整结果已保存。先分页读取错误并核实实际状态，不假设操作成功，也不要重复修改。" else
                "原工具已经执行，完整结果已保存。需要详情时分页读取；不要为读取结果重复执行原工具。")
        }
    }
    @Synchronized fun read(sessionId: String, args: JsonObject): JsonObject {
        val id = args.string("resultId")
        val raw = files.read(path(sessionId, id), FILE_LIMIT.toLong()) ?: error("完整工具结果不存在或已被清理；不要重新执行修改工具，请读取当前实际状态")
        val record = JsonParser.parseString(raw).asJsonObject
        require(record.string("sessionId") == sessionId && record.string("resultId") == id) { "工具结果不属于当前会话" }
        val text = record.remove("text").asString
        require(record.string("hash") == AgentTools.sourceHash(text)) { "工具结果存档已变化；请读取当前实际状态" }
        record.remove("sessionId")
        return AgentToolPaging.source(text, record, args).apply { add("text", remove("source")) }
    }
    @Synchronized fun delete(sessionId: String) {
        val directory = files.path("tool-results/${AgentFiles.requireId(sessionId)}/.keep").parent
        if (!Files.exists(directory)) return
        // No recursive deletion or link traversal: this directory contains only our result files.
        Files.list(directory).use { paths -> paths.toList().forEach { item ->
            val name = item.fileName.toString()
            if (name.endsWith(".json") && runCatching { AgentFiles.requireId(name.removeSuffix(".json")) }.isSuccess)
                Files.deleteIfExists(files.path("tool-results/$sessionId/$name"))
        } }
        if (Files.list(directory).use { !it.findAny().isPresent }) Files.deleteIfExists(directory)
    }
    private fun path(sessionId: String, id: String) = "tool-results/${AgentFiles.requireId(sessionId)}/${AgentFiles.requireId(id)}.json"
    private fun trim(sessionId: String, newest: String) {
        val directory = files.path(path(sessionId, newest)).parent
        val records = Files.list(directory).use { stream -> stream.filter { item ->
            val name = item.fileName.toString()
            name.endsWith(".json") && runCatching { AgentFiles.requireId(name.removeSuffix(".json")) }.isSuccess
        }.map { files.path("tool-results/$sessionId/" + it.fileName) }.toList() }
            .sortedBy { Files.getLastModifiedTime(it) }
        var bytes = records.sumOf { Files.size(it) }; var count = records.size
        records.forEach { item ->
            if ((bytes > maxSessionBytes || count > maxResults) && item.fileName.toString() != "$newest.json") {
                bytes -= Files.size(item); Files.deleteIfExists(item); count--
            }
        }
    }
    companion object {
        const val INLINE_LIMIT = 24_000
        const val FILE_LIMIT = 4_000_000
        internal fun prefix(text: String, max: Int): String {
            var end = minOf(text.length, max)
            if (end in 1 until text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            return text.substring(0, end)
        }
    }
}

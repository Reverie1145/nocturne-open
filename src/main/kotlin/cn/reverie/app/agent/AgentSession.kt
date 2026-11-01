package cn.reverie.app.agent

import java.util.UUID
import com.google.gson.annotations.SerializedName

class AgentSession(
    @field:SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @field:SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @field:SerializedName("title") var title: String = "新会话",
    @field:SerializedName("messages") internal val messages: MutableList<AgentMessage> = mutableListOf(),
    @field:SerializedName("entries") internal val entries: MutableList<AgentEntry> = mutableListOf(),
    @field:SerializedName("tasks") internal val tasks: MutableList<AgentTask> = mutableListOf(),
    @field:SerializedName("summary") internal var summary: String = "",
    @field:SerializedName("protocol") internal var protocol: AgentProtocol? = null,
    @field:SerializedName("totalTokens") var totalTokens: Long = 0,
    @field:SerializedName("providerKey") internal var providerKey: String = ""
) {
    @Synchronized fun entriesSnapshot(): List<AgentEntry> = entries.toList()
    @Synchronized fun tasksSnapshot(): List<AgentTask> = tasks.toList()
    @Synchronized internal fun addEntry(kind: String, text: String, callId: String? = null, tool: String? = null) {
        entries += AgentEntry(kind = kind, text = text.take(100000), callId = callId, tool = tool)
        while (entries.size > 300) entries.removeAt(0)
        var characters = entries.sumOf { it.text.length }
        while (characters > 1_500_000 && entries.size > 1) characters -= entries.removeAt(0).text.length
    }
    @Synchronized internal fun updateTask(id: Int, title: String, status: String): AgentTask {
        require(title.length in 1..200 && status in setOf("pending", "in_progress", "completed"))
        if (id == 0) {
            require(tasks.size < 100)
            return AgentTask((tasks.maxOfOrNull { it.id } ?: 0) + 1, title, status).also { tasks += it }
        }
        val index = tasks.indexOfFirst { it.id == id }; require(index >= 0) { "任务不存在" }
        return AgentTask(id, title, status).also { tasks[index] = it }
    }
}

data class AgentSessionInfo(val id: String, val title: String, val createdAt: Long)
class AgentSessionStore(private val files: AgentFiles) {
    internal val results = AgentToolResults(files)
    @Synchronized fun delete(id: String) {
        AgentFiles.requireId(id)
        results.delete(id)
        java.nio.file.Files.deleteIfExists(files.path("sessions/$id.json"))
        if (files.read("last-session.txt", 100)?.trim() == id)
            java.nio.file.Files.deleteIfExists(files.path("last-session.txt"))
    }
    fun save(session: AgentSession) = synchronized(session) {
        AgentFiles.requireId(session.id)
        files.write("sessions/${session.id}.json", agentGson.toJson(session), 4_000_000)
        files.write("last-session.txt", session.id, 100)
    }
    fun load(id: String): AgentSession {
        AgentFiles.requireId(id)
        val raw = files.read("sessions/$id.json", 4_000_000) ?: error("会话不存在")
        val session = agentGson.fromJson(raw, AgentSession::class.java)
        require(session.id == id)
        AgentContext.repair(session.messages)
        return session
    }
    fun latest(): AgentSession = files.read("last-session.txt", 100)?.trim()?.let { load(it) } ?: AgentSession()
    fun list(): List<AgentSessionInfo> {
        val dir = files.path("sessions/.keep").parent
        java.nio.file.Files.createDirectories(dir)
        return java.nio.file.Files.list(dir).use { paths -> paths.filter { it.fileName.toString().endsWith(".json") }
            .map { path -> runCatching { load(path.fileName.toString().removeSuffix(".json")) }
                .getOrNull()?.let { AgentSessionInfo(it.id, it.title, it.createdAt) } }
            .filter { it != null }.toList().filterNotNull().sortedByDescending { it.createdAt }.take(50) }
    }
}

/** Never split a tool-call/result group when trimming, compacting, recovering or resuming. */
internal object AgentContext {
    fun repair(messages: MutableList<AgentMessage>) {
        val repaired = mutableListOf<AgentMessage>()
        var pending = linkedMapOf<String, AgentToolCall>()
        for (message in messages) {
            if (message.role != "tool" && pending.isNotEmpty()) {
                pending.values.forEach { repaired += AgentMessage("tool", "上次执行已中断，未取得结果；不要假设已成功。", callId = it.id, error = true) }
                pending.clear()
            }
            if (message.role == "tool") {
                if (pending.remove(message.callId) != null) repaired += message
            } else {
                repaired += message
                message.calls.forEach { pending[it.id] = it }
            }
        }
        pending.values.forEach { repaired += AgentMessage("tool", "上次执行已中断，未取得结果；不要假设已成功。", callId = it.id, error = true) }
        messages.clear(); messages.addAll(repaired)
    }
    fun groups(messages: List<AgentMessage>): List<List<AgentMessage>> {
        val groups = mutableListOf<MutableList<AgentMessage>>()
        messages.forEach { message ->
            if (groups.isEmpty() || message.role == "assistant" || message.role == "user") groups += mutableListOf<AgentMessage>()
            groups.last() += message
        }
        return groups
    }
    fun size(messages: List<AgentMessage>): Int = messages.sumOf { agentGson.toJson(it).length }
    fun compactSplit(messages: List<AgentMessage>, targetChars: Int): Pair<List<AgentMessage>, List<AgentMessage>> {
        val groups = groups(messages)
        var keepFrom = groups.size
        var size = 0
        while (keepFrom > 0) {
            val next = size(groups[keepFrom - 1])
            if (size + next > targetChars && keepFrom < groups.size - 1) break
            size += next; keepFrom--
        }
        return groups.take(keepFrom).flatten() to groups.drop(keepFrom).flatten()
    }
}

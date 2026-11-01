package cn.reverie.app.agent.model

import cn.reverie.app.agent.*
import cn.reverie.app.agent.auth.OpenAiOAuthError
import com.google.gson.*
import java.io.BufferedReader
import java.io.StringReader

/** SSE framing is independent of HTTP packet boundaries and preserves split UTF-8 characters. */
internal object AgentSse {
    fun read(reader: BufferedReader, cancellation: AgentCancellation, event: (JsonObject) -> Unit) {
        val data = StringBuilder()
        var type = ""
        var count = 0L
        fun dispatch() {
            val payload = data.toString(); data.setLength(0)
            if (payload.isNotBlank() && payload != "[DONE]") {
                val value = JsonParser.parseString(payload).asJsonObject
                if (value.string("type").isBlank() && type.isNotEmpty()) value.addProperty("type", type)
                event(value)
            }
            type = ""
        }
        while (true) {
            cancellation.check()
            val line = reader.readLine()?.removePrefix("\uFEFF") ?: break
            count += line.length + 1
            require(count <= 4_000_000) { "模型响应超过 4 MB" }
            when {
                line.isEmpty() -> dispatch()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substring(5).removePrefix(" "))
                }
                line.startsWith("event:") -> type = line.substring(6).trim()
            }
        }
        dispatch()
        cancellation.check()
    }
}

/** Reconstructs output from deltas/items as well as the terminal envelope. Never runs partial tools. */
internal class ResponseAccumulator(private val update: (AgentLiveResponse) -> Unit = {}) {
    private val items = sortedMapOf<Int, JsonObject>()
    private var response: JsonObject? = null
    private val startedAt = System.currentTimeMillis()
    private fun item(event: JsonObject, kind: String = "message"): JsonObject = items.getOrPut(event.int("output_index")) {
        jsonObject("type" to kind).apply {
            if (event.string("item_id").isNotEmpty()) addProperty("id", event.string("item_id"))
            if (kind == "message") { addProperty("role", "assistant"); add("content", JsonArray()) }
        }
    }
    private fun part(event: JsonObject, kind: String): JsonObject {
        val item = item(event)
        val content = item.getAsJsonArray("content") ?: JsonArray().also { item.add("content", it) }
        val index = event.int("content_index")
        require(index in 0..1024) { "响应内容索引无效" }
        while (content.size() <= index) content.add(JsonObject())
        return content[index].asJsonObject.apply { if (string("type").isEmpty()) addProperty("type", kind) }
    }
    fun accept(event: JsonObject) {
        require(event.int("output_index") in 0..1024) { "响应输出索引无效" }
        when (event.string("type")) {
            "response.output_item.added", "response.output_item.done" -> {
                val incoming = event.getAsJsonObject("item") ?: error("响应缺少输出项")
                val index = event.int("output_index")
                items[index] = merge(items[index], incoming)
            }
            "response.content_part.added", "response.content_part.done" -> {
                val incoming = event.getAsJsonObject("part") ?: error("响应缺少内容项")
                val current = part(event, incoming.string("type"))
                val merged = merge(current, incoming)
                item(event).getAsJsonArray("content").set(event.int("content_index"), merged)
            }
            "response.output_text.delta", "response.refusal.delta" -> {
                val refusal = event.string("type") == "response.refusal.delta"
                val target = part(event, if (refusal) "refusal" else "output_text")
                val key = if (refusal) "refusal" else "text"
                target.addProperty(key, target.string(key) + event.string("delta"))
            }
            "response.output_text.done", "response.refusal.done" -> {
                val refusal = event.string("type") == "response.refusal.done"
                val key = if (refusal) "refusal" else "text"
                part(event, if (refusal) "refusal" else "output_text").addProperty(key, event.string(key))
            }
            "response.function_call_arguments.delta", "response.function_call_arguments.done" -> {
                val target = item(event, "function_call")
                target.addProperty("arguments", if (event.string("type").endsWith(".done")) event.string("arguments")
                    else target.string("arguments") + event.string("delta"))
                for (key in listOf("name", "call_id", "namespace")) if (event.has(key)) target.add(key, event.get(key).deepCopy())
            }
            "response.completed", "response.incomplete" -> {
                require(response == null) { "OpenAI 返回了重复终止事件" }
                val terminal = event.getAsJsonObject("response") ?: error("OpenAI 终止事件缺少响应")
                require(terminal.string("status") == if (event.string("type") == "response.completed") "completed" else "incomplete")
                terminal.getAsJsonArray("output")?.forEachIndexed { index, raw ->
                    val incoming = raw.asJsonObject
                    val matched = items.entries.firstOrNull { incoming.string("id").isNotEmpty() && it.value.string("id") == incoming.string("id") }?.key ?: index
                    items[matched] = merge(items[matched], incoming)
                }
                response = terminal.deepCopy().apply { add("output", JsonArray().apply { items.values.forEach { raw ->
                    add(raw.deepCopy().apply {
                        if (string("id").isEmpty()) remove("id")
                        if (string("type") in setOf("message", "function_call") && string("status") in setOf("", "in_progress"))
                            addProperty("status", terminal.string("status"))
                        getAsJsonArray("content")?.forEach { content -> content.asJsonObject.let { block ->
                            if (block.string("type") == "output_text" && !block.has("annotations")) block.add("annotations", JsonArray())
                        } }
                    })
                } }) }
            }
            "response.failed", "response.cancelled", "error" -> {
                val error = event.getAsJsonObject("response")?.getAsJsonObject("error") ?: event.getAsJsonObject("error") ?: event
                val code = error.string("code", "generation_failed")
                throw OpenAiOAuthError(code.takeIf { it.matches(Regex("[a-zA-Z0-9_]{1,100}")) } ?: "generation_failed")
            }
        }
        publish()
    }
    private fun publish() {
        val text = StringBuilder()
        val calls = mutableListOf<AgentToolCall>()
        items.values.forEach { item -> when (item.string("type")) {
            "message" -> item.getAsJsonArray("content")?.forEach { raw ->
                val part = raw.asJsonObject
                text.append(if (part.string("type") == "refusal") part.string("refusal") else part.string("text"))
            }
            "function_call" -> calls += AgentToolCall(item.string("call_id", item.string("id")), item.string("name"),
                jsonObject("preview" to item.string("arguments").take(6000)))
        } }
        update(AgentLiveResponse(text.toString().take(100000), calls, startedAt))
    }
    fun finish(): JsonObject = response ?: error("模型响应流中断，未收到完成事件；工具未执行")

    private fun merge(previous: JsonObject?, incoming: JsonObject): JsonObject {
        val merged = previous?.deepCopy() ?: JsonObject()
        incoming.entrySet().forEach { (key, value) ->
            if (value.isJsonArray && value.asJsonArray.isEmpty && merged.has(key)) return@forEach
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isEmpty() && merged.has(key)) return@forEach
            if (key == "content" && value.isJsonArray && merged.get(key)?.isJsonArray == true) {
                val content = merged.getAsJsonArray(key)
                value.asJsonArray.forEachIndexed { index, block ->
                    if (index < content.size()) content.set(index, merge(content[index].asJsonObject, block.asJsonObject)) else content.add(block.deepCopy())
                }
            } else merged.add(key, value.deepCopy())
        }
        return merged
    }
}

internal object OpenAiResponseStream {
    fun terminal(body: String): JsonObject = ResponseAccumulator().apply {
        AgentSse.read(BufferedReader(StringReader(body)), AgentCancellation(), ::accept)
    }.finish()
}

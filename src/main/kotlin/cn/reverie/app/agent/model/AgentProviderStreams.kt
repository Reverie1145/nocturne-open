package cn.reverie.app.agent.model

import cn.reverie.app.agent.*
import com.google.gson.*
import java.io.BufferedReader

internal object AgentProviderStreams {
    fun read(protocol: AgentProtocol, reader: BufferedReader, cancel: AgentCancellation,
             update: (AgentLiveResponse) -> Unit): JsonObject {
        if (protocol == AgentProtocol.RESPONSES) {
            val accumulator = ResponseAccumulator(update)
            AgentSse.read(reader, cancel, accumulator::accept)
            return accumulator.finish()
        }
        val content = sortedMapOf<Int, JsonObject>()
        val text = StringBuilder()
        var finish = ""
        var stopped = false
        var usage = JsonObject()
        val startedAt = System.currentTimeMillis()
        fun publish() {
            val calls = content.values.mapNotNull { block ->
                when (block.string("type")) {
                    "function" -> AgentToolCall(block.string("id"), block.getAsJsonObject("function").string("name"),
                        jsonObject("preview" to block.getAsJsonObject("function").string("arguments").take(6000)))
                    "tool_use" -> AgentToolCall(block.string("id"), block.string("name"), jsonObject("preview" to block.string("_arguments").take(6000)))
                    else -> null
                }
            }
            val value = if (protocol == AgentProtocol.CHAT_COMPLETIONS) text.toString()
                else content.values.filter { it.string("type") == "text" }.joinToString("") { it.string("text") }
            update(AgentLiveResponse(value.take(100000), calls, startedAt))
        }
        AgentSse.read(reader, cancel) { event ->
            if (event.has("error") || event.string("type") == "error") error("模型流返回错误：" +
                (event.getAsJsonObject("error")?.string("type") ?: "generation_failed").take(100))
            if (protocol == AgentProtocol.CHAT_COMPLETIONS) {
                event.getAsJsonObject("usage")?.let { usage = it }
                val choice = event.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                if (choice != null) {
                    choice.string("finish_reason").takeIf { it.isNotBlank() }?.let { finish = it; stopped = true }
                    choice.getAsJsonObject("delta")?.let { delta ->
                        text.append(delta.string("content")).append(delta.string("refusal"))
                        delta.getAsJsonArray("tool_calls")?.forEach { raw ->
                            val incoming = raw.asJsonObject
                            val index = incoming.int("index")
                            require(index in 0..31) { "工具调用数量过多" }
                            val call = content.getOrPut(index) { jsonObject("type" to "function", "id" to "", "function" to jsonObject("name" to "", "arguments" to "")) }
                            if (incoming.string("id").isNotEmpty()) call.addProperty("id", incoming.string("id"))
                            incoming.getAsJsonObject("function")?.let { fn ->
                                val target = call.getAsJsonObject("function")
                                for (key in listOf("name", "arguments")) if (fn.has(key)) target.addProperty(key, target.string(key) + fn.string(key))
                            }
                        }
                    }
                }
            } else when (event.string("type")) {
                "message_start" -> event.getAsJsonObject("message")?.getAsJsonObject("usage")?.let { usage = it.deepCopy() }
                "content_block_start" -> {
                    val index = event.int("index"); require(index in 0..1024)
                    content[index] = event.getAsJsonObject("content_block").deepCopy()
                }
                "content_block_delta" -> {
                    val block = content[event.int("index")] ?: error("缺少流内容块")
                    val delta = event.getAsJsonObject("delta") ?: JsonObject()
                    when (delta.string("type")) {
                        "text_delta" -> block.addProperty("text", block.string("text") + delta.string("text"))
                        "input_json_delta" -> block.addProperty("_arguments", block.string("_arguments") + delta.string("partial_json"))
                        "thinking_delta" -> block.addProperty("thinking", block.string("thinking") + delta.string("thinking"))
                        "signature_delta" -> block.addProperty("signature", block.string("signature") + delta.string("signature"))
                    }
                }
                "message_delta" -> {
                    finish = event.getAsJsonObject("delta")?.string("stop_reason") ?: finish
                    event.getAsJsonObject("usage")?.entrySet()?.forEach { (key, value) -> usage.add(key, value) }
                }
                "message_stop" -> stopped = true
            }
            publish()
        }
        require(stopped && finish.isNotEmpty()) { "模型响应流中断，未收到完成事件；工具未执行" }
        return if (protocol == AgentProtocol.CHAT_COMPLETIONS) jsonObject(
            "choices" to listOf(jsonObject("finish_reason" to finish, "message" to jsonObject(
                "role" to "assistant", "content" to text.toString(), "tool_calls" to content.values.toList()))), "usage" to usage)
        else {
            content.values.forEach { block ->
                block.remove("_arguments")?.asString?.let { arguments -> block.add("input", JsonParser.parseString(arguments)) }
            }
            jsonObject("content" to content.values.toList(), "stop_reason" to finish, "usage" to usage)
        }
    }
}

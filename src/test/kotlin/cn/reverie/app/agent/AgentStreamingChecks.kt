package cn.reverie.app.agent

import cn.reverie.app.agent.model.*
import com.google.gson.*
import com.sun.net.httpserver.HttpServer
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.*
import kotlin.test.*

class AgentStreamingChecks {
    private fun event(value: JsonObject) = "event: " + value.string("type") + "\ndata: " + value.toString() + "\n\n"
    @Test fun sparseTerminalKeepsDeltaTextAndFinalToolItemsWithoutDuplicatingOutput() {
        val text = "你好🙂 **完成**"
        val values = listOf(
            jsonObject("type" to "response.output_item.added", "output_index" to 0, "item" to jsonObject("type" to "message", "id" to "m1", "role" to "assistant", "content" to emptyList<Any>())),
            jsonObject("type" to "response.output_text.delta", "output_index" to 0, "item_id" to "m1", "content_index" to 0, "delta" to text),
            jsonObject("type" to "response.output_text.done", "output_index" to 0, "item_id" to "m1", "content_index" to 0, "text" to text),
            jsonObject("type" to "response.output_item.done", "output_index" to 1, "item" to jsonObject("type" to "function_call", "id" to "fc1", "call_id" to "c1", "namespace" to "nocturne", "name" to "create_script", "arguments" to "{\"id\":\"new\"}")),
            jsonObject("type" to "response.completed", "response" to jsonObject("status" to "completed", "output" to emptyList<Any>(), "usage" to jsonObject("total_tokens" to 19)))
        )
        val reply = HttpAgentModel.parseReply(AgentProtocol.RESPONSES, OpenAiResponseStream.terminal(values.joinToString("", transform = ::event)))
        assertEquals(text, reply.message.text); assertEquals("create_script", reply.message.calls.single().name)
        assertEquals("new", reply.message.calls.single().arguments.string("id")); assertEquals(19, reply.tokens)
        val fullTerminal = values.last().deepCopy().apply { getAsJsonObject("response").add("output", JsonArray().apply {
            add(jsonObject("type" to "message", "id" to "m1", "content" to listOf(jsonObject("type" to "output_text", "text" to text))))
            add(values[3].getAsJsonObject("item"))
        }) }
        val full = HttpAgentModel.parseReply(AgentProtocol.RESPONSES, OpenAiResponseStream.terminal((values.dropLast(1) + fullTerminal).joinToString("", transform = ::event)))
        assertEquals(text, full.message.text); assertEquals(1, full.message.calls.size)
    }
    @Test fun streamedArgumentsAndEncryptedReasoningRemainPaired() {
        val values = listOf(
            jsonObject("type" to "response.output_item.done", "output_index" to 0, "item" to jsonObject("type" to "reasoning", "id" to "r", "summary" to emptyList<Any>(), "encrypted_content" to "opaque-fixture")),
            jsonObject("type" to "response.output_item.added", "output_index" to 1, "item" to jsonObject("type" to "function_call", "id" to "fc", "call_id" to "c", "namespace" to "nocturne", "name" to "read_script", "arguments" to "")),
            jsonObject("type" to "response.function_call_arguments.delta", "output_index" to 1, "delta" to "{\"id\":"),
            jsonObject("type" to "response.function_call_arguments.delta", "output_index" to 1, "delta" to "\"sample\"}"),
            jsonObject("type" to "response.completed", "response" to jsonObject("status" to "completed", "output" to emptyList<Any>()))
        )
        val reply = HttpAgentModel.parseReply(AgentProtocol.RESPONSES, OpenAiResponseStream.terminal(values.joinToString("", transform = ::event)))
        assertEquals("sample", reply.message.calls.single().arguments.string("id"))
        assertEquals("opaque-fixture", reply.message.providerItems!!.first().asJsonObject.string("encrypted_content"))
        assertTrue(reply.message.text.isEmpty())
    }
    @Test fun refusalAndHeaderOnlyEventTypesAreRenderedAsText() {
        val source = "event: response.refusal.delta\ndata: {\"delta\":\"无法完成\"}\n\n" +
            "event: response.completed\ndata: {\"response\":{\"status\":\"completed\",\"output\":[]}}\n\n"
        assertEquals("无法完成", HttpAgentModel.parseReply(AgentProtocol.RESPONSES, OpenAiResponseStream.terminal(source)).message.text)
    }
    @Test fun textArrivesBeforeCompletionOverRealHttpForAllProtocolsAndSplitUtf8IsIntact() {
        for (protocol in AgentProtocol.entries) {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val seen = CountDownLatch(1); val release = CountDownLatch(1)
            val failures = CopyOnWriteArrayList<Throwable>()
            val expected = "中文🙂 **流式输出**"
            server.createContext("/") { exchange ->
                runCatching {
                    val request = JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject
                    assertTrue(request.boolean("stream"))
                    exchange.responseHeaders.set("Content-Type", "text/event-stream")
                    exchange.sendResponseHeaders(200, 0)
                    val initial = when (protocol) {
                        AgentProtocol.RESPONSES -> event(jsonObject("type" to "response.output_text.delta", "delta" to expected))
                        AgentProtocol.CHAT_COMPLETIONS -> "data: " + jsonObject("choices" to listOf(jsonObject("delta" to jsonObject("content" to expected)))) + "\n\n"
                        AgentProtocol.ANTHROPIC -> event(jsonObject("type" to "message_start", "message" to jsonObject("usage" to jsonObject("input_tokens" to 3)))) +
                            event(jsonObject("type" to "content_block_start", "index" to 0, "content_block" to jsonObject("type" to "text", "text" to ""))) +
                            event(jsonObject("type" to "content_block_delta", "index" to 0, "delta" to jsonObject("type" to "text_delta", "text" to expected)))
                    }
                    initial.toByteArray(Charsets.UTF_8).forEach { byte -> exchange.responseBody.write(byte.toInt() and 255); exchange.responseBody.flush() }
                    assertTrue(release.await(3, TimeUnit.SECONDS))
                    val terminal = when (protocol) {
                        AgentProtocol.RESPONSES -> event(jsonObject("type" to "response.completed", "response" to jsonObject("status" to "completed", "output" to emptyList<Any>(), "usage" to jsonObject("total_tokens" to 25))))
                        AgentProtocol.CHAT_COMPLETIONS -> "data: " + jsonObject("choices" to listOf(jsonObject("delta" to JsonObject(), "finish_reason" to "stop")), "usage" to jsonObject("total_tokens" to 25)) + "\n\ndata: [DONE]\n\n"
                        AgentProtocol.ANTHROPIC -> event(jsonObject("type" to "message_delta", "delta" to jsonObject("stop_reason" to "end_turn"), "usage" to jsonObject("output_tokens" to 22))) +
                            event(jsonObject("type" to "message_stop"))
                    }
                    exchange.responseBody.write(terminal.toByteArray(Charsets.UTF_8)); exchange.responseBody.close()
                }.onFailure { failures += it; exchange.close() }
            }
            server.start()
            val worker = Executors.newSingleThreadExecutor()
            try {
                val model = HttpAgentModel { "" }
                val result = worker.submit<AgentReply> { model.stream(
                    AgentSettings("http://127.0.0.1:" + server.address.port + "/v1", "fixture", protocol),
                    listOf(AgentMessage("user", "work")), JsonArray(), AgentCancellation()) { if (it.text == expected) seen.countDown() } }
                assertTrue(seen.await(3, TimeUnit.SECONDS), protocol.name)
                assertFalse(result.isDone, "Buffered instead of streamed")
                release.countDown()
                val reply = result.get(3, TimeUnit.SECONDS)
                assertEquals(expected, reply.message.text); assertEquals(25, reply.tokens)
                assertTrue(failures.isEmpty(), failures.toString())
            } finally { release.countDown(); worker.shutdownNow(); server.stop(0) }
        }
    }
    @Test fun aBrokenStreamNeverExecutesToolsAndKeepsVisiblePartialOutput() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val source = event(jsonObject("type" to "response.output_text.delta", "delta" to "部分内容")) +
                event(jsonObject("type" to "response.output_item.done", "output_index" to 1, "item" to jsonObject("type" to "function_call", "id" to "fc", "call_id" to "call", "name" to "mutate", "arguments" to "{}")))
            exchange.responseHeaders.set("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0); exchange.responseBody.use { it.write(source.toByteArray()) }
        }
        server.start()
        try {
            var executed = false
            val tools = AgentTools().apply { register(AgentTool("mutate", "fixture", AgentTools.schema(), true) { _, _ -> executed = true; JsonNull.INSTANCE }) }
            AgentEngine(HttpAgentModel { "" }, { AgentSettings("http://127.0.0.1:" + server.address.port + "/v1", "fixture", permissionMode = AgentPermissionMode.AUTO) },
                tools, AgentSessionStore(AgentFiles(Files.createTempDirectory("partial-stream")))).use { engine ->
                assertEquals(AgentState.FAILED, engine.submit("work").get(3, TimeUnit.SECONDS))
                assertFalse(executed); assertNull(engine.live)
                assertEquals("部分内容", engine.session.entriesSnapshot().first { it.kind == "partial" }.text)
                assertTrue(engine.session.messages.none { it.role == "assistant" || it.calls.isNotEmpty() })
            }
        } finally { server.stop(0) }
    }
    @Test fun providerStreamsRejectMissingTerminalAndDecodeMultipleToolArguments() {
        val missing = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"
        assertFailsWith<IllegalArgumentException> { AgentProviderStreams.read(AgentProtocol.CHAT_COMPLETIONS, BufferedReader(StringReader(missing)), AgentCancellation()) {} }
        val event = jsonObject("choices" to listOf(jsonObject("delta" to jsonObject("tool_calls" to listOf(
            jsonObject("index" to 0, "id" to "a", "function" to jsonObject("name" to "read", "arguments" to "{\"id\":")),
            jsonObject("index" to 1, "id" to "b", "function" to jsonObject("name" to "list", "arguments" to "{}")))))))
        val done = jsonObject("choices" to listOf(jsonObject("delta" to jsonObject("tool_calls" to listOf(
            jsonObject("index" to 0, "function" to jsonObject("arguments" to "\"sample\"}")))), "finish_reason" to "tool_calls")))
        val json = AgentProviderStreams.read(AgentProtocol.CHAT_COMPLETIONS, BufferedReader(StringReader("data: " + event + "\n\ndata: " + done + "\n\n")), AgentCancellation()) {}
        val reply = HttpAgentModel.parseReply(AgentProtocol.CHAT_COMPLETIONS, json)
        assertEquals(listOf("a", "b"), reply.message.calls.map { it.id }); assertEquals("sample", reply.message.calls.first().arguments.string("id"))
    }
}

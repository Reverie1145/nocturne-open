package cn.reverie.app.agent

import cn.reverie.app.agent.model.HttpAgentModel
import com.google.gson.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** Local HTTP fixtures exercise the real serializers, parser, transport and tool loop without credentials. */
class AgentModelIntegrationChecks {
    @Test fun allThreeProtocolsCompleteAnActualHttpToolRoundTrip() {
        for (protocol in AgentProtocol.entries) {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val requests = AtomicInteger(); val executions = AtomicInteger()
            val failures = CopyOnWriteArrayList<Throwable>()
            server.createContext("/") { exchange ->
                val step = requests.incrementAndGet()
                val body = runCatching {
                    val request = JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject
                    assertEquals("test-model", request.string("model"))
                    assertEquals(if (protocol == AgentProtocol.ANTHROPIC) "test-key" else "Bearer test-key",
                        exchange.requestHeaders.getFirst(if (protocol == AgentProtocol.ANTHROPIC) "x-api-key" else "Authorization"))
                    if (step == 2) when (protocol) {
                        AgentProtocol.CHAT_COMPLETIONS -> {
                            val last = request.getAsJsonArray("messages").last().asJsonObject
                            assertEquals("tool", last.string("role")); assertEquals("call-1", last.string("tool_call_id")); assertEquals("1", last.string("content"))
                        }
                        AgentProtocol.RESPONSES -> {
                            val input = request.getAsJsonArray("input")
                            assertTrue(input.any { it.asJsonObject.string("type") == "reasoning" })
                            val last = input.last().asJsonObject
                            assertEquals("function_call_output", last.string("type")); assertEquals("call-1", last.string("call_id")); assertEquals("1", last.string("output"))
                        }
                        AgentProtocol.ANTHROPIC -> {
                            val last = request.getAsJsonArray("messages").last().asJsonObject
                            assertEquals("user", last.string("role"))
                            val result = last.getAsJsonArray("content").first().asJsonObject
                            assertEquals("tool_result", result.string("type")); assertEquals("call-1", result.string("tool_use_id")); assertEquals("1", result.string("content"))
                        }
                    }
                    when (protocol) {
                        AgentProtocol.CHAT_COMPLETIONS -> if (step == 1) """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"checking","tool_calls":[{"type":"function","id":"call-1","function":{"name":"increment","arguments":"{}"}}]}}],"usage":{"total_tokens":30}}"""
                            else """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"done"}}],"usage":{"total_tokens":40}}"""
                        AgentProtocol.RESPONSES -> if (step == 1) """{"status":"completed","output":[{"type":"reasoning","id":"reason-1","summary":[],"encrypted_content":"fixture"},{"type":"function_call","id":"fc-1","call_id":"call-1","name":"increment","arguments":"{}"}],"usage":{"total_tokens":30}}"""
                            else """{"status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"done"}]}],"usage":{"total_tokens":40}}"""
                        AgentProtocol.ANTHROPIC -> if (step == 1) """{"stop_reason":"tool_use","content":[{"type":"text","text":"checking"},{"type":"tool_use","id":"call-1","name":"increment","input":{}}],"usage":{"input_tokens":20,"output_tokens":10}}"""
                            else """{"stop_reason":"end_turn","content":[{"type":"text","text":"done"}],"usage":{"input_tokens":20,"output_tokens":20}}"""
                    }
                }.getOrElse { failures += it; """{"error":{"message":"fixture failed"}}""" }
                exchange.responseHeaders.add("Content-Type", "application/json")
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(if (failures.isEmpty()) 200 else 400, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            try {
                val options = AgentSettings("http://127.0.0.1:${server.address.port}/v1", "test-model", protocol, AgentPermissionMode.AUTO)
                val tools = AgentTools().apply { register(AgentTool("increment", "count", AgentTools.schema()) { _, _ -> JsonPrimitive(executions.incrementAndGet()) }) }
                val store = AgentSessionStore(AgentFiles(Files.createTempDirectory("agent-http")))
                AgentEngine(HttpAgentModel { "test-key" }, { options }, tools, store).use { engine ->
                    assertEquals(AgentState.COMPLETED, engine.submit("work").get(5, TimeUnit.SECONDS), engine.session.entriesSnapshot().toString())
                    assertEquals(1, executions.get()); assertEquals(2, requests.get()); assertEquals(70, engine.turnTokens)
                    assertEquals("done", engine.session.messages.last().text)
                }
                assertTrue(failures.isEmpty(), failures.toString())
            } finally { server.stop(0) }
        }
    }
    @Test fun httpCancellationStopsPendingSampling() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val received = CountDownLatch(1); val release = CountDownLatch(1)
        server.createContext("/") { exchange -> received.countDown(); release.await(3, TimeUnit.SECONDS); exchange.close() }
        server.start()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val cancel = AgentCancellation()
            val result = worker.submit<AgentReply> {
                HttpAgentModel { "" }.complete(AgentSettings("http://127.0.0.1:${server.address.port}/v1", "test-model"), listOf(AgentMessage("user", "hello")), JsonArray(), cancel)
            }
            assertTrue(received.await(3, TimeUnit.SECONDS))
            cancel.cancel()
            val failure = assertFailsWith<ExecutionException> { result.get(2, TimeUnit.SECONDS) }
            assertIs<CancellationException>(failure.cause)
        } finally { release.countDown(); worker.shutdownNow(); server.stop(0) }
    }
    @Test fun serviceErrorsCannotExposeTheApiKey() {
        val secret = "test-only-sensitive-value"
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val error = jsonObject("error" to jsonObject("message" to "invalid credential: $secret")).toString().toByteArray()
            exchange.sendResponseHeaders(401, error.size.toLong()); exchange.responseBody.use { it.write(error) }
        }
        server.start()
        try {
            val failure = assertFailsWith<IllegalStateException> {
                HttpAgentModel { secret }.complete(AgentSettings("http://127.0.0.1:${server.address.port}/v1", "test-model"), listOf(AgentMessage("user", "hello")), JsonArray(), AgentCancellation())
            }
            assertFalse(failure.message.orEmpty().contains(secret)); assertTrue(failure.message.orEmpty().contains("[REDACTED]"))
        } finally { server.stop(0) }
    }
    @Test fun malformedToolArgumentsAreReturnedAsAValidatableErrorAndDuplicateIdsFail() {
        val malformed = JsonParser.parseString("""{"choices":[{"message":{"tool_calls":[{"id":"one","type":"function","function":{"name":"read","arguments":"{broken"}}]}}]}""").asJsonObject
        val reply = HttpAgentModel.parseReply(AgentProtocol.CHAT_COMPLETIONS, malformed)
        assertEquals("one", reply.message.calls.single().id)
        assertTrue(reply.message.calls.single().arguments.has("__invalid_arguments"))
        val duplicate = JsonParser.parseString("""{"output":[{"type":"function_call","call_id":"one","name":"a","arguments":"{}"},{"type":"function_call","call_id":"one","name":"b","arguments":"{}"}]}""").asJsonObject
        assertFailsWith<IllegalArgumentException> { HttpAgentModel.parseReply(AgentProtocol.RESPONSES, duplicate) }
    }
}

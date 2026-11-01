package cn.reverie.app.agent

import com.google.gson.*
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class AgentToolResultsChecks {
    @Test fun oversizedSingleListItemStaysValidAndCanBeReadAfterRestartWithoutReplayingMutation() {
        val files = AgentFiles(Files.createTempDirectory("agent-long-result"))
        val store = AgentSessionStore(files); val executions = AtomicInteger()
        val binding = jsonObject("id" to "large-module", "settings" to (0 until 24).map {
            jsonObject("name" to "setting-" + it, "value" to "😀\"\\\n".repeat(600))
        })
        val expected = AgentToolPaging.list(listOf(binding), JsonObject()).toString()
        assertTrue(expected.length > AgentToolResults.INLINE_LIMIT)
        var resultId = ""; var sessionId = ""
        var round = 0
        val tools = AgentTools().apply {
            register(AgentTool("create_large", "large fixture", AgentTools.schema(), true) { _, _ ->
                executions.incrementAndGet(); JsonParser.parseString(expected)
            })
        }
        val first = AgentModel { _, messages, _, _ ->
            if (round++ == 0) AgentReply(AgentMessage("assistant", calls = listOf(AgentToolCall("created", "create_large", JsonObject()))), 1)
            else {
                val archived = JsonParser.parseString(messages.last().text).asJsonObject
                assertEquals("archived", archived.string("resultState")); assertEquals("JSON", archived.string("format"))
                assertEquals(AgentTools.sourceHash(expected), archived.string("hash"))
                assertTrue(messages.last().text.length < AgentToolResults.INLINE_LIMIT)
                resultId = archived.string("resultId")
                AgentReply(AgentMessage("assistant", "saved"), 1)
            }
        }
        AgentEngine(first, { AgentSettings(permissionMode = AgentPermissionMode.AUTO) }, tools, store).use { engine ->
            assertEquals(AgentState.COMPLETED, engine.submit("create").get(5, TimeUnit.SECONDS))
            sessionId = engine.session.id
        }
        val text = StringBuilder(); var readRound = 0
        val resumed = AgentModel { _, messages, specs, _ ->
            if (messages.first().text.startsWith("总结")) return@AgentModel AgentReply(AgentMessage("assistant", "完整结果 $resultId 正在分页读取，已执行的创建操作不要重放。"), 1)
            assertTrue(specs.any { it.asJsonObject.string("name") == "read_tool_result" })
            var offset = 0
            if (readRound++ > 0) {
                val page = JsonParser.parseString(messages.last().text).asJsonObject
                assertEquals(resultId, page.string("resultId")); assertEquals("created", page.string("callId"))
                assertEquals(AgentTools.sourceHash(expected), page.string("hash"))
                assertFalse(page.string("text").lastOrNull()?.isHighSurrogate() ?: false)
                text.append(page.string("text")); offset = page.int("endOffset")
                if (!page.boolean("hasMore")) return@AgentModel AgentReply(AgentMessage("assistant", "read complete"), 1)
            }
            AgentReply(AgentMessage("assistant", calls = listOf(AgentToolCall("page-" + readRound, "read_tool_result",
                jsonObject("resultId" to resultId, "offset" to offset, "limit" to 8001)))), 1)
        }
        AgentEngine(resumed, { AgentSettings(maxRounds = 64, contextChars = 400000, permissionMode = AgentPermissionMode.READ_ONLY) },
            AgentTools(), store).use { engine ->
            assertEquals(sessionId, engine.session.id)
            assertEquals(AgentState.COMPLETED, engine.submit("read complete output").get(8, TimeUnit.SECONDS), engine.session.entriesSnapshot().takeLast(2).toString())
            assertEquals(expected, text.toString()); assertEquals(1, executions.get())
            assertEquals(JsonParser.parseString(expected), JsonParser.parseString(text.toString()))
            engine.deleteSession(sessionId)
            assertFalse(Files.exists(files.path("tool-results/$sessionId/.keep").parent))
        }
    }

    @Test fun resultRetentionIsBoundedImmutableAndScopedToItsSession() {
        val files = AgentFiles(Files.createTempDirectory("agent-result-retention"))
        val results = AgentToolResults(files, maxResults = 2, maxSessionBytes = 6000)
        val session = UUID.randomUUID().toString()
        val first = results.archive(session, AgentToolCall("one", "list", JsonObject()), "😀".repeat(600), false, false)
        val second = results.archive(session, AgentToolCall("two", "list", JsonObject()), "b".repeat(2000), false, false)
        val third = results.archive(session, AgentToolCall("three", "list", JsonObject()), "c".repeat(2000), false, false)
        fun args(record: JsonObject) = jsonObject("resultId" to record.string("resultId"))
        assertFailsWith<IllegalStateException> { results.read(session, args(first)) }
        assertEquals("b".repeat(2000), results.read(session, args(second)).string("text"))
        assertEquals("c".repeat(2000), results.read(session, args(third)).string("text"))
        assertFailsWith<IllegalStateException> { results.read(UUID.randomUUID().toString(), args(third)) }
        assertFailsWith<IllegalArgumentException> { results.read(session, jsonObject("resultId" to "../outside")) }
        val path = files.path("tool-results/$session/" + third.string("resultId") + ".json")
        val changed = JsonParser.parseString(Files.readString(path)).asJsonObject.apply { addProperty("text", "changed") }
        Files.writeString(path, changed.toString())
        assertFailsWith<IllegalArgumentException> { results.read(session, args(third)) }
    }

    @Test fun storageFailureReportsTheExecutedOperationWithoutEncouragingDuplicateWrites() {
        val files = AgentFiles(Files.createTempDirectory("agent-result-failure"))
        Files.writeString(files.path("tool-results"), "blocks directory creation")
        val executions = AtomicInteger(); var round = 0
        val tools = AgentTools().apply { register(AgentTool("write", "fixture", AgentTools.schema(), true) { _, _ ->
            executions.incrementAndGet(); JsonPrimitive("large".repeat(6000))
        }) }
        val model = AgentModel { _, messages, _, _ ->
            if (round++ == 0) AgentReply(AgentMessage("assistant", calls = listOf(AgentToolCall("write-once", "write", JsonObject()))), 1)
            else {
                val result = JsonParser.parseString(messages.last().text).asJsonObject
                assertEquals("archive_failed", result.string("resultState")); assertFalse(messages.last().error)
                assertContains(result.string("note"), "原工具已经执行"); assertContains(result.string("note"), "不要重新执行")
                AgentReply(AgentMessage("assistant", "inspect current state"), 1)
            }
        }
        AgentEngine(model, { AgentSettings(permissionMode = AgentPermissionMode.AUTO) }, tools, AgentSessionStore(files)).use { engine ->
            assertEquals(AgentState.COMPLETED, engine.submit("write").get(5, TimeUnit.SECONDS))
            assertEquals(1, executions.get())
        }
    }
}

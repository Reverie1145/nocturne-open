package cn.reverie.app.agent

import com.google.gson.*
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class AgentEngineChecks {
    private fun store() = AgentSessionStore(AgentFiles(Files.createTempDirectory("nocturne-agent-test")))
    private fun call(id: String, name: String) = AgentToolCall(id, name, JsonObject())
    private fun reply(vararg calls: AgentToolCall, text: String = "", tokens: Int = 50, truncated: Boolean = false) =
        AgentReply(AgentMessage("assistant", text, calls.toList()), tokens, truncated)
    private fun tools(counter: AtomicInteger, mutating: Boolean = false) = AgentTools().apply {
        register(AgentTool("increment", "test counter", AgentTools.schema(), mutating) { _, cancellation -> cancellation.check(); JsonPrimitive(counter.incrementAndGet()) })
    }
    private fun waitFor(block: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!block() && System.nanoTime() < until) Thread.sleep(10)
        assertTrue(block())
    }
    @Test fun multipleToolResultsFeedTheNextModelRoundIncludingErrors() {
        val counter = AtomicInteger()
        val round = AtomicInteger()
        val model = AgentModel { _, messages, _, _ ->
            if (round.incrementAndGet() == 1) reply(call("one", "increment"), call("two", "unknown"))
            else {
                assertEquals(listOf("one", "two"), messages.takeLast(2).map { it.callId })
                assertFalse(messages[messages.size - 2].error); assertTrue(messages.last().error)
                reply(text = "done")
            }
        }
        AgentEngine(model, { AgentSettings(permissionMode = AgentPermissionMode.AUTO) }, tools(counter), store()).use { engine ->
            assertEquals(AgentState.COMPLETED, engine.submit("work").get(3, TimeUnit.SECONDS))
            assertEquals(1, counter.get()); assertEquals(2, round.get()); assertEquals(100, engine.turnTokens)
        }
    }
    @Test fun readOnlyRejectsAnUnadvertisedMutation() {
        val counter = AtomicInteger(); val round = AtomicInteger()
        val model = AgentModel { _, messages, specs, _ ->
            assertFalse(specs.any { it.asJsonObject.string("name") == "increment" })
            assertTrue(specs.any { it.asJsonObject.string("name") == "read_tool_result" })
            if (round.incrementAndGet() == 1) reply(call("denied", "increment"))
            else { assertTrue(messages.last().error); reply(text = "read only") }
        }
        AgentEngine(model, { AgentSettings(permissionMode = AgentPermissionMode.READ_ONLY) }, tools(counter, true), store()).use { engine ->
            assertEquals(AgentState.COMPLETED, engine.submit("try mutation").get(3, TimeUnit.SECONDS))
            assertEquals(0, counter.get())
        }
    }
    @Test fun rejectingApprovalReturnsAnErrorWithoutExecuting() {
        val counter = AtomicInteger(); val round = AtomicInteger()
        val model = AgentModel { _, messages, _, _ ->
            if (round.incrementAndGet() == 1) reply(call("ask", "increment"))
            else { assertTrue(messages.last().error); reply(text = "rejected") }
        }
        AgentEngine(model, { AgentSettings() }, tools(counter, true), store()).use { engine ->
            val outcome = engine.submit("request")
            waitFor { engine.permissions.current() != null }
            assertTrue(engine.permissions.answer(engine.permissions.current()!!.id, false))
            assertEquals(AgentState.COMPLETED, outcome.get(3, TimeUnit.SECONDS))
            assertEquals(0, counter.get()); assertNull(engine.permissions.current())
        }
    }
    @Test fun cancellationWhileWaitingPairsAllCallsAndCanResume() {
        val counter = AtomicInteger(); val round = AtomicInteger(); val sessionStore = store()
        val model = AgentModel { _, _, _, _ ->
            if (round.incrementAndGet() == 1) reply(call("first", "increment"), call("second", "increment")) else reply(text = "continued")
        }
        AgentEngine(model, { AgentSettings() }, tools(counter, true), sessionStore).use { engine ->
            val outcome = engine.submit("work")
            waitFor { engine.permissions.current() != null }
            engine.stop()
            assertEquals(AgentState.CANCELLED, outcome.get(3, TimeUnit.SECONDS))
            assertEquals(0, counter.get()); assertNull(engine.permissions.current())
            assertEquals(listOf("first", "second"), engine.session.messages.filter { it.role == "tool" }.map { it.callId })
            val restored = sessionStore.load(engine.session.id)
            assertEquals(engine.session.messages.size, restored.messages.size)
            assertEquals(AgentState.COMPLETED, engine.submit("continue").get(3, TimeUnit.SECONDS))
        }
    }
    @Test fun approvalAllowsExactlyTheReviewedCall() {
        val counter = AtomicInteger(); val round = AtomicInteger()
        val model = AgentModel { _, _, _, _ -> if (round.incrementAndGet() == 1) reply(call("approved", "increment")) else reply(text = "done") }
        AgentEngine(model, { AgentSettings() }, tools(counter, true), store()).use { engine ->
            val outcome = engine.submit("work")
            waitFor { engine.permissions.current() != null }
            assertFalse(engine.permissions.answer("stale-id", true))
            assertEquals(0, counter.get())
            engine.permissions.answer(engine.permissions.current()!!.id, true)
            assertEquals(AgentState.COMPLETED, outcome.get(3, TimeUnit.SECONDS))
            assertEquals(1, counter.get())
        }
    }
    @Test fun budgetsAndTruncatedOutputPreventPendingMutations() {
        for (truncated in listOf(false, true)) {
            val counter = AtomicInteger()
            val model = AgentModel { _, _, _, _ -> reply(call("limited", "increment"), tokens = if (truncated) 20 else 1000, truncated = truncated) }
            AgentEngine(model, { AgentSettings(tokenBudget = 1000, permissionMode = AgentPermissionMode.AUTO) }, tools(counter, true), store()).use { engine ->
                assertEquals(AgentState.LIMIT_REACHED, engine.submit("work").get(3, TimeUnit.SECONDS))
                assertEquals(0, counter.get()); assertTrue(engine.session.messages.last().error)
            }
        }
    }
    @Test fun roundLimitStopsAnEndlessToolLoopWithPairedResults() {
        val counter = AtomicInteger(); val sequence = AtomicInteger()
        val model = AgentModel { _, _, _, _ -> reply(call("call-${sequence.incrementAndGet()}", "increment")) }
        AgentEngine(model, { AgentSettings(maxRounds = 2, permissionMode = AgentPermissionMode.AUTO) }, tools(counter, true), store()).use { engine ->
            assertEquals(AgentState.LIMIT_REACHED, engine.submit("work").get(3, TimeUnit.SECONDS))
            assertEquals(2, counter.get()); assertEquals(2, engine.session.messages.count { it.role == "tool" })
        }
    }
    @Test fun resumeRepairsInterruptedCallsAndRemovesOrphanResults() {
        val messages = mutableListOf(AgentMessage("user", "goal"), AgentMessage("assistant", calls = listOf(call("one", "increment"), call("two", "increment"))),
            AgentMessage("tool", "success", callId = "one"), AgentMessage("user", "continue"), AgentMessage("tool", "orphan", callId = "unknown"))
        AgentContext.repair(messages)
        assertEquals(listOf("one", "two"), messages.filter { it.role == "tool" }.map { it.callId })
        assertTrue(messages[3].error); assertEquals("continue", messages.last().text)
    }
    @Test fun compactionKeepsCompleteToolGroupsAndPersistsSummary() {
        val sessionStore = store(); val counter = AtomicInteger()
        val model = AgentModel { _, messages, _, _ ->
            if (messages.first().text.startsWith("总结")) reply(text = "retained user goal and completed operation") else reply(text = "done")
        }
        AgentEngine(model, { AgentSettings(contextChars = 12000) }, tools(counter), sessionStore).use { engine ->
            engine.session.messages += AgentMessage("user", "goal")
            repeat(8) { i -> engine.session.messages += AgentMessage("assistant", calls = listOf(call("c$i", "increment")))
                engine.session.messages += AgentMessage("tool", "x".repeat(1800), callId = "c$i") }
            assertEquals(AgentState.COMPLETED, engine.submit("continue").get(3, TimeUnit.SECONDS))
            assertTrue(engine.session.summary.contains("retained"))
            val restored = sessionStore.load(engine.session.id)
            assertEquals(engine.session.summary, restored.summary)
            val calls = restored.messages.flatMap { it.calls }.map { it.id }
            val results = restored.messages.filter { it.role == "tool" }.map { it.callId }
            assertEquals(calls, results)
        }
    }
    @Test fun providerContextSurvivesResumeOnlyForTheSameAccountAndModel() {
        val sessionStore = store(); val sequence = AtomicInteger()
        var identity = "account-one:model-one"
        val model = AgentModel { _, messages, _, _ ->
            val turn = sequence.incrementAndGet()
            if (turn == 2) assertNotNull(messages.first { it.role == "assistant" }.providerItems)
            if (turn == 3) {
                assertTrue(messages.all { it.providerItems == null })
                assertEquals(listOf("reply-1", "reply-2"), messages.filter { it.role == "assistant" }.map { it.text })
            }
            AgentReply(AgentMessage("assistant", "reply-" + turn, providerItems = JsonArray().apply {
                add(jsonObject("type" to "reasoning", "encrypted_content" to "opaque-" + turn))
            }), 10)
        }
        var sessionId = ""
        AgentEngine(model, { AgentSettings() }, tools(AtomicInteger()), sessionStore, providerIdentity = { identity }).use { engine ->
            assertEquals(AgentState.COMPLETED, engine.submit("first").get(3, TimeUnit.SECONDS))
            sessionId = engine.session.id
        }
        AgentEngine(model, { AgentSettings() }, tools(AtomicInteger()), sessionStore, providerIdentity = { identity }).use { engine ->
            assertEquals(sessionId, engine.session.id)
            assertEquals(AgentState.COMPLETED, engine.submit("resume").get(3, TimeUnit.SECONDS))
            identity = "account-two:model-one"
            assertEquals(AgentState.COMPLETED, engine.submit("another account").get(3, TimeUnit.SECONDS))
            assertEquals(identity, sessionStore.load(sessionId).providerKey)
        }
    }
    @Test fun accountSwitchDuringToolsStopsFurtherModelRequestsAndCanResume() {
        val sequence = AtomicInteger(); val sessionStore = store()
        var identity = "account-one"
        val model = AgentModel { _, _, _, _ ->
            if (sequence.incrementAndGet() == 1) reply(call("switch", "switch_account")) else reply(text = "resumed")
        }
        val tools = AgentTools().apply {
            register(AgentTool("switch_account", "test", AgentTools.schema()) { _, _ ->
                identity = "account-two"; JsonPrimitive("changed")
            })
        }
        AgentEngine(model, { AgentSettings() }, tools, sessionStore, providerIdentity = { identity }).use { engine ->
            assertEquals(AgentState.FAILED, engine.submit("first").get(3, TimeUnit.SECONDS))
            assertEquals(1, sequence.get())
            assertEquals("switch", engine.session.messages.last().callId)
            assertEquals(AgentState.COMPLETED, engine.submit("resume").get(3, TimeUnit.SECONDS))
            assertEquals(2, sequence.get())
        }
    }
}

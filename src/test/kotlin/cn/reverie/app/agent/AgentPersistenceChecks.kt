package cn.reverie.app.agent

import cn.reverie.app.agent.auth.OpenAiOAuth
import cn.reverie.app.agent.model.AgentModelCatalog
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.*
import kotlin.test.*

class AgentPersistenceChecks {
    private fun files() = AgentFiles(Files.createTempDirectory("agent-persistence"))
    @Test fun connectionProfilesKeysAndActiveModeSurviveRestartIndependently() {
        val files = files()
        val store = AgentSettingsStore(files)
        val api = AgentSettings(endpoint = "https://example.test/v1", model = "api-model", protocol = AgentProtocol.CHAT_COMPLETIONS,
            apiKeyEnvironment = "NOCTURNE_TEST_UNSET_ENV", customInstructions = "API instructions")
        val plan = AgentSettings(model = "plan-model", authMode = AgentAuthMode.OPENAI_OAUTH, customInstructions = "Plan instructions", timeoutSeconds = 120)
        store.save(api, "test-only-api-secret")
        store.switch(AgentAuthMode.OPENAI_OAUTH); store.save(plan, "ignored-plan-key")
        var restored = AgentSettingsStore(files)
        assertEquals(plan, restored.settings); assertEquals(api, restored.profile(AgentAuthMode.API_KEY))
        assertEquals("test-only-api-secret", restored.apiKey())
        assertFalse(files.read("settings.json")!!.contains("test-only-api-secret"))
        assertFalse(files.read("api-key.enc")!!.contains("test-only-api-secret"))
        restored.switch(AgentAuthMode.API_KEY)
        restored = AgentSettingsStore(files)
        assertEquals(api, restored.settings); assertEquals(plan, restored.profile(AgentAuthMode.OPENAI_OAUTH))
    }
    @Test fun legacySettingsMigrateAndAnEmptyModelCanBeSavedBeforeTheCatalogIsLoaded() {
        for (mode in AgentAuthMode.entries) {
            val files = files()
            val old = AgentSettings(model = "legacy", authMode = mode)
            files.write("settings.json", agentGson.toJson(old))
            val store = AgentSettingsStore(files)
            assertEquals(old, store.settings); assertTrue(store.loadWarning.isEmpty())
            store.save(old.copy(model = ""), "")
            assertTrue(JsonParser.parseString(files.read("settings.json")).asJsonObject.has("api"))
            assertEquals("", AgentSettingsStore(files).settings.model)
        }
    }
    @Test fun deletingActiveAndInactiveHistoryCannotRestoreDeletedConversations() {
        val files = files(); val sessions = AgentSessionStore(files)
        val first = AgentSession(title = "First"); val second = AgentSession(title = "Second")
        sessions.save(first); sessions.save(second)
        AgentEngine(AgentModel { _, _, _, _ -> AgentReply(AgentMessage("assistant", "done"), 1) },
            { AgentSettings() }, AgentTools(), sessions).use { engine ->
            engine.deleteSession(first.id)
            assertEquals(second.id, engine.session.id)
            assertFails { sessions.load(first.id) }
            engine.deleteSession(second.id)
            assertNotEquals(second.id, engine.session.id)
            assertEquals(engine.session.id, sessions.latest().id)
            assertTrue(sessions.list().none { it.id == first.id || it.id == second.id })
            assertFailsWith<IllegalArgumentException> { engine.deleteSession("../outside") }
        }
    }
    @Test fun detailedReferenceDefaultsUpgradeOnceAndCustomBudgetsRemainUnchanged() {
        val files = files()
        val original = AgentSettings(model = "chosen", tokenBudget = 60000)
        val custom = AgentSettings(model = "plan", authMode = AgentAuthMode.OPENAI_OAUTH, tokenBudget = 45000)
        files.write("settings.json", jsonObject("active" to AgentAuthMode.API_KEY, "api" to original, "oauth" to custom).toString())
        val restored = AgentSettingsStore(files)
        assertEquals(original.copy(tokenBudget = 180000), restored.settings)
        assertEquals(custom, restored.profile(AgentAuthMode.OPENAI_OAUTH))
        assertEquals(2, JsonParser.parseString(files.read("settings.json")).asJsonObject.int("contextVersion"))
        // After the schema upgrade, choosing the old numeric value explicitly must survive future restarts.
        restored.save(original, "")
        assertEquals(60000, AgentSettingsStore(files).settings.tokenBudget)
    }
    @Test fun aRunningTurnCannotHaveItsHistoryDeleted() {
        val latch = CountDownLatch(1); val release = CountDownLatch(1)
        AgentEngine(AgentModel { _, _, _, _ -> latch.countDown(); release.await(3, TimeUnit.SECONDS); AgentReply(AgentMessage("assistant", "done"), 0) },
            { AgentSettings() }, AgentTools(), AgentSessionStore(files())).use { engine ->
            val turn = engine.submit("work")
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertFailsWith<IllegalStateException> { engine.deleteSession(engine.session.id) }
            release.countDown(); assertEquals(AgentState.COMPLETED, turn.get(2, TimeUnit.SECONDS))
        }
    }
    @Test fun modelCatalogAutoLoadsAndRestoresCacheWithoutCredentialsInTheCache() {
        val files = files(); val store = AgentSettingsStore(files)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val received = CountDownLatch(1); val failures = CopyOnWriteArrayList<Throwable>()
        val restarted = CountDownLatch(1); val requests = java.util.concurrent.atomic.AtomicInteger()
        server.createContext("/v1/models") { exchange ->
            runCatching {
                assertEquals("Bearer catalog-fixture-key", exchange.requestHeaders.getFirst("Authorization"))
                val body = "{\"data\":[{\"id\":\"text-embedding-fixture\"},{\"id\":\"model-a\"},{\"id\":\"model-b\",\"display_name\":\"模型 B\"}]}".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
                if (requests.incrementAndGet() == 2) restarted.countDown()
                received.countDown()
            }.onFailure { failures += it; exchange.close() }
        }
        server.start()
        try {
            val options = AgentSettings(endpoint = "http://127.0.0.1:" + server.address.port + "/v1", apiKeyEnvironment = "NOCTURNE_TEST_UNSET_ENV")
            store.save(options, "catalog-fixture-key")
            OpenAiOAuth(files).use { oauth ->
                AgentModelCatalog(files, store, oauth).use { catalog ->
                    catalog.request()
                    assertTrue(received.await(3, TimeUnit.SECONDS))
                    val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                    while (catalog.state(AgentAuthMode.API_KEY).models.isEmpty() && System.nanoTime() < until) Thread.sleep(5)
                    assertEquals(listOf("text-embedding-fixture", "model-a", "model-b"), catalog.state(AgentAuthMode.API_KEY).models.map { it.id })
                    assertEquals("model-a", store.settings.model)
                }
                AgentModelCatalog(files, AgentSettingsStore(files), oauth).use { restored ->
                    assertEquals(listOf("text-embedding-fixture", "model-a", "model-b"), restored.state(AgentAuthMode.API_KEY).models.map { it.id })
                    restored.request(force = true)
                    assertTrue(restarted.await(3, TimeUnit.SECONDS), "A restored catalog must refresh even with a recent cache")
                    val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                    while (restored.state(AgentAuthMode.API_KEY).loading && System.nanoTime() < until) Thread.sleep(5)
                    assertEquals("已加载 3 个模型", restored.state(AgentAuthMode.API_KEY).status)
                }
            }
            val cache = Files.list(files.path("model-catalogs/.keep").parent).use { paths -> paths.filter { it.toString().endsWith(".json") }.toList() }
            assertEquals(1, cache.size); assertFalse(Files.readString(cache.single()).contains("catalog-fixture-key"))
            assertTrue(failures.isEmpty(), failures.toString())
        } finally { server.stop(0) }
    }
}

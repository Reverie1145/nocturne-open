package cn.reverie.app.agent

import cn.reverie.app.agent.auth.*
import cn.reverie.app.agent.model.HttpAgentModel
import cn.reverie.app.agent.model.OpenAiResponseStream
import com.google.gson.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/** Local authorization server with signed JWTs, rotating tokens and an actual SSE endpoint. */
class OpenAiOAuthChecks {
    private class Fixture : AutoCloseable {
        val files = AgentFiles(Files.createTempDirectory("nocturne-openai-oauth"))
        val time = AtomicLong(System.currentTimeMillis() / 1000)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val executor = Executors.newFixedThreadPool(6)
        private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val root get() = "http://127.0.0.1:" + server.address.port
        val endpoints get() = OpenAiEndpoints(root, "$root/v1")
        val browser = CopyOnWriteArrayList<URI>()
        val failures = CopyOnWriteArrayList<Throwable>()
        val tokenRequests = AtomicInteger()
        val refreshRequests = AtomicInteger()
        val revokeRequests = AtomicInteger()
        val modelRequests = AtomicInteger()
        val latestRefresh = ConcurrentHashMap<String, String>()
        private val refreshSubjects = ConcurrentHashMap<String, String>()
        @Volatile var subject = "subject-one"
        @Volatile var grantedScopes = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
        @Volatile var claimsChange: (JsonObject) -> Unit = {}
        @Volatile var corruptSignature = false
        @Volatile var refreshFailure = ""
        @Volatile var refreshReceived: CountDownLatch? = null
        @Volatile var releaseRefresh: CountDownLatch? = null
        @Volatile var response: (HttpExchange) -> Unit = { exchange -> send(exchange, 200, sse(jsonObject(
            "status" to "completed", "output" to listOf(jsonObject("type" to "message", "role" to "assistant",
                "content" to listOf(jsonObject("type" to "output_text", "text" to "done")))), "usage" to jsonObject("total_tokens" to 20)))) }
        init {
            server.executor = executor
            server.createContext("/") { exchange ->
                runCatching { handle(exchange) }.onFailure { failure ->
                    failures += failure
                    runCatching { send(exchange, 500, """{"error":{"code":"fixture_failure"}}""") }
                }
                exchange.close()
            }
            server.start()
        }
        fun oauth() = OpenAiOAuth(files, endpoints, { browser += it }, { time.get() })
        fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
            val split = it.split('=', limit = 2)
            URLDecoder.decode(split[0], Charsets.UTF_8) to URLDecoder.decode(split.getOrElse(1) { "" }, Charsets.UTF_8)
        }
        fun callback(oauth: OpenAiOAuth, values: Map<String, String> = emptyMap()): Int {
            val auth = query(browser.last())
            val client = auth.getValue("client_id").let { if (it == "dynamic_agent_client") "oaiapp_" + browser.size else it }
            val params = linkedMapOf("state" to auth.getValue("state"), "code" to "synthetic-code", "client_id" to client)
            params.putAll(values)
            val request = HttpRequest.newBuilder(URI(auth.getValue("redirect_uri") + "?" + OpenAiOAuthHttp.encodeForm(params))).GET().build()
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
        }
        fun login(oauth: OpenAiOAuth, accountId: String? = null, success: Boolean = true) {
            oauth.beginLogin(accountId = accountId)
            assertEquals(200, callback(oauth))
            waitFor { !oauth.isLoggingIn }
            if (success) {
                assertTrue(oauth.activeAccount()?.connected == true, oauth.status)
                if (oauth.activeAccount()?.planEnabled == true) waitFor { oauth.models.isNotEmpty() }
            }
            assertTrue(failures.isEmpty(), failures.toString())
        }
        private fun handle(exchange: HttpExchange) {
            when (exchange.requestURI.path) {
                "/.well-known/openid-configuration" -> send(exchange, 200, jsonObject("issuer" to root,
                    "authorization_endpoint" to endpoints.authorize, "token_endpoint" to endpoints.token,
                    "jwks_uri" to "$root/jwks", "revocation_endpoint" to "$root/revoke").toString())
                "/jwks" -> {
                    val key = pair.public as RSAPublicKey
                    send(exchange, 200, jsonObject("keys" to listOf(jsonObject("kid" to "fixture-key", "kty" to "RSA",
                        "use" to "sig", "alg" to "RS256", "n" to unsigned(key.modulus), "e" to unsigned(key.publicExponent)))).toString())
                }
                "/api/accounts/oauth/token" -> {
                    val form = query(URI("http://fixture/?" + exchange.requestBody.bufferedReader().readText()))
                    assertEquals("https://api.openai.com/v1", form["resource"])
                    assertFalse(form.containsKey("client_secret"))
                    val client = form.getValue("client_id")
                    assertNotEquals("dynamic_agent_client", client)
                    val isRefresh = form["grant_type"] == "refresh_token"
                    val nonce: String?
                    val tokenSubject: String
                    if (isRefresh) {
                        refreshRequests.incrementAndGet()
                        assertFalse(form.containsKey("scope"))
                        refreshReceived?.countDown(); releaseRefresh?.await(5, TimeUnit.SECONDS)
                        if (refreshFailure.isNotEmpty()) { send(exchange, if (refreshFailure == "invalid_grant") 400 else 503,
                            jsonObject("error" to refreshFailure).toString()); return }
                        assertEquals(latestRefresh[client], form["refresh_token"])
                        tokenSubject = refreshSubjects.getValue(form.getValue("refresh_token")); nonce = null
                    } else {
                        tokenRequests.incrementAndGet()
                        assertEquals("authorization_code", form["grant_type"])
                        val auth = query(browser.last())
                        assertEquals(auth["redirect_uri"], form["redirect_uri"])
                        assertEquals(auth["code_challenge"], Base64.getUrlEncoder().withoutPadding().encodeToString(
                            MessageDigest.getInstance("SHA-256").digest(form.getValue("code_verifier").toByteArray(Charsets.US_ASCII))))
                        tokenSubject = subject; nonce = auth.getValue("nonce")
                    }
                    val refresh = "refresh_" + UUID.randomUUID()
                    latestRefresh[client] = refresh; refreshSubjects[refresh] = tokenSubject
                    val claims = jsonObject("iss" to root, "aud" to client, "sub" to tokenSubject, "email" to "same@example.test",
                        "name" to "Test account", "exp" to time.get() + 3600, "iat" to time.get())
                    nonce?.let { claims.addProperty("nonce", it) }; claimsChange(claims)
                    send(exchange, 200, jsonObject("access_token" to "access_" + UUID.randomUUID(), "refresh_token" to refresh,
                        "id_token" to jwt(claims), "token_type" to "Bearer", "expires_in" to 3600,
                        "scope" to grantedScopes, "earliest_refresh_at" to time.get()).toString())
                }
                "/v1/models" -> {
                    modelRequests.incrementAndGet()
                    assertTrue(exchange.requestHeaders.getFirst("Authorization").startsWith("Bearer access_"))
                    send(exchange, 200, jsonObject("models" to listOf(
                        jsonObject("slug" to "hidden", "display_name" to "Hidden", "visibility" to "hide"),
                        jsonObject("slug" to "fixture-model", "display_name" to "Fixture model", "visibility" to "list"),
                        jsonObject("slug" to "second-model", "display_name" to "Second", "visibility" to "list"))).toString())
                }
                "/revoke" -> {
                    val form = query(URI("http://fixture/?" + exchange.requestBody.bufferedReader().readText()))
                    assertEquals("refresh_token", form["token_type_hint"])
                    assertEquals(latestRefresh[form.getValue("client_id")], form["token"])
                    revokeRequests.incrementAndGet(); send(exchange, 200, "")
                }
                "/v1/responses" -> response(exchange)
                else -> send(exchange, 404, "{}")
            }
        }
        private fun jwt(claims: JsonObject): String {
            val encoder = Base64.getUrlEncoder().withoutPadding()
            val header = encoder.encodeToString("""{"alg":"RS256","kid":"fixture-key"}""".toByteArray())
            val payload = encoder.encodeToString(claims.toString().toByteArray(Charsets.UTF_8))
            val signed = "$header.$payload"
            val signer = Signature.getInstance("SHA256withRSA"); signer.initSign(pair.private); signer.update(signed.toByteArray(Charsets.US_ASCII))
            val signature = signer.sign(); if (corruptSignature) signature[0] = (signature[0].toInt() xor 1).toByte()
            return signed + "." + encoder.encodeToString(signature)
        }
        private fun unsigned(value: BigInteger): String {
            val raw = value.toByteArray(); val unsigned = if (raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
            return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned)
        }
        fun send(exchange: HttpExchange, status: Int, body: String, stream: Boolean = false) {
            exchange.responseHeaders.set("Content-Type", if (stream) "text/event-stream" else "application/json")
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        override fun close() { releaseRefresh?.countDown(); server.stop(0); executor.shutdownNow() }
    }
    @Test fun dynamicRegistrationValidatesStatePkceIdentityAndStoresCredentialsEncrypted() = Fixture().use { f ->
        f.oauth().use { oauth ->
            oauth.beginLogin()
            val auth = f.query(f.browser.last())
            assertEquals("dynamic_agent_client", auth["client_id"]); assertEquals("Nocturne", auth["agent_name_hint"])
            assertEquals("S256", auth["code_challenge_method"]); assertEquals("127.0.0.1", URI(auth.getValue("redirect_uri")).host)
            assertTrue(auth.getValue("scope").contains(OPENAI_PLAN_SCOPE))
            assertEquals(400, f.callback(oauth, mapOf("state" to "wrong-state")))
            assertEquals(0, f.tokenRequests.get()); assertTrue(oauth.isLoggingIn)
            assertEquals(200, f.callback(oauth)); waitFor { !oauth.isLoggingIn && oauth.models.isNotEmpty() }
            assertEquals(listOf("fixture-model", "second-model"), oauth.models.map { it.id })
            assertEquals(1, oauth.accounts().size); assertTrue(oauth.activeAccount()!!.planEnabled)
            assertTrue(oauth.needsWelcome()); oauth.acknowledgeWelcome(); assertFalse(oauth.needsWelcome())
            val encrypted = f.files.read("openai-oauth.enc")!!
            assertFalse(encrypted.contains("access_")); assertFalse(encrypted.contains("refresh_")); assertFalse(encrypted.contains("same@example.test"))
            assertTrue(f.failures.isEmpty(), f.failures.toString())
        }
        f.oauth().use { restored -> assertEquals(1, restored.accounts().size); assertTrue(restored.activeAccount()!!.connected); assertFalse(restored.needsWelcome()) }
    }
    @Test fun signatureNonceAudienceIssuerAndExpiryFailuresNeverActivateAnAccount() = Fixture().use { f ->
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.addProperty("nonce", "wrong") }, { it.addProperty("aud", "another-client") },
            { it.addProperty("iss", "https://untrusted.example") }, { it.addProperty("exp", f.time.get() - 120) })
        f.oauth().use { oauth ->
            mutations.forEach { change ->
                f.claimsChange = change; f.login(oauth, success = false)
                assertTrue(oauth.accounts().isEmpty()); assertTrue(oauth.status.contains("验证"))
            }
            f.claimsChange = {}; f.corruptSignature = true; f.login(oauth, success = false)
            assertTrue(oauth.accounts().isEmpty())
        }
    }
    @Test fun returningSignInRetainsClientAndHostBindingsAndRejectsAnotherIdentity() = Fixture().use { f ->
        f.oauth().use { oauth ->
            f.login(oauth)
            val first = oauth.activeAccount()!!
            val initial = f.query(f.browser.last())
            f.subject = "another-subject"
            f.login(oauth, first.id, success = false)
            assertEquals(first, oauth.activeAccount()); assertEquals(1, oauth.accounts().size)
            val returning = f.query(f.browser.last())
            assertEquals("oaiapp_1", returning["client_id"])
            assertEquals(initial["ext_agent_host_id"], returning["ext_agent_host_id"])
            assertFalse(returning.containsKey("agent_name_hint")); assertTrue(returning.containsKey("id_token_hint"))
            f.subject = "subject-one"; f.login(oauth, first.id)
            assertEquals(first.id, oauth.activeAccountId())
        }
    }
    @Test fun missingPlanPermissionAndDeclinedConsentDoNotEnableInference() = Fixture().use { f ->
        f.grantedScopes = "openid profile email"
        f.oauth().use { oauth ->
            f.login(oauth)
            assertFalse(oauth.activeAccount()!!.planEnabled); assertEquals(0, f.modelRequests.get())
            assertFailsWith<IllegalArgumentException> { oauth.withAccess(AgentCancellation()) { } }
            val first = oauth.activeAccount()!!
            oauth.beginLogin(accountId = first.id, requestPlanConsent = true)
            assertEquals("consent", f.query(f.browser.last())["prompt"])
            assertEquals(200, f.callback(oauth, mapOf("error" to "access_denied")))
            waitFor { !oauth.isLoggingIn }; assertEquals(first, oauth.activeAccount())
            assertEquals(1, f.tokenRequests.get())
        }
    }
    @Test fun accountsRemainSeparateAndLogoutRevokesOnlyTheSelectedRenewableSession() = Fixture().use { f ->
        f.oauth().use { oauth ->
            f.login(oauth); val first = oauth.activeAccountId()
            f.subject = "subject-two"; f.login(oauth); val second = oauth.activeAccountId()
            assertNotEquals(first, second); assertEquals(2, oauth.accounts().size)
            assertNotEquals(oauth.accounts()[0].label, oauth.accounts()[1].label)
            oauth.selectAccount(first); oauth.signOut()
            assertEquals(1, f.revokeRequests.get())
            assertFalse(oauth.activeAccount()!!.connected)
            assertTrue(oauth.accounts().first { it.id == second }.connected)
            oauth.beginLogin(accountId = first)
            val auth = f.query(f.browser.last())
            assertEquals("oaiapp_1", auth["client_id"]); assertFalse(auth.containsKey("id_token_hint"))
            oauth.cancelLogin()
        }
    }
    @Test fun concurrentRefreshesAcrossClientsUseOneRotatingRefreshToken() = Fixture().use { f ->
        f.oauth().use { first ->
            f.login(first)
            f.oauth().use { second ->
                val label = first.activeAccount()!!.label
                f.claimsChange = { claims -> claims.remove("email"); claims.remove("name") }
                f.time.addAndGet(3590)
                val pool = Executors.newFixedThreadPool(4)
                try {
                    val futures = (0..3).map { i -> pool.submit<String> {
                        (if (i % 2 == 0) first else second).withAccess(AgentCancellation()) { it.token }
                    } }
                    val results = futures.map { it.get(8, TimeUnit.SECONDS) }
                    assertEquals(1, results.distinct().size); assertEquals(1, f.refreshRequests.get())
                    assertEquals(label, first.activeAccount()!!.label); assertEquals(label, second.activeAccount()!!.label)
                    assertTrue(f.failures.isEmpty(), f.failures.toString())
                } finally { pool.shutdownNow() }
            }
        }
    }
    @Test fun cancellingARequestDoesNotDiscardARefreshAlreadyInFlightOrBlockAccountUi() = Fixture().use { f ->
        f.oauth().use { oauth ->
            f.login(oauth); f.time.addAndGet(3590)
            f.refreshReceived = CountDownLatch(1); f.releaseRefresh = CountDownLatch(1)
            val pool = Executors.newSingleThreadExecutor()
            try {
                val signal = AgentCancellation()
                val future = pool.submit<String> { oauth.withAccess(signal) { it.token } }
                assertTrue(f.refreshReceived!!.await(3, TimeUnit.SECONDS))
                val start = System.nanoTime(); assertTrue(oauth.accounts().isNotEmpty())
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 200)
                signal.cancel()
                assertIs<CancellationException>(assertFailsWith<ExecutionException> { future.get(1, TimeUnit.SECONDS) }.cause)
                f.releaseRefresh!!.countDown()
                val renewed = oauth.withAccess(AgentCancellation()) { it.token }
                assertTrue(renewed.startsWith("access_")); assertEquals(1, f.refreshRequests.get())
                f.oauth().use { restored -> assertEquals(renewed, restored.withAccess(AgentCancellation()) { it.token }) }
            } finally { f.releaseRefresh!!.countDown(); pool.shutdownNow() }
        }
    }
    @Test fun temporaryRefreshFailuresPreserveCredentialsAndTerminalFailuresClearOnlyTokens() = Fixture().use { f ->
        f.oauth().use { oauth ->
            f.login(oauth); val id = oauth.activeAccountId(); f.time.addAndGet(3590)
            f.refreshFailure = "temporary_failure"
            assertFailsWith<OpenAiOAuthError> { oauth.withAccess(AgentCancellation()) { } }
            assertTrue(oauth.activeAccount()!!.connected)
            f.refreshFailure = "invalid_grant"
            assertFailsWith<OpenAiOAuthError> { oauth.withAccess(AgentCancellation()) { } }
            assertEquals(id, oauth.activeAccountId()); assertFalse(oauth.activeAccount()!!.connected)
            assertEquals(1, oauth.accounts().size)
        }
    }
    @Test fun oauthUsesStreamingResponsesWithNamespacedToolsAndNeverUsesTheApiKey() = Fixture().use { f ->
        f.oauth().use { oauth ->
            f.login(oauth)
            val rounds = AtomicInteger(); val executions = AtomicInteger()
            f.response = { exchange ->
                val body = JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject
                assertTrue(body.boolean("stream")); assertFalse(body.boolean("store", true)); assertFalse(body.has("max_output_tokens"))
                assertTrue(exchange.requestHeaders.getFirst("Authorization").startsWith("Bearer access_"))
                val namespace = body.getAsJsonArray("tools").single().asJsonObject
                assertEquals("namespace", namespace.string("type")); assertEquals("nocturne", namespace.string("name"))
                val step = rounds.incrementAndGet()
                val output = if (step == 1) listOf(jsonObject("type" to "function_call", "name" to "increment", "namespace" to "nocturne",
                    "call_id" to "call-one", "arguments" to "{}"))
                else {
                    val input = body.getAsJsonArray("input")
                    assertTrue(input.any { it.asJsonObject.string("type") == "function_call" && it.asJsonObject.string("namespace") == "nocturne" })
                    assertEquals("call-one", input.last().asJsonObject.string("call_id"))
                    assertEquals("1", input.last().asJsonObject.string("output"))
                    listOf(jsonObject("type" to "message", "role" to "assistant", "content" to listOf(jsonObject("type" to "output_text", "text" to "done"))))
                }
                f.send(exchange, 200, ": keepalive\r\n\r\n" + sse(jsonObject("status" to "completed", "output" to output,
                    "usage" to jsonObject("total_tokens" to 30))), stream = true)
            }
            val tools = AgentTools().apply { register(AgentTool("increment", "count", AgentTools.schema()) { _, _ -> JsonPrimitive(executions.incrementAndGet()) }) }
            val options = AgentSettings(model = "fixture-model", authMode = AgentAuthMode.OPENAI_OAUTH, permissionMode = AgentPermissionMode.AUTO)
            val model = HttpAgentModel({ error("API Key must not be read for OAuth") }, oauth)
            AgentEngine(model, { options }, tools, AgentSessionStore(f.files)).use { engine ->
                assertEquals(AgentState.COMPLETED, engine.submit("count").get(6, TimeUnit.SECONDS), engine.session.entriesSnapshot().toString())
                assertEquals(1, executions.get()); assertEquals(2, rounds.get())
            }
            assertTrue(f.failures.isEmpty(), f.failures.toString())
        }
    }
    @Test fun interruptedStreamsUsageErrorsAndForeignEndpointsNeverExecuteToolsOrChangeBilling(): Unit = Fixture().use { f ->
        f.oauth().use { oauth ->
            f.login(oauth)
            val executions = AtomicInteger(); val requests = AtomicInteger()
            val options = AgentSettings(model = "fixture-model", authMode = AgentAuthMode.OPENAI_OAUTH)
            val model = HttpAgentModel({ error("Unexpected API fallback") }, oauth)
            f.response = { exchange -> requests.incrementAndGet(); f.send(exchange, 200,
                "data: " + jsonObject("type" to "response.output_item.done", "item" to jsonObject("type" to "function_call",
                    "name" to "mutate", "call_id" to "partial", "arguments" to "{}")) + "\n\n", stream = true) }
            val tools = AgentTools().apply { register(AgentTool("mutate", "change", AgentTools.schema(), true) { _, _ -> JsonPrimitive(executions.incrementAndGet()) }) }
            AgentEngine(model, { options.copy(permissionMode = AgentPermissionMode.AUTO) }, tools, AgentSessionStore(f.files)).use { engine ->
                assertEquals(AgentState.FAILED, engine.submit("work").get(5, TimeUnit.SECONDS))
                assertEquals(0, executions.get())
            }
            f.response = { exchange -> requests.incrementAndGet(); f.send(exchange, 429,
                """{"error":{"code":"subscription_sharing_usage_limit_exceeded","message":"ignored"}}""") }
            val failure = assertFailsWith<OpenAiOAuthError> { model.complete(options, listOf(AgentMessage("user", "work")), JsonArray(), AgentCancellation()) }
            assertTrue(failure.message!!.contains("限制")); assertEquals(2, requests.get())
            assertFailsWith<IllegalArgumentException> { model.complete(options.copy(endpoint = "https://untrusted.example/v1"),
                listOf(AgentMessage("user", "work")), JsonArray(), AgentCancellation()) }
            assertEquals(2, requests.get())
            assertFailsWith<OpenAiOAuthError> { OpenAiResponseStream.terminal("data: " + jsonObject("type" to "response.failed",
                "response" to jsonObject("error" to jsonObject("code" to "subscription_sharing_usage_limit_exceeded"))) + "\n\n") }
        }
    }
    @Test fun unauthorizedSamplingRefreshesOnceAndCancellingAnOAuthStreamStopsPromptly() = Fixture().use { f ->
        f.oauth().use { oauth ->
            f.login(oauth)
            val options = AgentSettings(model = "fixture-model", authMode = AgentAuthMode.OPENAI_OAUTH)
            val model = HttpAgentModel({ error("Unexpected API fallback") }, oauth)
            val sampled = CopyOnWriteArrayList<String>()
            f.response = { exchange ->
                sampled += exchange.requestHeaders.getFirst("Authorization")
                if (sampled.size == 1) f.send(exchange, 401, """{"error":{"code":"subscription_sharing_invalid_user"}}""")
                else f.send(exchange, 200, sse(jsonObject("status" to "completed", "output" to listOf(
                    jsonObject("type" to "message", "role" to "assistant", "content" to listOf(jsonObject("type" to "output_text", "text" to "renewed")))))), stream = true)
            }
            assertEquals("renewed", model.complete(options, listOf(AgentMessage("user", "hello")), JsonArray(), AgentCancellation()).message.text)
            assertEquals(1, f.refreshRequests.get()); assertEquals(2, sampled.size); assertNotEquals(sampled[0], sampled[1])
            val streaming = CountDownLatch(1); val release = CountDownLatch(1)
            f.response = { exchange ->
                exchange.responseHeaders.set("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n".toByteArray())
                exchange.responseBody.flush(); streaming.countDown()
                release.await(3, TimeUnit.SECONDS)
                runCatching { exchange.responseBody.close() }
            }
            val pool = Executors.newSingleThreadExecutor()
            try {
                val signal = AgentCancellation()
                val future = pool.submit<AgentReply> { model.complete(options, listOf(AgentMessage("user", "wait")), JsonArray(), signal) }
                assertTrue(streaming.await(3, TimeUnit.SECONDS)); signal.cancel()
                assertIs<CancellationException>(assertFailsWith<ExecutionException> { future.get(1, TimeUnit.SECONDS) }.cause)
            } finally { release.countDown(); pool.shutdownNow() }
            assertTrue(f.failures.isEmpty(), f.failures.toString())
        }
    }
    @Test fun persistedLoginCanRefreshCatalogAndCompleteDeltaOnlyInferenceAfterRestart() = Fixture().use { f ->
        f.oauth().use { oauth -> f.login(oauth) }
        val settings = AgentSettingsStore(f.files).apply { save(AgentSettings(authMode = AgentAuthMode.OPENAI_OAUTH), "") }
        f.oauth().use { resumed ->
            assertTrue(resumed.activeAccount()?.connected == true)
            assertTrue(resumed.status.contains("已恢复"))
            cn.reverie.app.agent.model.AgentModelCatalog(f.files, settings, resumed).use { catalog ->
                resumed.onModelsChanged = catalog::acceptOAuth
                val choices = catalog.refresh(AgentAuthMode.OPENAI_OAUTH)
                assertEquals(listOf("fixture-model", "second-model"), choices.map { it.id })
                assertEquals("fixture-model", settings.settings.model)
                f.response = { exchange ->
                    val body = "event: response.output_text.delta\ndata: " + jsonObject("type" to "response.output_text.delta",
                        "item_id" to "message", "output_index" to 0, "content_index" to 0, "delta" to "订阅恢复成功🙂") + "\n\n" +
                        sse(jsonObject("status" to "completed", "output" to emptyList<Any>(), "usage" to jsonObject("total_tokens" to 18)))
                    f.send(exchange, 200, body, stream = true)
                }
                AgentEngine(HttpAgentModel({ error("Unexpected API fallback") }, resumed), { settings.settings }, AgentTools(), AgentSessionStore(f.files)).use { engine ->
                    assertEquals(AgentState.COMPLETED, engine.submit("check").get(4, TimeUnit.SECONDS), engine.session.entriesSnapshot().toString())
                    assertEquals("订阅恢复成功🙂", engine.session.messages.last().text)
                    assertEquals(18, engine.turnTokens)
                }
            }
            assertTrue(f.failures.isEmpty(), f.failures.toString())
        }
    }
    companion object {
        private fun sse(response: JsonObject) = "event: response.completed\ndata: " +
            jsonObject("type" to "response.completed", "response" to response).toString() + "\n\n"
        private fun waitFor(condition: () -> Boolean) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            while (!condition() && System.nanoTime() < until) Thread.sleep(10)
            assertTrue(condition(), "OAuth operation did not settle")
        }
    }
}

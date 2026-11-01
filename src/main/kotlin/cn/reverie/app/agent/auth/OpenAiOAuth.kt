package cn.reverie.app.agent.auth

import cn.reverie.app.agent.*
import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

internal class OpenAiAccess(val accountId: String, val token: String, val apiRoot: String)

/**
 * Official Sign in with ChatGPT public-client flow. Each registration owns its tokens;
 * callback identities never replace a selected account until cryptographically verified.
 */
class OpenAiOAuth internal constructor(
    files: AgentFiles,
    private val endpoints: OpenAiEndpoints,
    private val openBrowser: (URI) -> Unit,
    private val now: () -> Long
) : AutoCloseable {
    constructor(files: AgentFiles) : this(files, OpenAiEndpoints(), { Desktop.getDesktop().browse(it) },
        { System.currentTimeMillis() / 1000 })
    private val credentials = OpenAiCredentialStore(files)
    private val http = OpenAiOAuthHttp()
    private val identity = OpenAiIdentityVerifier(endpoints, http, now)
    private val workers = Executors.newFixedThreadPool(2) { Thread(it, "Nocturne-OpenAI-OAuth").apply { isDaemon = true } }
    private val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "Nocturne-OpenAI-Login-Timer").apply { isDaemon = true } }
    private val renewals = Executors.newSingleThreadExecutor { Thread(it, "Nocturne-OpenAI-Renewal").apply { isDaemon = true } }
    private val refreshes = ConcurrentHashMap<String, CompletableFuture<OpenAiCredentials>>()
    private val requests = CopyOnWriteArrayList<AgentCancellation>()
    private val closed = AtomicBoolean()
    @Volatile private var pending: Attempt? = null
    @Volatile var status = credentials.loadWarning.ifEmpty {
        credentials.accounts().firstOrNull { it.id == credentials.activeId() && it.connected }?.let { "已恢复 ChatGPT 登录 · " + it.label }
            ?: "尚未连接 ChatGPT 账户"
    }
        private set
    @Volatile var models: List<OpenAiModelInfo> = emptyList()
        private set
    var onModelsChanged: (List<OpenAiModelInfo>) -> Unit = {}
    val isLoggingIn: Boolean get() = pending != null
    fun accounts() = credentials.accounts()
    fun activeAccountId() = credentials.activeId()
    fun activeAccount() = accounts().firstOrNull { it.id == activeAccountId() }
    fun needsWelcome() = activeAccount()?.let { it.connected && it.planEnabled && !credentials.welcomeSeen() } == true
    fun acknowledgeWelcome() = credentials.transaction(AgentCancellation()) { it.planWelcomeSeen = true }

    private class Attempt(val server: HttpServer, val selected: OpenAiCredentials?, val clientId: String,
                          val state: String, val nonce: String, val verifier: String, val redirect: String,
                          val authorizationUri: URI) {
        val cancellation = AgentCancellation()
        val claimed = AtomicBoolean()
        val expires = System.nanoTime() + TimeUnit.MINUTES.toNanos(10)
        var expiryTask: ScheduledFuture<*>? = null
    }
    @Synchronized fun beginLogin(clientId: String = "", accountId: String? = null, requestPlanConsent: Boolean = false) {
        check(!closed.get()) { "OpenAI 登录系统已关闭" }
        check(pending == null) { "已有登录正在等待浏览器授权" }
        val selected = accountId?.let { credentials.find(it) ?: error("账户记录不存在") }
        require(selected != null || accounts().size < 10) { "最多保存 10 个 ChatGPT 账户" }
        val client = selected?.clientId ?: clientId.trim().ifEmpty { "dynamic_agent_client" }
        require(client.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "OAuth Client ID 格式无效" }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val redirect = "http://127.0.0.1:" + server.address.port + "/auth/callback"
        val state = random(32); val nonce = random(32); val verifier = random(64)
        val values = linkedMapOf("client_id" to client, "ext_agent_host_id" to credentials.hostId(),
            "response_type" to "code", "redirect_uri" to redirect,
            "scope" to "openid profile email offline_access resource.invoke $OPENAI_PLAN_SCOPE",
            "resource" to OPENAI_API_ROOT, "state" to state, "nonce" to nonce,
            "code_challenge_method" to "S256", "code_challenge" to Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))))
        if (client == "dynamic_agent_client") values["agent_name_hint"] = "Nocturne"
        if (requestPlanConsent) values["prompt"] = "consent"
        selected?.let {
            if (it.idToken.isNotEmpty()) values["id_token_hint"] = it.idToken
            if (it.email.isNotEmpty()) values["login_hint"] = it.email
        }
        val attempt = Attempt(server, selected, client, state, nonce, verifier, redirect,
            URI(endpoints.authorize + "?" + OpenAiOAuthHttp.encodeForm(values)))
        pending = attempt
        server.executor = workers
        server.createContext("/auth/callback") { exchange -> callback(attempt, exchange) }
        server.start()
        attempt.expiryTask = timer.schedule({
            attempt.cancellation.cancel()
            finish(attempt, "登录已超时，请重新打开登录页")
        }, 10, TimeUnit.MINUTES)
        status = "请在浏览器登录，并允许使用 ChatGPT 订阅"
        runCatching { openBrowser(attempt.authorizationUri) }.onFailure { status = "无法打开浏览器，请点击「重开登录页」" }
    }
    fun reopenLogin() { pending?.let { openBrowser(it.authorizationUri) } }
    @Synchronized fun cancelLogin() {
        pending?.let { it.cancellation.cancel(); finish(it, "登录已取消") }
    }
    private fun callback(attempt: Attempt, exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET" || exchange.requestURI.path != "/auth/callback" ||
                !exchange.remoteAddress.address.isLoopbackAddress ||
                exchange.requestHeaders.getFirst("Host") != "127.0.0.1:" + attempt.server.address.port) {
                page(exchange, 400, "登录回调无效"); return
            }
            val params = parseQuery(exchange.requestURI.rawQuery.orEmpty())
            val returnedState = params["state"].orEmpty()
            if (pending !== attempt || System.nanoTime() > attempt.expires || !MessageDigest.isEqual(
                    returnedState.toByteArray(), attempt.state.toByteArray())) {
                page(exchange, 400, "登录状态无法验证，请返回客户端重新登录"); return
            }
            if (!attempt.claimed.compareAndSet(false, true)) { page(exchange, 409, "此登录回调已经处理"); return }
            if (params["error"] != null) {
                page(exchange, 200, "授权未完成，请返回 Nocturne")
                finish(attempt, if (params["error"] == "access_denied") OpenAiOAuthError("access_denied").message!! else "OpenAI 登录未完成")
                return
            }
            if (params["iss"] != null) require(params["iss"] == endpoints.issuer)
            val issued = params["client_id"] ?: attempt.clientId
            require(issued != "dynamic_agent_client" && issued.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "未返回签发的 Client ID" }
            require(attempt.clientId == "dynamic_agent_client" || issued == attempt.clientId) { "回调 Client ID 不匹配" }
            val code = params["code"].orEmpty()
            require(code.length in 1..12000) { "登录回调缺少授权码" }
            page(exchange, 200, "已收到登录回调。请返回 Nocturne 查看账户验证结果。")
            status = "正在验证 OpenAI 账户…"
            workers.execute { completeLogin(attempt, issued, code) }
        } catch (_: Throwable) {
            runCatching { page(exchange, 400, "登录回调无法验证") }
            if (attempt.claimed.get()) finish(attempt, "登录回调无法验证，请重新登录")
        } finally { exchange.close() }
    }
    private fun completeLogin(attempt: Attempt, issued: String, code: String) {
        val signal = attempt.cancellation
        try {
            val response = http.json(endpoints.token, signal, linkedMapOf("grant_type" to "authorization_code",
                "client_id" to issued, "code" to code, "code_verifier" to attempt.verifier,
                "redirect_uri" to attempt.redirect, "resource" to OPENAI_API_ROOT))
            val token = response.string("id_token")
            val verified = identity.verify(token, issued, attempt.nonce, signal)
            attempt.selected?.let { require(it.clientId == issued && it.subject == verified.subject) { "登录返回了另一个账户" } }
            val record = tokenRecord(attempt.selected?.id ?: UUID.randomUUID().toString(), issued, verified, response, null)
            synchronized(this) {
                signal.check()
                check(pending === attempt)
                requests.forEach { it.cancel() }
                credentials.transaction(signal) { data ->
                    signal.check()
                    val previous = data.accounts.firstOrNull { it.clientId == issued && it.subject == verified.subject }
                    val saved = if (previous != null && previous.id != record.id) tokenRecord(previous.id, issued, verified, response, null) else record
                    data.accounts.removeIf { it.id == saved.id }
                    require(data.accounts.size < 10)
                    data.accounts += saved; data.activeId = saved.id
                }
                models = emptyList()
                finish(attempt, if (OPENAI_PLAN_SCOPE !in record.scopes) "账户已登录，但尚未授权使用 ChatGPT 订阅"
                    else "已连接 " + activeAccount()!!.label)
            }
            if (OPENAI_PLAN_SCOPE in record.scopes) runCatching { refreshModels(signal) }.onFailure {
                if (!isLoggingIn && activeAccount()?.connected == true && activeAccount()?.id == record.id)
                    status = "已连接；模型列表未能加载，请点击刷新"
            }
        } catch (failure: Throwable) {
            finish(attempt, when {
                signal.isCancelled -> "登录已取消"
                failure is OpenAiOAuthError -> failure.message!!
                else -> "OpenAI 登录验证未通过，请重新登录"
            })
        }
    }
    @Synchronized private fun finish(attempt: Attempt, message: String) {
        if (pending !== attempt) return
        attempt.expiryTask?.cancel(false)
        attempt.server.stop(0)
        pending = null; status = message
    }
    fun selectAccount(id: String) {
        check(!isLoggingIn) { "先完成或取消浏览器登录" }
        requests.forEach { it.cancel() }
        credentials.transaction(AgentCancellation()) { data ->
            require(data.accounts.any { it.id == id }) { "账户记录不存在" }
            data.activeId = id
        }
        models = emptyList(); status = "已选择 " + activeAccount()!!.label
    }
    internal fun <T> withAccess(cancellation: AgentCancellation, action: (OpenAiAccess) -> T): T {
        check(!closed.get()) { "OpenAI 登录系统已关闭" }
        val accountId = activeAccountId()
        requests += cancellation
        try {
            cancellation.check()
            val record = freshCredentials(accountId, cancellation)
            require(activeAccountId() == accountId) { "ChatGPT 账户已切换，请重试任务" }
            return action(OpenAiAccess(record.id, record.accessToken, endpoints.apiRoot))
        } finally { requests.remove(cancellation) }
    }
    private fun freshCredentials(id: String, cancellation: AgentCancellation, forceToken: String? = null): OpenAiCredentials {
        val saved = credentials.find(id) ?: error("请先在模型设置中连接 ChatGPT 账户")
        require(saved.accessToken.isNotEmpty()) { "ChatGPT 登录已失效，请重新登录" }
        require(OPENAI_PLAN_SCOPE in saved.scopes) { "请在浏览器授予使用 ChatGPT 订阅的权限" }
        if (saved.expiresAt > now() + 60 && forceToken == null) return saved
        // A cancelled inference stops waiting immediately. A rotating refresh already in flight
        // still persists its replacement credentials, instead of discarding a consumed token.
        val future = refreshes.compute(id) { _, previous ->
            previous?.takeUnless { it.isDone } ?: CompletableFuture<OpenAiCredentials>().also { result ->
                renewals.execute {
                    try { result.complete(refreshCredentials(id, AgentCancellation(), forceToken)) }
                    catch (failure: Throwable) { result.completeExceptionally(failure) }
                    finally { refreshes.remove(id, result) }
                }
            }
        }!!
        while (true) {
            cancellation.check()
            try { return future.get(100, TimeUnit.MILLISECONDS).also { cancellation.check() } }
            catch (_: TimeoutException) { }
            catch (failure: ExecutionException) {
                val cause = failure.cause
                if (cause is OpenAiOAuthError || cause is CancellationException || cause is InterruptedException) throw cause
                error("OpenAI 续期响应未能通过验证，请重新登录")
            }
        }
    }
    private fun refreshCredentials(id: String, cancellation: AgentCancellation, forceToken: String?): OpenAiCredentials {
        var terminal: OpenAiOAuthError? = null
        val record = credentials.transaction(cancellation) { data ->
            val current = data.accounts.firstOrNull { it.id == id } ?: error("ChatGPT 账户不存在")
            require(current.accessToken.isNotEmpty()) { "ChatGPT 登录已失效，请重新登录" }
            if ((forceToken == null && current.expiresAt > now() + 60) || (forceToken != null && current.accessToken != forceToken)) return@transaction current
            require(current.refreshToken.isNotEmpty()) { "无法续期，请重新登录 ChatGPT" }
            if (now() < current.earliestRefreshAt) {
                require(current.expiresAt > now() + 5) { "OpenAI 尚未允许续期，请稍后重试" }
                return@transaction current
            }
            try {
                val response = http.json(endpoints.token, cancellation, linkedMapOf("grant_type" to "refresh_token",
                    "client_id" to current.clientId, "refresh_token" to current.refreshToken, "resource" to OPENAI_API_ROOT))
                val verified = if (response.string("id_token").isNotEmpty()) identity.verify(response.string("id_token"), current.clientId, null, cancellation)
                    else OpenAiIdentity(current.subject, current.email, current.name)
                require(verified.subject == current.subject) { "刷新返回了另一个账户" }
                val updated = tokenRecord(current.id, current.clientId, verified, response, current)
                require(updated.refreshToken.isNotEmpty()) { "OpenAI 未返回续期凭据" }
                data.accounts[data.accounts.indexOf(current)] = updated
                updated
            } catch (failure: OpenAiOAuthError) {
                if (failure.code !in TERMINAL_REFRESH_ERRORS) throw failure
                terminal = failure
                val cleared = current.cleared()
                data.accounts[data.accounts.indexOf(current)] = cleared
                cleared
            }
        }
        terminal?.let { status = it.message!!; throw it }
        require(OPENAI_PLAN_SCOPE in record.scopes) { "ChatGPT 订阅使用权限已被撤销" }
        return record
    }
    internal fun refreshAfterUnauthorized(access: OpenAiAccess, cancellation: AgentCancellation): String =
        freshCredentials(access.accountId, cancellation, access.token).accessToken
    fun refreshModels(cancellation: AgentCancellation = AgentCancellation()) = withAccess(cancellation) { access ->
        val response = http.json(access.apiRoot + "/models", cancellation, bearer = access.token)
        val catalog = response.getAsJsonArray("models") ?: error("OpenAI 未返回账户的模型目录")
        val available = catalog.mapNotNull { raw ->
            val entry = raw.asJsonObject
            if (entry.string("visibility") != "list" || entry.string("slug").isBlank()) null
            else OpenAiModelInfo(entry.string("slug").take(200), entry.string("display_name", entry.string("slug")).take(100))
        }.distinctBy { it.id }.take(100)
        require(available.isNotEmpty()) { "此账户尚无可用于订阅请求的模型" }
        cancellation.check()
        require(activeAccountId() == access.accountId) { "ChatGPT 账户已切换" }
        models = available
        onModelsChanged(available)
        available
    }
    fun signOut() {
        cancelLogin(); requests.forEach { it.cancel() }; models = emptyList()
        var confirmed = true
        credentials.transaction(AgentCancellation()) { data ->
            val current = data.accounts.firstOrNull { it.id == data.activeId } ?: return@transaction
            if (current.refreshToken.isNotEmpty()) {
                confirmed = runCatching {
                    val signal = AgentCancellation()
                    val metadata = identity.metadata(signal)
                    val endpoint = identity.trustedEndpoint(metadata.string("revocation_endpoint"))
                    http.request(endpoint, signal, linkedMapOf("token" to current.refreshToken,
                        "token_type_hint" to "refresh_token", "client_id" to current.clientId))
                }.isSuccess
            }
            data.accounts[data.accounts.indexOf(current)] = current.cleared()
        }
        status = if (confirmed) "已退出 ChatGPT，账户注册保留供下次登录"
            else "已在本机退出；远程撤销未确认，可在 ChatGPT 设置中断开此应用"
    }
    private fun tokenRecord(id: String, clientId: String, verified: OpenAiIdentity, response: JsonObject,
                            prior: OpenAiCredentials?): OpenAiCredentials {
        val access = response.string("access_token")
        require(access.isNotBlank() && access.length <= 32768 && !access.any { it == '\r' || it == '\n' })
        require(response.string("token_type").equals("Bearer", true)) { "OpenAI 未返回 Bearer 授权" }
        val expires = response.get("expires_in")?.asLong ?: error("OpenAI 未返回授权有效期")
        require(expires in 1..86400)
        val scopes = if (response.has("scope")) response.string("scope").split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
            else prior?.scopes ?: emptySet()
        val refresh = response.string("refresh_token")
        require(refresh.length <= 32768)
        return OpenAiCredentials(id, clientId, verified.subject, verified.email.ifEmpty { prior?.email ?: "" },
            verified.name.ifEmpty { prior?.name ?: "" }, access,
            refresh.ifEmpty { prior?.refreshToken ?: "" }, response.string("id_token").ifEmpty { prior?.idToken ?: "" },
            scopes, now() + expires, response.get("earliest_refresh_at")?.takeUnless { it.isJsonNull }?.let {
                if (it.asJsonPrimitive.isNumber) it.asLong else it.asString.toLongOrNull()
                    ?: java.time.Instant.parse(it.asString).epochSecond
            } ?: 0)
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        cancelLogin(); requests.forEach { it.cancel() }; workers.shutdownNow(); timer.shutdownNow(); renewals.shutdown()
    }
    companion object {
        private val random = SecureRandom()
        private fun random(bytes: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also { random.nextBytes(it) })
        private val TERMINAL_REFRESH_ERRORS = setOf("invalid_grant", "invalid_refresh_token", "token_expired",
            "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused")
        private fun parseQuery(query: String): Map<String, String> {
            require(query.length <= 16000)
            val values = linkedMapOf<String, String>()
            query.split('&').filter { it.isNotEmpty() }.forEach { field ->
                val pair = field.split('=', limit = 2)
                val key = URLDecoder.decode(pair[0], Charsets.UTF_8)
                require(key !in values) { "重复回调参数" }
                values[key] = URLDecoder.decode(pair.getOrElse(1) { "" }, Charsets.UTF_8)
            }
            return values
        }
        private fun page(exchange: HttpExchange, status: Int, message: String) {
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.responseHeaders.set("Cache-Control", "no-store")
            exchange.responseHeaders.set("Referrer-Policy", "no-referrer")
            exchange.responseHeaders.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
            val bytes = ("<!doctype html><html lang=\"zh\"><meta charset=\"utf-8\"><title>Nocturne · ChatGPT</title>" +
                "<h1>Nocturne</h1><p>" + message + "</p></html>").toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}

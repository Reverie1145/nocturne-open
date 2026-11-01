package cn.reverie.app.agent.model

import cn.reverie.app.agent.*
import cn.reverie.app.agent.auth.*
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.net.URI
import java.net.http.*
import java.time.Duration
import java.util.concurrent.*

data class AgentModelCatalogState(val models: List<OpenAiModelInfo> = emptyList(), val loading: Boolean = false,
                                  val status: String = "保存连接后自动加载模型", val fetchedAt: Long = 0)

/** Catalogs belong to a connection/account, not to the currently displayed form. */
class AgentModelCatalog(private val files: AgentFiles, private val settings: AgentSettingsStore,
                        private val oauth: OpenAiOAuth) : AutoCloseable {
    private data class Cache(@field:SerializedName("models") val models: List<OpenAiModelInfo>,
                             @field:SerializedName("fetchedAt") val fetchedAt: Long)
    private val states = ConcurrentHashMap<String, AgentModelCatalogState>()
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "Nocturne-Model-Catalog").apply { isDaemon = true } }
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
    private val signals = java.util.concurrent.CopyOnWriteArrayList<AgentCancellation>()
    @Volatile private var closed = false
    private fun identity(mode: AgentAuthMode, options: AgentSettings = settings.profile(mode),
                         secret: String = settings.apiKey(options), accountId: String = oauth.activeAccountId()): String {
        val connection = if (mode == AgentAuthMode.OPENAI_OAUTH) accountId
            else options.endpoint.trim().trimEnd('/') + ":" + options.protocol + ":" + secret
        return AgentTools.sourceHash(mode.name + ":" + connection)
    }
    fun state(mode: AgentAuthMode): AgentModelCatalogState {
        val key = identity(mode)
        return states.computeIfAbsent(key) {
            runCatching { files.read("model-catalogs/" + key + ".json", 512000)?.let { raw ->
                val cache = agentGson.fromJson(raw, Cache::class.java)
                AgentModelCatalogState(cache.models, status = "已恢复模型列表", fetchedAt = cache.fetchedAt)
            } }.getOrNull() ?: AgentModelCatalogState()
        }
    }
    fun request(mode: AgentAuthMode = settings.settings.authMode, force: Boolean = false) {
        if (!closed) worker.execute {
            val current = state(mode)
            if (!current.loading && (force || System.currentTimeMillis() - current.fetchedAt > 60_000)) runCatching { refresh(mode) }
        }
    }
    fun acceptOAuth(choices: List<OpenAiModelInfo>) {
        if (closed || choices.isEmpty()) return
        val options = settings.profile(AgentAuthMode.OPENAI_OAUTH)
        commit(AgentAuthMode.OPENAI_OAUTH, identity(AgentAuthMode.OPENAI_OAUTH), options, choices)
    }
    fun refresh(mode: AgentAuthMode): List<OpenAiModelInfo> {
        check(!closed)
        val options = settings.profile(mode)
        val secret = if (mode == AgentAuthMode.API_KEY) settings.apiKey(options) else ""
        HttpAgentModel.validateSettings(options, allowEmptyModel = true)
        val key = identity(mode, options, secret)
        val previous = state(mode)
        if (mode == AgentAuthMode.OPENAI_OAUTH && oauth.activeAccount()?.let { it.connected && it.planEnabled } != true) {
            states[key] = previous.copy(loading = false, status = "登录 ChatGPT 后自动加载订阅模型")
            return previous.models
        }
        states[key] = previous.copy(loading = true, status = "正在加载模型列表…")
        val cancellation = AgentCancellation(); signals += cancellation
        try {
            val choices = if (mode == AgentAuthMode.OPENAI_OAUTH) oauth.refreshModels(cancellation)
                else fetchApi(options, cancellation, secret)
            require(choices.isNotEmpty()) { "服务未提供可用模型；可手动填写模型名称" }
            if (identity(mode) == key) commit(mode, key, options, choices)
            return choices
        } catch (failure: Throwable) {
            val message = if (secret.isBlank()) failure.agentError() else failure.agentError().replace(secret, "[REDACTED]")
            if (identity(mode) == key) states[key] = previous.copy(loading = false, status = "模型列表：" + message)
            if (failure is java.util.concurrent.CancellationException) throw failure
            throw IllegalStateException(message)
        } finally { signals -= cancellation }
    }
    private fun commit(mode: AgentAuthMode, key: String, options: AgentSettings, choices: List<OpenAiModelInfo>) {
        val now = System.currentTimeMillis()
        files.write("model-catalogs/" + key + ".json", agentGson.toJson(Cache(choices, now)), 512000)
        if (options.model.isBlank() || (mode == AgentAuthMode.OPENAI_OAUTH && choices.none { it.id == options.model }))
            settings.selectModel(mode, preferredModel(choices).id, options)
        states[key] = AgentModelCatalogState(choices, status = "已加载 " + choices.size + " 个模型", fetchedAt = now)
    }
    private fun fetchApi(options: AgentSettings, cancel: AgentCancellation, secret: String): List<OpenAiModelInfo> {
        val root = options.endpoint.trim().trimEnd('/').removeSuffix("/chat/completions").removeSuffix("/responses").removeSuffix("/messages")
        val request = HttpRequest.newBuilder(URI(root + "/models")).timeout(Duration.ofSeconds(20)).GET()
        if (options.protocol == AgentProtocol.ANTHROPIC) {
            request.header("anthropic-version", "2023-06-01")
            if (secret.isNotEmpty()) request.header("x-api-key", secret)
        } else if (secret.isNotEmpty()) request.header("Authorization", "Bearer " + secret)
        val pending = client.sendAsync(request.build(), HttpAgentModel.limitedBodyHandler())
        val hook = cancel.onCancel { pending.cancel(true) }
        val response = try { pending.get(25, TimeUnit.SECONDS) }
            catch (_: TimeoutException) { pending.cancel(true); error("加载模型列表超时") }
            finally { hook.close() }
        cancel.check()
        require(response.statusCode() in 200..299) { "HTTP " + response.statusCode() + "，请检查服务地址和凭据" }
        val json = JsonParser.parseString(response.body()).asJsonObject
        return (json.getAsJsonArray("data") ?: json.getAsJsonArray("models") ?: error("服务未提供 models/data 列表"))
            .take(2000).mapNotNull { raw ->
                if (raw.isJsonPrimitive) raw.asString.takeIf { it.length in 1..200 }?.let { OpenAiModelInfo(it, it) }
                else raw.asJsonObject.let { value ->
                    val id = value.string("id", value.string("slug"))
                    id.takeIf { it.length in 1..200 }?.let { OpenAiModelInfo(it, value.string("display_name", value.string("name", it)).take(200)) }
                }
            }.distinctBy { it.id }
    }
    override fun close() { closed = true; signals.forEach { it.cancel() }; worker.shutdownNow() }
    companion object {
        /** Model directories also contain audio/image/embedding products, unsuitable for a text tool loop. */
        fun preferredModel(choices: List<OpenAiModelInfo>): OpenAiModelInfo {
            require(choices.isNotEmpty())
            return choices.firstOrNull { item ->
                val id = item.id.lowercase()
                listOf("embedding", "embed-", "whisper", "tts", "dall-e", "image", "moderation", "rerank", "transcribe", "audio", "realtime")
                    .none { id.contains(it) }
            } ?: choices.first()
        }
    }
}

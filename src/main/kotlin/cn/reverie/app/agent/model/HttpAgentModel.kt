package cn.reverie.app.agent.model

import cn.reverie.app.agent.*
import cn.reverie.app.agent.auth.*
import com.google.gson.*
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.Flow

/** Provider-specific wire formats share the same tool-result loop and cancellation semantics. */
class HttpAgentModel(private val apiKey: () -> String) : AgentModel {
    private var oauth: OpenAiOAuth? = null
    constructor(apiKey: () -> String, oauth: OpenAiOAuth) : this(apiKey) { this.oauth = oauth }
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER).build()

    override fun complete(settings: AgentSettings, messages: List<AgentMessage>, tools: JsonArray,
                          cancellation: AgentCancellation): AgentReply = stream(settings, messages, tools, cancellation) {}

    override fun stream(settings: AgentSettings, messages: List<AgentMessage>, tools: JsonArray,
                        cancellation: AgentCancellation, update: (AgentLiveResponse) -> Unit): AgentReply {
        validateSettings(settings)
        require(settings.model.isNotBlank()) { "请先选择模型" }
        if (settings.authMode == AgentAuthMode.OPENAI_OAUTH) {
            val connection = oauth ?: error("此模型实例尚未配置 OpenAI OAuth")
            return connection.withAccess(cancellation) { access ->
                completeWithKey(settings, messages, tools, cancellation, access.token, access, update)
            }
        }
        return completeWithKey(settings, messages, tools, cancellation, apiKey().trim(), update = update)
    }
    private fun completeWithKey(settings: AgentSettings, messages: List<AgentMessage>, tools: JsonArray,
                                cancellation: AgentCancellation, initialKey: String, access: OpenAiAccess? = null,
                                update: (AgentLiveResponse) -> Unit): AgentReply {
        var key = initialKey
        val body = requestBody(settings, messages, tools, stream = true)
        val suffix = when (settings.protocol) {
            AgentProtocol.CHAT_COMPLETIONS -> "chat/completions"
            AgentProtocol.RESPONSES -> "responses"
            AgentProtocol.ANTHROPIC -> "messages"
        }
        val endpoint = (access?.apiRoot ?: settings.endpoint).trim().trimEnd('/')
        val url = if (endpoint.endsWith("/$suffix")) endpoint else "$endpoint/$suffix"
        val builder = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(settings.timeoutSeconds.toLong()))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString(), Charsets.UTF_8))
        builder.header("Accept", "text/event-stream, application/json")
        if (settings.protocol == AgentProtocol.ANTHROPIC) {
            builder.header("anthropic-version", "2023-06-01")
            if (key.isNotEmpty()) builder.header("x-api-key", key)
        } else if (key.isNotEmpty()) builder.header("Authorization", "Bearer $key")
        var renewed = false
        repeat(3) { attempt ->
            cancellation.check()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(settings.timeoutSeconds.toLong())
            val pending = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
            val hook = cancellation.onCancel { pending.cancel(true) }
            val response = try { pending.get(settings.timeoutSeconds.toLong() + 5, TimeUnit.SECONDS) }
            catch (failure: ExecutionException) { cancellation.check(); throw IllegalStateException("模型请求失败：${redact(failure.cause?.agentError() ?: failure.agentError(), key)}") }
            catch (failure: InterruptedException) { pending.cancel(true); Thread.currentThread().interrupt(); throw failure }
            catch (_: TimeoutException) { pending.cancel(true); error("模型请求超时") }
            finally { hook.close() }
            val input = limitedInput(response.body())
            val bodyHook = cancellation.onCancel { runCatching { input.close() } }
            val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
            val timeout = streamTimeouts.schedule({ timedOut.set(true); runCatching { input.close() } },
                (deadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
            var errorBody = ""
            val json = try { input.use {
                cancellation.check()
                if (response.statusCode() !in 200..299) { errorBody = it.reader(Charsets.UTF_8).readText(); null }
                else {
                    val reader = it.bufferedReader(Charsets.UTF_8)
                    if (response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream", true))
                        AgentProviderStreams.read(settings.protocol, reader, cancellation, update)
                    else {
                        reader.mark(256)
                        val first = CharArray(128); val read = reader.read(first); reader.reset()
                        if (read > 0 && String(first, 0, read).trimStart('\uFEFF', ' ', '\r', '\n').let { it.startsWith("data:") || it.startsWith("event:") || it.startsWith(":") })
                            AgentProviderStreams.read(settings.protocol, reader, cancellation, update)
                        else JsonParser.parseReader(reader).asJsonObject
                    }
                }
            } } catch (failure: Throwable) {
                cancellation.check()
                if (timedOut.get()) error("模型响应流超时；已收到的部分内容不会执行工具")
                if (failure is OpenAiOAuthError) throw failure
                error(redact(failure.agentError(), key))
            } finally { timeout.cancel(false); bodyHook.close() }
            cancellation.check()
            if (json != null) {
                if (access != null && json.string("status") == "failed")
                    throw OpenAiOAuthError(json.getAsJsonObject("error")?.string("code", "generation_failed") ?: "generation_failed")
                if (access != null) json.getAsJsonArray("output")?.forEach {
                    val item = it.asJsonObject
                    require(item.string("type") != "function_call" || item.string("namespace") in setOf("", "nocturne")) {
                        "OpenAI 返回了未知的工具命名空间，工具未执行"
                    }
                }
                return try { parseReply(settings.protocol, json).also { update(AgentLiveResponse(it.message.text, it.message.calls)) } }
                    catch (failure: RuntimeException) { cancellation.check(); error(redact(failure.agentError(), key)) }
            }
            val oauthError = if (access != null) runCatching {
                JsonParser.parseString(errorBody).asJsonObject.getAsJsonObject("error")?.string("code", "request_failed")
            }.getOrNull()?.takeIf { it.matches(Regex("[a-zA-Z0-9_]{1,100}")) } ?: "request_failed" else ""
            if (access != null && response.statusCode() == 401 && !renewed && attempt < 2) {
                key = oauth!!.refreshAfterUnauthorized(access, cancellation)
                builder.setHeader("Authorization", "Bearer $key")
                renewed = true
            } else if (((response.statusCode() == 429 && oauthError != "subscription_sharing_usage_limit_exceeded") ||
                    response.statusCode() in 500..599) && attempt < 2) {
                // Only retry model sampling. Completed tool mutations are never replayed.
                val delay = (response.headers().firstValue("retry-after").orElse("").toLongOrNull()
                    ?: (attempt + 1).toLong()).coerceIn(1, 10) * 1000
                val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay)
                while (System.nanoTime() < until) { cancellation.check(); Thread.sleep(100) }
            } else {
                if (access != null) throw OpenAiOAuthError(oauthError, response.statusCode())
                val detail = runCatching { JsonParser.parseString(errorBody).asJsonObject
                    .getAsJsonObject("error")?.string("message") }.getOrNull() ?: errorBody.take(500)
                error("模型接口 HTTP ${response.statusCode()}：${redact(detail, key)}")
            }
        }
        error("模型服务暂时不可用")
    }
    private fun redact(text: String, key: String) = if (key.isBlank()) text.take(1500) else text.replace(key, "[REDACTED]").take(1500)

    companion object {
        private val streamTimeouts = Executors.newSingleThreadScheduledExecutor { Thread(it, "Nocturne-Stream-Timeout").apply { isDaemon = true } }
        private fun limitedInput(input: InputStream) = object : FilterInputStream(input) {
            var bytes = 0L
            private fun count(amount: Int) { if (amount > 0) bytes += amount; require(bytes <= 4_000_000) { "模型响应超过 4 MB" } }
            override fun read(): Int = super.read().also { if (it >= 0) count(1) }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = `in`.read(buffer, offset, length).also(::count)
        }
        fun validateSettings(settings: AgentSettings, allowEmptyModel: Boolean = false) {
            val uri = URI(settings.endpoint.trim())
            require(uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) { "服务地址必须为 HTTP(S) URL，不能包含凭据、查询参数或片段" }
            require(uri.scheme == "https" || uri.host in setOf("localhost", "127.0.0.1", "[::1]", "::1")) { "远程模型服务请使用 HTTPS" }
            require((allowEmptyModel || settings.authMode == AgentAuthMode.OPENAI_OAUTH || settings.model.isNotBlank()) && settings.model.length <= 200) { "请填写模型名称" }
            if (settings.authMode == AgentAuthMode.OPENAI_OAUTH) {
                require(settings.endpoint.trim().trimEnd('/') in setOf(OPENAI_API_ROOT, "$OPENAI_API_ROOT/responses") &&
                    settings.protocol == AgentProtocol.RESPONSES) { "ChatGPT 订阅必须使用官方 Responses 接口" }
            }
            require(settings.openAiClientId.isEmpty() || settings.openAiClientId.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "OAuth Client ID 格式无效" }
            require(settings.maxRounds in 1..64 && settings.maxOutputTokens in 256..32768)
            require(settings.tokenBudget in 1000..1000000 && settings.contextChars in 12000..1000000)
            require(settings.timeoutSeconds in 10..300)
            require(settings.customInstructions.length <= 16000)
            require(settings.apiKeyEnvironment.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,80}"))) { "API Key 环境变量名称不合法" }
        }
        internal fun requestBody(settings: AgentSettings, messages: List<AgentMessage>, tools: JsonArray, stream: Boolean = false): JsonObject {
            val body = jsonObject("model" to settings.model)
            if (stream) body.addProperty("stream", true)
            when (settings.protocol) {
                AgentProtocol.CHAT_COMPLETIONS -> {
                    body.add("messages", JsonArray().apply { messages.forEach { message ->
                        add(jsonObject("role" to message.role, "content" to message.text).apply {
                            if (message.calls.isNotEmpty()) add("tool_calls", JsonArray().apply { message.calls.forEach { call ->
                                add(jsonObject("id" to call.id, "type" to "function", "function" to jsonObject("name" to call.name, "arguments" to call.arguments.toString())))
                            } })
                            if (message.callId != null) addProperty("tool_call_id", message.callId)
                        })
                    } })
                    body.addProperty("max_tokens", settings.maxOutputTokens)
                    if (!tools.isEmpty) body.add("tools", JsonArray().apply { tools.forEach { add(jsonObject("type" to "function", "function" to it)) } })
                }
                AgentProtocol.RESPONSES -> {
                    body.addProperty("store", false)
                    body.add("include", agentGson.toJsonTree(listOf("reasoning.encrypted_content")))
                    if (settings.authMode == AgentAuthMode.OPENAI_OAUTH) body.addProperty("stream", true)
                    else body.addProperty("max_output_tokens", settings.maxOutputTokens)
                    body.addProperty("instructions", messages.filter { it.role == "system" }.joinToString("\n") { it.text })
                    body.add("input", JsonArray().apply { messages.filter { it.role != "system" }.forEach { message ->
                        when {
                            message.role == "tool" -> add(jsonObject("type" to "function_call_output", "call_id" to message.callId, "output" to message.text))
                            message.providerItems != null -> message.providerItems.forEach { add(it.deepCopy()) }
                            else -> {
                                if (message.text.isNotEmpty()) add(jsonObject("role" to message.role, "content" to message.text))
                                message.calls.forEach { call -> add(jsonObject("type" to "function_call", "call_id" to call.id, "name" to call.name, "arguments" to call.arguments.toString())) }
                            }
                        }
                    } })
                    val responseTools = JsonArray().apply { tools.forEach { spec ->
                        add(spec.asJsonObject.deepCopy().apply { addProperty("type", "function"); addProperty("strict", false) })
                    } }
                    if (!responseTools.isEmpty) body.add("tools", if (settings.authMode == AgentAuthMode.OPENAI_OAUTH)
                        JsonArray().apply { add(jsonObject("type" to "namespace", "name" to "nocturne",
                            "description" to "Nocturne client and JavaScript tools", "tools" to responseTools)) } else responseTools)
                    if (settings.authMode == AgentAuthMode.OPENAI_OAUTH) body.getAsJsonArray("input").forEach {
                        val item = it.asJsonObject
                        if (item.string("type") == "function_call" && item.string("namespace").isBlank()) item.addProperty("namespace", "nocturne")
                    }
                }
                AgentProtocol.ANTHROPIC -> {
                    body.addProperty("max_tokens", settings.maxOutputTokens)
                    body.addProperty("system", messages.filter { it.role == "system" }.joinToString("\n") { it.text })
                    val wire = JsonArray()
                    messages.filter { it.role != "system" }.forEach { message ->
                        val role = if (message.role == "assistant") "assistant" else "user"
                        val content = JsonArray()
                        when {
                            message.role == "tool" -> content.add(jsonObject("type" to "tool_result", "tool_use_id" to message.callId, "content" to message.text, "is_error" to message.error))
                            message.providerItems != null -> message.providerItems.forEach { content.add(it.deepCopy()) }
                            else -> {
                                if (message.text.isNotEmpty()) content.add(jsonObject("type" to "text", "text" to message.text))
                                message.calls.forEach { call -> content.add(jsonObject("type" to "tool_use", "id" to call.id, "name" to call.name, "input" to call.arguments)) }
                            }
                        }
                        if (wire.size() > 0 && wire[wire.size() - 1].asJsonObject.string("role") == role)
                            content.forEach { wire[wire.size() - 1].asJsonObject.getAsJsonArray("content").add(it) }
                        else wire.add(jsonObject("role" to role, "content" to content))
                    }
                    body.add("messages", wire)
                    if (!tools.isEmpty) body.add("tools", JsonArray().apply { tools.forEach { spec ->
                        add(jsonObject("name" to spec.asJsonObject.string("name"), "description" to spec.asJsonObject.string("description"), "input_schema" to spec.asJsonObject.get("parameters")))
                    } })
                }
            }
            return body
        }
        internal fun parseReply(protocol: AgentProtocol, root: JsonObject): AgentReply {
            val calls = mutableListOf<AgentToolCall>()
            val text = StringBuilder()
            var providerItems: JsonArray? = null
            var truncated = false
            when (protocol) {
                AgentProtocol.CHAT_COMPLETIONS -> {
                    val choice = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject ?: error("模型没有返回 choices")
                    truncated = choice.string("finish_reason") == "length"
                    val message = choice.getAsJsonObject("message") ?: error("模型没有返回 message")
                    val content = message.get("content")
                    if (content?.isJsonPrimitive == true) text.append(content.asString)
                    else if (content?.isJsonArray == true) content.asJsonArray.forEach { block -> text.append(block.asJsonObject.string("text")) }
                    if (text.isEmpty()) text.append(message.string("refusal"))
                    message.get("tool_calls")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { raw ->
                        val call = raw.asJsonObject
                        require(call.string("type", "function") == "function") { "不支持的工具类型" }
                        val fn = call.getAsJsonObject("function")
                        calls += AgentToolCall(call.string("id", UUID.randomUUID().toString()), fn.string("name"), parseArguments(fn.string("arguments", "{}")))
                    }
                }
                AgentProtocol.RESPONSES -> {
                    if (root.string("status") == "failed") error(root.get("error")?.toString() ?: "模型生成失败")
                    truncated = root.string("status") == "incomplete"
                    providerItems = root.getAsJsonArray("output") ?: error("模型没有返回 output")
                    providerItems.forEach { raw ->
                        val item = raw.asJsonObject
                        when (item.string("type")) {
                            "function_call" -> calls += AgentToolCall(item.string("call_id"), item.string("name"), parseArguments(item.string("arguments", "{}")))
                            "message" -> item.getAsJsonArray("content")?.forEach { block ->
                                val b = block.asJsonObject
                                if (b.string("type") == "output_text") text.append(b.string("text"))
                                if (b.string("type") == "refusal") text.append(b.string("refusal"))
                            }
                        }
                    }
                }
                AgentProtocol.ANTHROPIC -> {
                    truncated = root.string("stop_reason") == "max_tokens"
                    providerItems = root.getAsJsonArray("content") ?: error("模型没有返回 content")
                    providerItems.forEach { raw ->
                        val block = raw.asJsonObject
                        when (block.string("type")) {
                            "text" -> text.append(block.string("text"))
                            "tool_use" -> calls += AgentToolCall(block.string("id"), block.string("name"), block.getAsJsonObject("input") ?: JsonObject())
                        }
                    }
                }
            }
            require(calls.size <= 32 && calls.map { it.id }.distinct().size == calls.size && calls.all { it.id.isNotBlank() }) { "模型工具调用数量或标识无效" }
            val usage = root.getAsJsonObject("usage") ?: JsonObject()
            val tokens = usage.int("total_tokens", usage.int("input_tokens") + usage.int("output_tokens"))
            return AgentReply(AgentMessage("assistant", text.toString().take(100000), calls, providerItems = providerItems), tokens, truncated)
        }
        private fun parseArguments(raw: String): JsonObject = runCatching {
            require(raw.length <= 300000) { "工具参数过大" }
            JsonParser.parseString(raw).asJsonObject
        }.getOrElse { jsonObject("__invalid_arguments" to it.agentError()) }
        internal fun limitedBodyHandler(): HttpResponse.BodyHandler<String> = HttpResponse.BodyHandler {
            val delegate = HttpResponse.BodySubscribers.ofString(Charsets.UTF_8)
            object : HttpResponse.BodySubscriber<String> {
                private var subscription: Flow.Subscription? = null
                private var bytes = 0L
                override fun getBody(): CompletionStage<String> = delegate.body
                override fun onSubscribe(s: Flow.Subscription) { subscription = s; delegate.onSubscribe(s) }
                override fun onNext(items: List<ByteBuffer>) {
                    bytes += items.sumOf { it.remaining().toLong() }
                    if (bytes > 4_000_000) { subscription?.cancel(); delegate.onError(IllegalStateException("模型响应超过 4 MB")) }
                    else delegate.onNext(items)
                }
                override fun onError(error: Throwable) = delegate.onError(error)
                override fun onComplete() = delegate.onComplete()
            }
        }
    }
}

package cn.reverie.app.agent

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal val agentGson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
internal fun jsonObject(vararg values: Pair<String, Any?>): JsonObject = JsonObject().apply {
    values.forEach { (key, value) -> add(key, agentGson.toJsonTree(value)) }
}
internal fun JsonObject.string(key: String, default: String = ""): String =
    get(key)?.takeUnless { it.isJsonNull }?.asString ?: default
internal fun JsonObject.boolean(key: String, default: Boolean = false): Boolean =
    get(key)?.takeUnless { it.isJsonNull }?.asBoolean ?: default
internal fun JsonObject.int(key: String, default: Int = 0): Int =
    get(key)?.takeUnless { it.isJsonNull }?.asInt ?: default

/** One cancellation signal follows a turn through HTTP, permission requests and main-thread work. */
class AgentCancellation {
    private val cancelled = AtomicBoolean(false)
    private val hooks = CopyOnWriteArrayList<() -> Unit>()
    val isCancelled: Boolean get() = cancelled.get()
    fun check() {
        if (isCancelled || Thread.currentThread().isInterrupted) throw CancellationException("已停止")
    }
    fun onCancel(hook: () -> Unit): AutoCloseable {
        hooks += hook
        if (isCancelled && hooks.remove(hook)) hook()
        return AutoCloseable { hooks.remove(hook) }
    }
    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        hooks.toList().forEach { runCatching(it) }
        hooks.clear()
    }
}

enum class AgentProtocol {
    @SerializedName("CHAT_COMPLETIONS") CHAT_COMPLETIONS,
    @SerializedName("RESPONSES") RESPONSES,
    @SerializedName("ANTHROPIC") ANTHROPIC
}
enum class AgentPermissionMode {
    @SerializedName("ASK") ASK, @SerializedName("READ_ONLY") READ_ONLY, @SerializedName("AUTO") AUTO
}
enum class AgentAuthMode {
    @SerializedName("API_KEY") API_KEY, @SerializedName("OPENAI_OAUTH") OPENAI_OAUTH
}
enum class AgentState { IDLE, THINKING, EXECUTING, WAITING_APPROVAL, COMPLETED, CANCELLED, FAILED, LIMIT_REACHED }

data class AgentSettings(
    @field:SerializedName("endpoint") val endpoint: String = "https://api.openai.com/v1",
    @field:SerializedName("model") val model: String = "",
    @field:SerializedName("protocol") val protocol: AgentProtocol = AgentProtocol.RESPONSES,
    @field:SerializedName("permissionMode") val permissionMode: AgentPermissionMode = AgentPermissionMode.ASK,
    @field:SerializedName("maxRounds") val maxRounds: Int = 16,
    @field:SerializedName("maxOutputTokens") val maxOutputTokens: Int = 4096,
    @field:SerializedName("tokenBudget") val tokenBudget: Int = 180000,
    @field:SerializedName("contextChars") val contextChars: Int = 90000,
    @field:SerializedName("timeoutSeconds") val timeoutSeconds: Int = 90,
    @field:SerializedName("customInstructions") val customInstructions: String = "",
    @field:SerializedName("apiKeyEnvironment") val apiKeyEnvironment: String = "NOCTURNE_AI_API_KEY",
    @field:SerializedName("authMode") val authMode: AgentAuthMode = AgentAuthMode.API_KEY,
    @field:SerializedName("openAiClientId") val openAiClientId: String = ""
)

data class AgentToolCall(@field:SerializedName("id") val id: String,
                         @field:SerializedName("name") val name: String,
                         @field:SerializedName("arguments") val arguments: JsonObject)
data class AgentMessage(
    @field:SerializedName("role") val role: String,
    @field:SerializedName("text") val text: String = "",
    @field:SerializedName("calls") val calls: List<AgentToolCall> = emptyList(),
    @field:SerializedName("callId") val callId: String? = null,
    @field:SerializedName("error") val error: Boolean = false,
    // Responses reasoning items must accompany their function calls in the next request.
    @field:SerializedName("providerItems") val providerItems: JsonArray? = null
)
data class AgentReply(val message: AgentMessage, val tokens: Int, val truncated: Boolean = false)
data class AgentTask(@field:SerializedName("id") val id: Int, @field:SerializedName("title") val title: String,
                     @field:SerializedName("status") val status: String)
data class AgentEntry(@field:SerializedName("time") val time: Long = System.currentTimeMillis(),
                      @field:SerializedName("kind") val kind: String, @field:SerializedName("text") val text: String,
                      @field:SerializedName("callId") val callId: String? = null,
                      @field:SerializedName("tool") val tool: String? = null)

/** Immutable snapshots are safe for the render thread while HTTP is still producing events. */
data class AgentLiveResponse(val text: String = "", val calls: List<AgentToolCall> = emptyList(),
                             val startedAt: Long = System.currentTimeMillis())
data class AgentApproval(val id: String, val tool: String, val arguments: String)

fun interface AgentModel {
    fun complete(settings: AgentSettings, messages: List<AgentMessage>, tools: JsonArray,
                 cancellation: AgentCancellation): AgentReply
    fun stream(settings: AgentSettings, messages: List<AgentMessage>, tools: JsonArray,
               cancellation: AgentCancellation, update: (AgentLiveResponse) -> Unit): AgentReply =
        complete(settings, messages, tools, cancellation).also { update(AgentLiveResponse(it.message.text, it.message.calls)) }
}

internal fun Throwable.agentError(): String =
    (message ?: javaClass.simpleName).take(1500)

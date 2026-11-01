package cn.reverie.app.agent.auth

import cn.reverie.app.agent.*
import cn.reverie.app.agent.model.HttpAgentModel
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal const val OPENAI_API_ROOT = "https://api.openai.com/v1"
internal const val OPENAI_ISSUER = "https://auth.openai.com"
internal const val OPENAI_PLAN_SCOPE = "chatgpt.tokens.use.direct"

/** Endpoints are fixed in production. Local overrides only support transport fixtures. */
internal data class OpenAiEndpoints(
    val issuer: String = OPENAI_ISSUER,
    val apiRoot: String = OPENAI_API_ROOT
) {
    val authorize get() = "$issuer/api/accounts/authorize"
    val token get() = "$issuer/api/accounts/oauth/token"
    val discovery get() = "$issuer/.well-known/openid-configuration"
}

internal class OpenAiOAuthError(val code: String, val status: Int = 0) : IllegalStateException(
    when (code) {
        "access_denied" -> "未授予 ChatGPT 订阅用量权限，请在浏览器中完成授权"
        "invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired",
        "refresh_token_invalidated", "refresh_token_reused", "subscription_sharing_invalid_user" -> "OpenAI 登录已失效，请重新登录"
        "invalid_client" -> "OAuth 客户端未获授权，请检查 Nocturne 的 Client ID 和 OpenAI 准入"
        "subscription_sharing_permission_required", "subscription_sharing_app_not_enabled",
        "subscription_sharing_no_entitlement", "subscription_sharing_ineligible_subscription",
        "subscription_sharing_user_not_eligible" -> "此账户、工作区或客户端尚未获得 ChatGPT 订阅使用权限"
        "chatpass_v2_scope_not_authorized", "chatpass_v2_invalid_authorization_context" -> "此客户端的 OAuth 授权范围不允许使用订阅"
        "subscription_sharing_usage_limit_exceeded" -> "此应用的 ChatGPT 订阅用量已达到限制，请查看 ChatGPT 设置中的用量"
        "subscription_sharing_unsupported_capability" -> "订阅接口不支持此模型或请求能力"
        "subscription_sharing_route_not_supported" -> "此 OAuth 授权不支持当前模型接口"
        "subscription_sharing_usage_unavailable", "subscription_sharing_user_unavailable" -> "OpenAI 暂时无法检查订阅用量，请稍后重试"
        "network_error" -> "无法连接 OpenAI，请检查网络后重试"
        else -> "OpenAI 请求未成功（HTTP $status，" + code.take(100) + "）"
    }
)

/** Do not propagate OAuth response bodies: they can contain credentials. */
internal class OpenAiOAuthHttp {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER).build()
    fun request(uri: String, cancellation: AgentCancellation, form: Map<String, String>? = null,
                bearer: String? = null): HttpResponse<String> {
        cancellation.check()
        val builder = HttpRequest.newBuilder(URI(uri)).timeout(Duration.ofSeconds(35)).header("Accept", "application/json")
        if (form == null) builder.GET() else builder.header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form), Charsets.UTF_8))
        bearer?.let { builder.header("Authorization", "Bearer $it") }
        val future = client.sendAsync(builder.build(), HttpAgentModel.limitedBodyHandler())
        val hook = cancellation.onCancel { future.cancel(true) }
        val response = try { future.get(40, TimeUnit.SECONDS) }
        catch (_: ExecutionException) { cancellation.check(); throw OpenAiOAuthError("network_error") }
        catch (_: TimeoutException) { future.cancel(true); throw OpenAiOAuthError("network_error") }
        catch (failure: InterruptedException) { future.cancel(true); Thread.currentThread().interrupt(); throw failure }
        finally { hook.close() }
        cancellation.check()
        if (response.statusCode() !in 200..299) {
            val code = runCatching {
                val root = JsonParser.parseString(response.body()).asJsonObject
                if (root.get("error")?.isJsonObject == true) root.getAsJsonObject("error").string("code", "request_failed")
                else root.string("error", "request_failed")
            }.getOrDefault("request_failed")
            throw OpenAiOAuthError(code.takeIf { it.matches(Regex("[a-zA-Z0-9_]{1,100}")) } ?: "request_failed", response.statusCode())
        }
        return response
    }
    fun json(uri: String, cancellation: AgentCancellation, form: Map<String, String>? = null,
             bearer: String? = null): JsonObject {
        val response = request(uri, cancellation, form, bearer)
        return try { JsonParser.parseString(response.body()).asJsonObject }
        catch (_: RuntimeException) { error("OpenAI 返回了无效数据") }
    }
    companion object {
        fun encodeForm(values: Map<String, String>): String = values.entries.joinToString("&") {
            URLEncoder.encode(it.key, Charsets.UTF_8) + "=" + URLEncoder.encode(it.value, Charsets.UTF_8)
        }
    }
}

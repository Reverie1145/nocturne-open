package cn.reverie.app.agent.auth

import cn.reverie.app.account.AccountVault
import cn.reverie.app.agent.*
import com.google.gson.annotations.SerializedName
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Tokens are intentionally absent from public account descriptors and their toString(). */
data class OpenAiAccountInfo(val id: String, val label: String, val connected: Boolean, val planEnabled: Boolean)
data class OpenAiModelInfo(@field:SerializedName("id") val id: String, @field:SerializedName("label") val label: String)

internal class OpenAiCredentials(
    @field:SerializedName("id") val id: String,
    @field:SerializedName("client_id") val clientId: String,
    @field:SerializedName("subject") val subject: String,
    @field:SerializedName("email") val email: String,
    @field:SerializedName("name") val name: String,
    @field:SerializedName("access_token") val accessToken: String,
    @field:SerializedName("refresh_token") val refreshToken: String,
    @field:SerializedName("id_token") val idToken: String,
    @field:SerializedName("scopes") val scopes: Set<String>,
    @field:SerializedName("expires_at") val expiresAt: Long,
    @field:SerializedName("earliest_refresh_at") val earliestRefreshAt: Long
) {
    fun info() = OpenAiAccountInfo(id, (email.ifBlank { name }.ifBlank { "ChatGPT" }) + " · " + id.take(8),
        accessToken.isNotEmpty(), OPENAI_PLAN_SCOPE in scopes)
    fun cleared() = OpenAiCredentials(id, clientId, subject, email, name, "", "", "", emptySet(), 0, 0)
}

internal class OpenAiCredentialData(
    @field:SerializedName("host_id") val hostId: String = "urn:uuid:" + UUID.randomUUID(),
    @field:SerializedName("active_id") var activeId: String = "",
    @field:SerializedName("plan_welcome_seen") var planWelcomeSeen: Boolean = false,
    @field:SerializedName("accounts") val accounts: MutableList<OpenAiCredentials> = mutableListOf()
)

/** A file lock also serializes rotating refreshes across two clients using the same game directory. */
internal class OpenAiCredentialStore(private val files: AgentFiles) {
    private var data = OpenAiCredentialData()
    @Volatile private var accountSnapshot = emptyList<OpenAiAccountInfo>()
    @Volatile private var recordSnapshot = emptyMap<String, OpenAiCredentials>()
    @Volatile private var activeSnapshot = ""
    @Volatile private var hostSnapshot = data.hostId
    @Volatile private var welcomeSnapshot = false
    var loadWarning = ""
        private set
    init {
        runCatching {
            transaction(AgentCancellation()) { }
        }.onFailure { loadWarning = "OpenAI 登录信息未能读取，请检查凭据文件或重新登录" }
    }
    fun accounts() = accountSnapshot
    fun activeId() = activeSnapshot
    fun find(id: String): OpenAiCredentials? = recordSnapshot[id]
    fun hostId() = hostSnapshot
    fun welcomeSeen() = welcomeSnapshot
    @Synchronized fun <T> transaction(cancellation: AgentCancellation, action: (OpenAiCredentialData) -> T): T {
        val lockPath = files.path("openai-oauth.lock")
        FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
            var lock: java.nio.channels.FileLock? = null
            while (lock == null) {
                cancellation.check()
                lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                check(System.nanoTime() < until) { "其他客户端正在刷新 OpenAI 登录，请稍后重试" }
                if (lock == null) Thread.sleep(25)
            }
            lock.use {
                val vault = files.path("openai-oauth.enc").toFile()
                if (vault.exists()) {
                    files.read("openai-oauth.enc", 512000)
                    val restored = agentGson.fromJson(AccountVault.read(vault) ?: error("凭据无法解密"), OpenAiCredentialData::class.java)
                    require(restored.hostId.startsWith("urn:uuid:") && restored.hostId.length <= 80 && restored.accounts.size <= 10)
                    require(restored.accounts.map { it.id }.distinct().size == restored.accounts.size)
                    restored.accounts.forEach { record ->
                        AgentFiles.requireId(record.id)
                        require(record.clientId != "dynamic_agent_client" && record.clientId.matches(Regex("[A-Za-z0-9_-]{1,200}")))
                    }
                    data = restored
                }
                cancellation.check()
                val result = action(data)
                val plain = agentGson.toJson(data)
                require(plain.toByteArray(Charsets.UTF_8).size <= 380000) { "OpenAI 账户凭据超过存储上限" }
                AccountVault.write(vault, plain)
                recordSnapshot = data.accounts.associateBy { it.id }
                accountSnapshot = data.accounts.map { it.info() }
                activeSnapshot = data.activeId
                hostSnapshot = data.hostId
                welcomeSnapshot = data.planWelcomeSeen
                loadWarning = ""
                return result
            }
        }
    }
}

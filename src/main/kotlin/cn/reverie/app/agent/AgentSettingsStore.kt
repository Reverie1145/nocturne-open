package cn.reverie.app.agent

import cn.reverie.app.account.AccountVault
import cn.reverie.app.agent.auth.OPENAI_API_ROOT
import cn.reverie.app.agent.model.HttpAgentModel
import com.google.gson.annotations.SerializedName

/** Connection profiles never overwrite one another when the active mode changes. */
class AgentSettingsStore(private val files: AgentFiles) {
    private data class Profiles(
        @field:SerializedName("contextVersion") val contextVersion: Int = 2,
        @field:SerializedName("active") val active: AgentAuthMode = AgentAuthMode.API_KEY,
        @field:SerializedName("api") val api: AgentSettings = AgentSettings(),
        @field:SerializedName("oauth") val oauth: AgentSettings = AgentSettings(authMode = AgentAuthMode.OPENAI_OAUTH)
    )
    @Volatile private var profiles = Profiles()
    val settings: AgentSettings get() = profile(profiles.active)
    @Volatile private var storedKey = ""
    var loadWarning: String = ""
        private set
    var onChanged: (AgentSettings) -> Unit = {}
    init {
        runCatching {
            files.read("settings.json", 128000)?.let { raw ->
                val root = com.google.gson.JsonParser.parseString(raw).asJsonObject
                profiles = if (root.has("active") && root.has("api") && root.has("oauth"))
                    agentGson.fromJson(root, Profiles::class.java)
                else {
                    val legacy = agentGson.fromJson(root, AgentSettings::class.java)
                    if (legacy.authMode == AgentAuthMode.API_KEY) Profiles(api = legacy)
                    else Profiles(active = legacy.authMode, oauth = legacy,
                        api = legacy.copy(authMode = AgentAuthMode.API_KEY, model = "", endpoint = OPENAI_API_ROOT))
                }
                HttpAgentModel.validateSettings(profiles.api, allowEmptyModel = true)
                HttpAgentModel.validateSettings(profiles.oauth, allowEmptyModel = true)
                if (root.int("contextVersion") < 2) {
                    // The former untouched defaults cannot fit several rounds of the detailed reference and tool schemas.
                    fun upgradeDefaults(options: AgentSettings): AgentSettings =
                        if (options.tokenBudget == 60000 && options.maxRounds == 16 && options.maxOutputTokens == 4096 &&
                            options.contextChars == 90000 && options.timeoutSeconds == 90) options.copy(tokenBudget = 180000) else options
                    profiles = profiles.copy(contextVersion = 2, api = upgradeDefaults(profiles.api), oauth = upgradeDefaults(profiles.oauth))
                    files.write("settings.json", agentGson.toJson(profiles), 128000)
                }
            }
            val keyFile = files.path("api-key.enc").toFile()
            if (keyFile.exists()) storedKey = AccountVault.read(keyFile) ?: ""
        }.onFailure { loadWarning = "模型设置未能完整读取：" + it.agentError(); profiles = Profiles() }
    }
    fun profile(mode: AgentAuthMode): AgentSettings = if (mode == AgentAuthMode.API_KEY) profiles.api else profiles.oauth
    fun apiKey(options: AgentSettings): String = System.getenv(options.apiKeyEnvironment)?.takeIf { it.isNotBlank() } ?: storedKey
    fun apiKey(): String = apiKey(profile(AgentAuthMode.API_KEY))
    fun keyForEditing(): String = storedKey
    @Synchronized fun save(updated: AgentSettings, key: String) {
        HttpAgentModel.validateSettings(updated, allowEmptyModel = true)
        require(key.length <= 16000 && !key.contains('\n') && !key.contains('\r')) { "API Key 格式无效" }
        val next = if (updated.authMode == AgentAuthMode.API_KEY) profiles.copy(active = updated.authMode, api = updated)
            else profiles.copy(active = updated.authMode, oauth = updated)
        if (updated.authMode == AgentAuthMode.API_KEY) AccountVault.write(files.path("api-key.enc").toFile(), key.trim())
        files.write("settings.json", agentGson.toJson(next), 128000)
        if (updated.authMode == AgentAuthMode.API_KEY) storedKey = key.trim()
        profiles = next
        onChanged(updated)
    }
    @Synchronized fun switch(mode: AgentAuthMode) {
        val next = profiles.copy(active = mode)
        files.write("settings.json", agentGson.toJson(next), 128000)
        profiles = next
        onChanged(settings)
    }
    @Synchronized fun selectModel(mode: AgentAuthMode, id: String, expected: AgentSettings) {
        if (profile(mode) != expected) return
        val updated = expected.copy(model = id)
        val next = if (mode == AgentAuthMode.API_KEY) profiles.copy(api = updated) else profiles.copy(oauth = updated)
        files.write("settings.json", agentGson.toJson(next), 128000)
        profiles = next
    }
}

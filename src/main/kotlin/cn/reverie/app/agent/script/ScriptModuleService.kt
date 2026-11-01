package cn.reverie.app.agent.script

import cn.reverie.app.agent.*
import cn.reverie.module.*
import com.google.gson.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Persisted JavaScript-backed modules use the same controls, categories and settings as ClickGUI. */
class ScriptModuleService(
    private val scripts: ScriptService,
    private val dispatch: (() -> Unit) -> Unit,
    private val register: (Module) -> Unit = ModuleManager::register,
    private val unregister: (Module) -> Unit = ModuleManager::unregister,
    private val find: (String) -> Module? = ModuleManager::getModuleByName,
    private val writeSnapshot: (String) -> Unit = { scripts.repository.files.write("script-modules.json", it, FILE_LIMIT) }
) : AutoCloseable {
    private data class Binding(val definition: JsonObject, val module: Module, val scriptId: String)
    private val bindings = linkedMapOf<String, Binding>()
    private val persistence: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "Nocturne-Script-Modules").apply { isDaemon = true } }
    private val saveLock = Any()
    private var lastSaved = ""
    private var pendingSave: String? = null
    private var writerScheduled = false
    private var damagedFile = false
    @Volatile private var closed = false
    private val files get() = scripts.repository.files
    fun restore() {
        val entries = runCatching {
            files.read("script-modules.json", FILE_LIMIT.toLong())?.let { JsonParser.parseString(it).asJsonArray.also { value ->
                require(value.size() <= 32) { "模块记录超过 32 项" }
            } }
        }.getOrElse {
            damagedFile = true
            scripts.recordError("@modules", "模块恢复失败，已保留原文件：" + it.agentError())
            return
        } ?: return
        synchronized(saveLock) { lastSaved = agentGson.toJson(entries) }
        entries.forEach { raw ->
            runCatching { create(raw.asJsonObject, restoring = true) }.onFailure {
                damagedFile = true
                scripts.recordError("@modules", "模块恢复失败，已保留原文件：" + it.agentError())
            }
        }
    }
    fun list(): JsonArray = JsonArray().apply { bindings.values.forEach { add(snapshot(it)) } }
    fun forScript(id: String): JsonObject = bindings.values.firstOrNull { it.scriptId == id }?.let { snapshot(it) }
        ?: error("当前脚本尚未绑定 ClickGUI 模块")
    fun create(arguments: JsonObject, restoring: Boolean = false, idempotent: Boolean = false): JsonObject {
        check(!closed) { "脚本模块系统已关闭" }
        val definition = validate(arguments)
        val id = definition.string("id"); val scriptId = definition.string("script")
        bindings[id]?.let { existing ->
            if (idempotent && existing.scriptId == scriptId) return snapshot(existing)
        }
        require(id !in bindings) { "模块 ID 已存在" }
        require(bindings.size < 32) { "最多添加 32 个脚本模块" }
        require(bindings.values.none { it.scriptId == scriptId }) { "一个脚本只能绑定一个 ClickGUI 模块" }
        require(find(definition.string("name")) == null) { "模块名称已存在，请使用唯一名称" }
        scripts.repository.read(scriptId)
        val proposed = list().apply { add(definition.deepCopy().apply { addProperty("enabled", false); addProperty("key", definition.int("key", -1)) }) }
        require(agentGson.toJson(proposed).toByteArray(Charsets.UTF_8).size <= FILE_LIMIT) { "脚本模块持久化数据超过 4 MB；请减少文本设置" }
        if (!restoring) prepareChange()
        val module = object : Module(definition.string("name"), definition.string("description"),
            Category.valueOf(definition.string("category")), definition.int("key", -1)) {
            override fun onEnable() {
                if (closed || bindings[id]?.module !== this) { enabled = false; return }
                val state = scripts.status(scriptId).state
                if (state in setOf(ScriptState.RUNNING, ScriptState.STARTING)) return
                try {
                    scripts.start(scriptId).whenComplete { status, failure ->
                        if (failure != null || status?.state != ScriptState.RUNNING) dispatch {
                            if (closed || bindings[id]?.module !== this) return@dispatch
                            val actual = scripts.status(scriptId)
                            if (actual.state in setOf(ScriptState.RUNNING, ScriptState.STARTING)) return@dispatch
                            if (enabled) enabled = false
                            scripts.recordError(scriptId, actual.error.ifEmpty { failure?.agentError() ?: status?.error ?: "脚本未启动" })
                        }
                    }
                } catch (failure: Throwable) {
                    // Module.enabled changes before onEnable; revert it to keep ClickGUI truthful.
                    enabled = false
                    scripts.recordError(scriptId, failure.agentError())
                    throw failure
                }
            }
            override fun onDisable() { if (!closed) scripts.stop(scriptId) }
        }
        definition.getAsJsonArray("settings")?.forEach { raw ->
            val item = raw.asJsonObject
            val name = item.string("name"); val description = item.string("description")
            module.addSetting(when (item.string("type")) {
                "boolean" -> BooleanSetting(name, description, item.get("value").asBoolean)
                "number" -> NumberSetting(name, description, item.get("value").asDouble, item.get("min").asDouble,
                    item.get("max").asDouble, item.get("step")?.asDouble ?: 0.1)
                "mode" -> ModeSetting(name, description, item.string("value"), item.getAsJsonArray("modes").map { it.asString })
                else -> StringSetting(name, description, item.string("value"))
            })
        }
        val binding = Binding(definition, module, scriptId)
        bindings[id] = binding; register(module)
        if (scripts.status(scriptId).state in setOf(ScriptState.RUNNING, ScriptState.STARTING) || restoring && definition.boolean("enabled"))
            runCatching { module.enabled = true }
        if (!restoring) persist()
        return snapshot(binding)
    }
    fun remove(id: String, owner: String = "@agent"): JsonObject {
        val binding = bindings[id] ?: error("脚本模块不存在")
        require(owner == "@agent" || binding.scriptId == owner) { "只能删除当前脚本拥有的模块" }
        prepareChange()
        if (binding.module.enabled) binding.module.enabled = false
        scripts.stop(binding.scriptId)
        unregister(binding.module); bindings.remove(id); persist()
        return jsonObject("removed" to id, "scriptPreserved" to true)
    }
    fun runtimeChanged(status: ScriptStatus) {
        if (closed) return
        val binding = bindings.values.firstOrNull { it.scriptId == status.id } ?: return
        // Queued start/failure notifications can arrive after a later restart or stop.
        val wanted = scripts.status(status.id).state in setOf(ScriptState.RUNNING, ScriptState.STARTING)
        if (binding.module.enabled != wanted) binding.module.enabled = wanted
    }
    fun onTick() {
        if (closed) return
        bindings.values.toList().forEach { binding ->
            if (!java.nio.file.Files.exists(files.path("scripts/" + binding.scriptId + ".js"), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                if (binding.module.enabled) binding.module.enabled = false
                unregister(binding.module); bindings.remove(binding.definition.string("id"))
            }
        }
        persist()
    }
    private fun snapshot(binding: Binding): JsonObject = binding.definition.deepCopy().apply {
        addProperty("enabled", binding.module.enabled); addProperty("key", binding.module.keyCode)
        add("settings", JsonArray().apply { binding.definition.getAsJsonArray("settings")?.forEach { raw ->
            val setting = raw.asJsonObject.deepCopy()
            binding.module.settings.firstOrNull { it.name == setting.string("name") }?.let { setting.add("value", agentGson.toJsonTree(it.value)) }
            add(setting)
        } })
    }
    private fun persist() {
        if (closed || damagedFile) return
        val content = agentGson.toJson(list())
        enqueueSave(content)
    }
    private fun prepareChange() {
        if (!damagedFile) return
        // Recovery is triggered by an explicit module edit, never by the startup/tick/close path.
        files.read("script-modules.json", FILE_LIMIT.toLong())?.let { original ->
            files.write("module-backups/${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.json", original, FILE_LIMIT)
        }
        damagedFile = false
    }
    private fun enqueueSave(content: String) = synchronized(saveLock) {
        if (content == pendingSave || !writerScheduled && content == lastSaved) return@synchronized
        pendingSave = content
        if (writerScheduled) return@synchronized
        writerScheduled = true
        persistence.execute {
            while (true) {
                val next = synchronized(saveLock) {
                    val value = pendingSave
                    pendingSave = null
                    if (value == null) writerScheduled = false
                    value
                } ?: break
                runCatching { writeSnapshot(next) }.onSuccess { synchronized(saveLock) { lastSaved = next } }
                    .onFailure { scripts.recordError("@modules", "模块保存失败，下次同步会重试：" + it.agentError()) }
            }
        }
    }
    override fun close() {
        if (closed) return
        val finalState = agentGson.toJson(list())
        closed = true
        if (!damagedFile) enqueueSave(finalState)
        persistence.shutdown()
        // The final snapshot shares the same writer; an older asynchronous write cannot overwrite it.
        runCatching { persistence.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS) }
        bindings.values.toList().forEach { binding ->
            if (binding.module.enabled) binding.module.enabled = false
            unregister(binding.module)
        }
        bindings.clear()
    }
    companion object {
        internal const val FILE_LIMIT = 4_000_000
        internal fun validate(arguments: JsonObject): JsonObject {
            val result = arguments.deepCopy()
            AgentFiles.requireId(result.string("id")); AgentFiles.requireId(result.string("script"))
            require(result.string("name").length in 1..64 && result.string("name").none { it.isISOControl() || it == '§' }) { "模块名称无效" }
            require(result.string("description").length <= 500)
            require(result.string("category", "MISC") in Category.entries.map { it.name }) { "模块分类无效" }
            result.addProperty("category", result.string("category", "MISC"))
            require(result.int("key", -1) in -1..348) { "按键编码无效" }
            val entries = result.getAsJsonArray("settings") ?: JsonArray().also { result.add("settings", it) }
            require(entries.size() <= 24 && entries.map { it.asJsonObject.string("name").lowercase() }.distinct().size == entries.size()) { "设置数量或名称无效" }
            entries.forEach { raw ->
                val entry = raw.asJsonObject
                require(entry.string("name").length in 1..64 && !entry.string("name").equals("Hidden", true) && entry.string("name").none { it.isISOControl() || it == '§' })
                require(entry.string("description").length <= 500)
                val value = entry.get("value") ?: error("设置需要默认 value")
                when (entry.string("type")) {
                    "boolean" -> require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
                    "number" -> {
                        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble.isFinite())
                        val min = entry.get("min")?.asDouble ?: error("数值设置需要 min")
                        val max = entry.get("max")?.asDouble ?: error("数值设置需要 max")
                        val step = entry.get("step")?.asDouble ?: 0.1
                        require(min.isFinite() && max.isFinite() && step.isFinite() && min < max && step > 0 && value.asDouble in min..max && max - min < 1e12)
                    }
                    "mode" -> {
                        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                        val modes = entry.getAsJsonArray("modes") ?: error("模式设置需要 modes")
                        require(modes.size() in 1..32 && modes.all { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.length in 1..100 } && modes.any { it.asString == value.asString })
                    }
                    "string" -> require(value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.length <= 4096)
                    else -> error("设置类型只支持 boolean、number、mode、string")
                }
            }
            return result
        }
    }
}

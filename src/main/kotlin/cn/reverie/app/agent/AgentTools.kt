package cn.reverie.app.agent

import cn.reverie.app.agent.script.ScriptHost
import cn.reverie.app.agent.script.ScriptService
import cn.reverie.app.agent.script.ScriptState
import com.google.gson.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AgentTool(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val mutating: Boolean = false,
    val execute: (JsonObject, AgentCancellation) -> JsonElement
) {
    fun spec(): JsonObject = jsonObject("name" to name, "description" to description, "parameters" to parameters)
}

/** Tool definitions are the single source of truth for schemas, policy and execution. */
class AgentTools {
    private val definitions = linkedMapOf<String, AgentTool>()
    fun register(tool: AgentTool) { require(tool.name !in definitions); definitions[tool.name] = tool }
    fun find(name: String) = definitions[name]
    internal fun installResultReader(results: AgentToolResults, session: () -> AgentSession) {
        register(AgentTool("read_tool_result", AgentToolDocumentation.describe("read_tool_result", "分页读取当前会话中已经执行的工具完整结果", false),
            schema("resultId" to idField("超长结果返回的 resultId，属于当前会话；不是 callId 或脚本 id"),
                "offset" to numberField("integer", "UTF-16 偏移，默认 0；继续读取用 endOffset", 0, Int.MAX_VALUE),
                "limit" to numberField("integer", "字符上限，默认 8000，范围 1..12000", 1, 12000), required = listOf("resultId"))) { args, signal ->
            signal.check(); results.read(session().id, args)
        })
    }
    fun specs(mode: AgentPermissionMode): JsonArray = JsonArray().apply {
        definitions.values.filter { mode != AgentPermissionMode.READ_ONLY || !it.mutating }.forEach { add(it.spec()) }
    }
    fun validate(tool: AgentTool, args: JsonObject) = validateValue(args, tool.parameters, "参数")
    private fun validateValue(value: JsonElement, schema: JsonObject, path: String) {
        for (keyword in listOf("oneOf", "anyOf")) schema.getAsJsonArray(keyword)?.let { choices ->
            val matches = choices.count { runCatching { validateValue(value, it.asJsonObject, path) }.isSuccess }
            require(if (keyword == "oneOf") matches == 1 else matches > 0) { "$path 不符合允许的参数结构；请检查 type、必需字段和 value 类型" }
        }
        when (schema.string("type")) {
            "object" -> {
                require(value.isJsonObject) { "$path 必须是对象" }
                val obj = value.asJsonObject
                schema.getAsJsonArray("required")?.forEach { require(obj.has(it.asString)) { "$path 缺少 ${it.asString}" } }
                val props = schema.getAsJsonObject("properties") ?: JsonObject()
                if (!schema.boolean("additionalProperties", true)) require(obj.keySet().all { props.has(it) }) { "$path 包含未知字段" }
                obj.entrySet().forEach { (key, item) -> props.getAsJsonObject(key)?.let { validateValue(item, it, "$path.$key") } }
            }
            "string" -> {
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$path 必须是字符串" }
                require(value.asString.length in schema.int("minLength", 0)..schema.int("maxLength", Int.MAX_VALUE)) { "$path 字符串长度超出范围" }
                schema.get("pattern")?.let { require(Regex(it.asString).containsMatchIn(value.asString)) { "$path 格式无效" } }
            }
            "boolean" -> require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$path 必须是布尔值" }
            "number", "integer" -> {
                require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble.isFinite()) { "$path 必须是有限数字" }
                if (schema.string("type") == "integer") require(value.asDouble == value.asDouble.toLong().toDouble()) { "$path 必须是整数" }
                schema.get("minimum")?.let { require(value.asDouble >= it.asDouble) { "$path 小于最小值 " + it.asDouble } }
                schema.get("maximum")?.let { require(value.asDouble <= it.asDouble) { "$path 大于最大值 " + it.asDouble } }
                schema.get("exclusiveMinimum")?.let { require(value.asDouble > it.asDouble) { "$path 必须大于 " + it.asDouble } }
            }
            "array" -> {
                require(value.isJsonArray && value.asJsonArray.size() in schema.int("minItems", 0)..schema.int("maxItems", 100)) { "$path 数组长度超出范围" }
                if (schema.boolean("uniqueItems")) require(value.asJsonArray.toList().distinct().size == value.asJsonArray.size()) { "$path 不能有重复项" }
                schema.getAsJsonObject("items")?.let { item -> value.asJsonArray.forEachIndexed { index, child -> validateValue(child, item, "$path[$index]") } }
            }
        }
        schema.getAsJsonArray("enum")?.let { require(value in it) { "$path 不在允许值中" } }
    }
    companion object {
        internal fun schema(vararg properties: Pair<String, JsonObject>, required: List<String> = properties.map { it.first }): JsonObject =
            jsonObject("type" to "object", "properties" to JsonObject().apply { properties.forEach { (key, value) -> add(key, value) } },
                "required" to required, "additionalProperties" to false)
        internal fun field(type: String, description: String, values: List<String>? = null) = jsonObject("type" to type, "description" to description).apply {
            values?.let { add("enum", agentGson.toJsonTree(it)) }
        }
        private fun textField(description: String, min: Int = 0, max: Int = 4096) =
            field("string", description).apply { addProperty("minLength", min); addProperty("maxLength", max) }
        private fun idField(description: String = "脚本 id，不是显示名称；1..64 个字母/数字/_/-，以字母或数字开头，禁止 Windows 保留名") =
            textField(description, 1, 64).apply { addProperty("pattern", "^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$") }
        private fun numberField(type: String, description: String, min: Number, max: Number) =
            field(type, description).apply { addProperty("minimum", min); addProperty("maximum", max) }
        private fun capabilitiesField() = jsonObject("type" to "array", "description" to "只声明实际需要的能力；无需求用 []。用户授权独立，不由 AI 授予。",
            "items" to field("string", "脚本能力", ScriptRepository.SCRIPT_CAPABILITIES.toList()), "maxItems" to 4, "uniqueItems" to true)
        private fun listSchema(tail: Boolean = false): JsonObject {
            val props = linkedMapOf("offset" to numberField("integer", "列表偏移，默认 0；后续页用 endOffset", 0, Int.MAX_VALUE),
                "limit" to numberField("integer", "一页最多条数，默认 20，范围 1..50", 1, 50))
            if (tail) props["tail"] = field("boolean", "默认 true 读取最近一页；offset 显式提供时按该位置读取，false 从最早开始")
            return schema(*props.toList().toTypedArray(), required = emptyList())
        }
        private fun sourceSchema(attached: Boolean = false): JsonObject {
            val props = linkedMapOf<String, JsonObject>()
            if (!attached) props["id"] = idField()
            props["offset"] = numberField("integer", "UTF-16 字符偏移，默认 0；必须使用返回的 endOffset 继续", 0, 256000)
            props["limit"] = numberField("integer", "字符数上限，默认 8000；JSON 转义较多时返回较短页", 1, 12000)
            return schema(*props.toList().toTypedArray(), required = if (attached) emptyList() else listOf("id"))
        }
        internal fun moduleSettingsSchema(): JsonObject {
            fun setting(type: String, value: JsonObject, vararg extra: Pair<String, JsonObject>): JsonObject = schema(
                "name" to textField("唯一设置名，不能叫 Hidden；脚本按此 name 读取", 1, 64),
                "type" to field("string", "设置类型", listOf(type)),
                "value" to value, "description" to textField("此项用途，可省略", max = 500), *extra,
                required = listOf("name", "type", "value") + extra.map { it.first }.filter { it != "step" })
            val number = setting("number", field("number", "默认有限数字，必须位于 min..max"),
                "min" to field("number", "最小值，必须小于 max"), "max" to field("number", "最大值，区间小于 1e12"),
                "step" to field("number", "步长，默认 0.1，必须大于 0").apply { addProperty("exclusiveMinimum", 0) })
            val modes = jsonObject("type" to "array", "description" to "允许的模式字符串", "minItems" to 1, "maxItems" to 32,
                "items" to textField("模式名称", 1, 100))
            return jsonObject("type" to "array", "description" to "ClickGUI 可调设置定义，最多 24 项，name 不重复；每项按 type 提供完整结构。无设置用 []。",
                "maxItems" to 24, "items" to jsonObject("anyOf" to listOf(
                    setting("boolean", field("boolean", "默认 true/false")),
                    number, setting("mode", textField("默认模式，必须属于 modes", 1, 100), "modes" to modes),
                    setting("string", textField("默认文本，最多 4096 字符")))))
        }
        fun sourceHash(source: String): String = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        fun client(scripts: ScriptService, host: ScriptHost, attachment: () -> AgentScriptAttachment? = { null },
                   session: () -> AgentSession): AgentTools = AgentTools().apply {
            fun tool(name: String, description: String, schema: JsonObject = schema(), mutation: Boolean = false,
                     action: (JsonObject, AgentCancellation) -> JsonElement) =
                register(AgentTool(name, AgentToolDocumentation.describe(name, description, mutation), schema, mutation, action))
            val workspace = AgentWorkspaceContext(scripts, host)
            tool("client_context", "读取当前客户端和脚本工作区详细快照") { _, cancel -> workspace.read(cancel) }
            tool("list_scripts", "分页列出本地脚本、运行状态和已授予的能力", listSchema()) { args, _ ->
                AgentToolPaging.list(scripts.repository.list().map {
                    val grants = scripts.repository.grants(it.id)
                    jsonObject("manifest" to it, "status" to scripts.status(it.id), "grants" to grants, "missingGrants" to it.capabilities - grants)
                }, args)
            }
            tool("read_script", "分页读取磁盘脚本及完整 hash、运行状态和权限", sourceSchema()) { args, _ ->
                val id = args.string("id")
                val record = scripts.repository.read(id)
                val grants = scripts.repository.grants(id)
                AgentToolPaging.source(record.source, jsonObject("manifest" to record.manifest,
                    "hash" to sourceHash(record.source), "status" to scripts.status(id),
                    "grants" to grants, "missingGrants" to record.manifest.capabilities - grants), args)
            }
            tool("read_attached_script", "分页读取用户选中的编辑器草稿", sourceSchema(attached = true)) { args, _ ->
                val current = attachment() ?: error("本次任务没有附带脚本；请在脚本页选中文件并开启附带选中脚本")
                AgentToolPaging.source(current.source, current.summary().apply { add("manifest", agentGson.toJsonTree(current.manifest)) }, args)
            }
            tool("script_api", "读取详细的 JavaScript API、示例和 Agent 工作流",
                schema("topic" to field("string", "手册章节，省略返回 all；小上下文建议分章读取", AgentKnowledge.topics),
                    required = emptyList())) { args, _ -> JsonPrimitive(AgentKnowledge.page(args.string("topic", "all"))) }
            tool("validate_script", "仅检查 JavaScript 语法，不执行代码",
                schema("source" to textField("待检查的完整 JavaScript，不包含 Markdown 代码围栏", 1, 256000))) { args, _ ->
                val error = scripts.validate("draft", args.string("source")); jsonObject("valid" to (error == null), "error" to error)
            }
            tool("write_script", "创建或修改脚本。已有脚本必须先读取并提供 expectedHash。保存不自动执行，不能授予权限。运行中的脚本需先停止。",
                schema("id" to idField(), "source" to textField("完整 JavaScript 源码，非 Markdown；最多 256000 UTF-8 字节", 1, 256000),
                    "expectedHash" to textField("刚从 read_script 返回的完整磁盘 SHA-256；仅新脚本用空字符串", max = 64).apply { addProperty("pattern", "^([a-f0-9]{64})?$") },
                    "description" to textField("完整用途描述，修改时保留原用途", max = 500), "name" to textField("显示名称，可省略保留原名", max = 100), "capabilities" to capabilitiesField(),
                    required = listOf("id", "source", "expectedHash", "description", "capabilities")), true) { args, signal ->
                signal.check()
                val id = AgentFiles.requireId(args.string("id"))
                require(scripts.status(id).state !in setOf(ScriptState.RUNNING, ScriptState.STARTING, ScriptState.STOPPING)) { "先停止运行中的脚本" }
                synchronized(scripts.repository) {
                    val old = scripts.repository.files.read("scripts/$id.js")
                    require(if (old == null) args.string("expectedHash").isEmpty() else sourceHash(old) == args.string("expectedHash")) { "脚本已变化，请重新读取" }
                    val source = args.string("source")
                    scripts.validate(id, source)?.let { error(it) }
                    val prior = old?.let { scripts.repository.manifest(id) }
                    val manifest = ScriptManifest(id, args.string("name", prior?.name ?: id), args.string("description"),
                        args.getAsJsonArray("capabilities").map { it.asString }.toSet(), prior?.autoStart ?: false)
                    signal.check()
                    scripts.repository.save(ScriptRecord(manifest, source))
                    jsonObject("saved" to id, "hash" to sourceHash(source), "missingGrants" to (manifest.capabilities - scripts.repository.grants(id)))
                }
            }
            tool("create_script", "创建新的 JavaScript 脚本，自行编写源码并检查语法。不会自动运行或授予权限。脚本已存在时改用 read_script/write_script。",
                schema("id" to idField("全新唯一脚本 id；不能是已有文件或 Windows 保留名"), "name" to textField("脚本显示名称，不是文件 id", 1, 100),
                    "source" to textField("完整 JavaScript 源码，不包含 Markdown；最多 256000 UTF-8 字节", 1, 256000),
                    "description" to textField("用途说明，解释行为与触发条件", max = 500), "capabilities" to capabilitiesField()), true) { args, signal ->
                val creation = args.deepCopy().apply { addProperty("expectedHash", "") }
                find("write_script")!!.execute(creation, signal)
            }
            tool("update_script_metadata", "修改脚本显示名称、描述和启动选项；保留源码与用户授权。运行中脚本需先停止。新增权限仍需用户授予。",
                schema("id" to idField(), "name" to textField("完整显示名称", 1, 100), "description" to textField("完整用途描述", max = 500),
                    "autoStart" to field("boolean", "下次客户端初始化自动运行，不等于立即启动"),
                    "capabilities" to capabilitiesField()), true) { args, signal ->
                val id = args.string("id")
                require(scripts.status(id).state !in setOf(ScriptState.RUNNING, ScriptState.STARTING, ScriptState.STOPPING)) { "先停止脚本" }
                synchronized(scripts.repository) {
                    val record = scripts.repository.read(id); signal.check()
                    val manifest = record.manifest.copy(name = args.string("name"), description = args.string("description"),
                        autoStart = args.boolean("autoStart"), capabilities = args.getAsJsonArray("capabilities").map { it.asString }.toSet())
                    scripts.repository.save(record.copy(manifest = manifest)); agentGson.toJsonTree(manifest)
                }
            }
            tool("read_script_storage", "分页读取脚本持久化 JSON 存储", sourceSchema()) { args, _ ->
                val id = args.string("id"); scripts.repository.read(id)
                val json = scripts.repository.storage(id).toString()
                AgentToolPaging.source(json, jsonObject("id" to id, "format" to "JSON", "hash" to sourceHash(json)), args)
                    .apply { add("json", remove("source")) }
            }
            tool("replace_script", "精确修改已有脚本的一段文本；oldText 必须非空且仅出现一次。提供完整文件 expectedHash，保留元数据和授权，检查语法，不自动执行。适合长脚本。",
                schema("id" to idField(), "expectedHash" to textField("read_script 返回的完整文件 SHA-256", 64, 64).apply { addProperty("pattern", "^[a-f0-9]{64}$") },
                    "oldText" to textField("非空且只匹配一次的原文，包含足够上下文", 1, 256000),
                    "newText" to textField("替换后的完整文字，空字符串表示删除该段", max = 256000)), true) { args, signal ->
                signal.check()
                val id = AgentFiles.requireId(args.string("id"))
                require(scripts.status(id).state !in setOf(ScriptState.RUNNING, ScriptState.STARTING, ScriptState.STOPPING)) { "先停止运行中的脚本" }
                synchronized(scripts.repository) {
                    val record = scripts.repository.read(id)
                    require(sourceHash(record.source) == args.string("expectedHash")) { "脚本已变化，请重新读取" }
                    val oldText = args.string("oldText")
                    require(oldText.isNotEmpty()) { "oldText 不能为空" }
                    val offset = record.source.indexOf(oldText)
                    require(offset >= 0 && record.source.indexOf(oldText, offset + 1) < 0) { "oldText 必须匹配唯一原文，请增加上下文" }
                    val source = record.source.replaceRange(offset, offset + oldText.length, args.string("newText"))
                    scripts.validate(id, source)?.let { error(it) }
                    signal.check()
                    scripts.repository.save(record.copy(source = source))
                    jsonObject("saved" to id, "hash" to sourceHash(source), "offset" to offset)
                }
            }
            tool("start_script", "启动已获权限的本地脚本；语法或启动错误会返回",
                schema("id" to idField()), true) { args, signal ->
                signal.check(); val status = scripts.start(args.string("id")).get(5, TimeUnit.SECONDS)
                require(status.state == ScriptState.RUNNING) { status.error.ifEmpty { "脚本未启动" } }
                agentGson.toJsonTree(status)
            }
            tool("stop_script", "停止脚本并释放其控制输入",
                schema("id" to idField()), true) { args, _ -> agentGson.toJsonTree(scripts.stop(args.string("id")).get(5, TimeUnit.SECONDS)) }
            tool("get_script_status", "读取指定脚本的启动/停止状态和错误", schema("id" to idField())) { args, _ ->
                val id = args.string("id"); val record = scripts.repository.read(id)
                val status = scripts.status(id); val grants = scripts.repository.grants(id)
                val missing = record.manifest.capabilities - grants
                agentGson.toJsonTree(status).asJsonObject.apply {
                    add("render", scripts.renderStatus(id))
                    add("capabilities", agentGson.toJsonTree(record.manifest.capabilities)); add("grants", agentGson.toJsonTree(grants))
                    add("missingGrants", agentGson.toJsonTree(missing))
                    addProperty("readyToStart", missing.isEmpty() && status.state in setOf(ScriptState.STOPPED, ScriptState.FAILED))
                    if (missing.isNotEmpty()) addProperty("userAction", "脚本 → 选中 $id → 权限 → 授予：" + missing.joinToString())
                }
            }
            val logSchema = listSchema(tail = true).apply {
                getAsJsonObject("properties").add("id", idField()); add("required", agentGson.toJsonTree(listOf("id")))
                getAsJsonObject("properties").getAsJsonObject("limit").addProperty("description", "一页最多条数，默认 30，范围 1..50")
            }
            tool("script_logs", "分页读取脚本日志，默认最近 30 条", logSchema) { args, _ ->
                val paging = args.deepCopy().apply { if (!has("tail")) addProperty("tail", !has("offset")) }
                AgentToolPaging.list(scripts.logs(args.string("id")).map { agentGson.toJsonTree(it) }, paging, 30)
                    .apply { addProperty("hasEarlier", int("offset") > 0) }
            }
            val operations = listOf(
                Triple("player_state", "读取玩家生命、位置、朝向、世界和服务器", schema()),
                Triple("inventory", "读取玩家背包", schema()),
                Triple("nearby_entities", "读取附近实体（距离 1 到 64 格）", schema("radius" to numberField("number", "半径格数，默认 16", 1, 64), required = emptyList())),
                Triple("list_modules", "分页列出客户端模块", listSchema()),
                Triple("get_module", "读取模块的状态和设置，可指定单个设置避免大量无关长文本", schema(
                    "name" to textField("真实模块显示名称，先 list_modules；忽略大小写", 1, 100),
                    "setting" to textField("可选，只读取此准确设置名；省略读取全部设置", 1, 100), required = listOf("name"))),
                Triple("list_script_modules", "分页列出脚本模块、脚本绑定和持久化设置", listSchema()),
                Triple("create_module", "把已有 JavaScript 脚本注册为真正的 ClickGUI 模块，持久化分类、按键和可调设置。开启模块运行脚本，关闭停止脚本；一个脚本绑定一个模块。不授予脚本权限。",
                    schema("id" to idField("唯一模块绑定 id，不是显示名称；规则与脚本 id 相同"), "script" to idField("已保存且语法正确的脚本 id"),
                        "name" to textField("唯一 ClickGUI 显示名称，不能含控制字符或 §", 1, 64),
                        "description" to textField("模块用途及触发条件", max = 500),
                        "category" to field("string", "ClickGUI 分类，例如 PLAYER，不能写中文分类名", listOf("COMBAT", "MOVEMENT", "RENDER", "PLAYER", "WORLD", "MISC")),
                        "key" to numberField("integer", "GLFW 按键码，可省略；-1 不绑定，例如 R=82", -1, 348),
                        "settings" to moduleSettingsSchema(),
                        required = listOf("id", "script", "name", "description", "category", "settings"))),
                Triple("remove_module", "删除脚本模块并停止其脚本，保留源码",
                    schema("id" to idField("list_script_modules 返回的模块 id，不是脚本 id 或显示 name"))),
                Triple("set_keybind", "设置模块按键绑定",
                    schema("name" to textField("模块显示名称，先 list_modules/get_module", 1, 100), "key" to numberField("integer", "GLFW 按键码，-1 取消绑定", -1, 348))),
                Triple("set_module", "开启或关闭客户端模块", schema("name" to textField("实际模块显示名称，不是文件 id", 1, 100), "enabled" to field("boolean", "true 开启，false 关闭，不能用字符串"))),
                Triple("set_setting", "修改模块设置，数值会限制在合法范围", schema("module" to textField("get_module 的 name", 1, 100),
                    "setting" to textField("get_module.settings 中准确的 name", 1, 100),
                    "value" to jsonObject("description" to "与设置类型一致的布尔/数字/字符串；mode 使用允许值，gui 不可修改",
                        "anyOf" to listOf(field("boolean", "布尔值"), field("number", "有限数字"), textField("模式名或文本", max = 4096))))),
                Triple("notify", "显示客户端本地通知", schema("text" to textField("本地通知文字，不发服务器", 1, 4000))),
                Triple("local_chat", "打印本地聊天文字", schema("text" to textField("本地聊天栏文字，不发服务器", 1, 4000))),
                Triple("send_chat", "向当前服务器发送消息或 /命令", schema("text" to textField("用户明确要求发到服务器的内容，不含换行，不能以 . 开头", 1, 256))),
                Triple("hold_key", "短时按住游戏输入（1 到 200 tick），到期自动释放",
                    schema("key" to field("string", "游戏输入名称，不是 GLFW 编码", listOf("forward", "back", "left", "right", "jump", "sneak", "sprint")),
                        "ticks" to numberField("integer", "持续 tick 数，默认 1；约 20 tick/秒", 1, 200), required = listOf("key"))),
                Triple("look", "设置玩家朝向", schema("yaw" to field("number", "水平角，有限度数"),
                    "pitch" to numberField("number", "俯仰角，单位度", -90, 90))),
                Triple("release_controls", "释放 Agent 持有的输入", schema())
            )
            val reads = setOf("player_state", "inventory", "nearby_entities", "list_modules", "get_module", "list_script_modules")
            operations.forEach { (name, description, schema) -> tool(name, description, schema, name !in reads) { args, signal ->
                val result = host.call("@agent", name, args, signal)
                when (name) {
                    "list_modules", "list_script_modules" -> AgentToolPaging.list(result.asJsonArray.toList(), args)
                    "get_module" -> if (args.has("setting")) result.asJsonObject.deepCopy().apply {
                        val all = getAsJsonArray("settings")
                        val selected = all.firstOrNull { it.asJsonObject.string("name").equals(args.string("setting"), true) }
                            ?: error("模块设置不存在：" + args.string("setting"))
                        addProperty("settingsTotal", all.size()); addProperty("selectedSetting", selected.asJsonObject.string("name"))
                        add("settings", JsonArray().apply { add(selected) })
                    } else result
                    "set_setting" -> result.asJsonObject.deepCopy().apply {
                        val all = remove("settings").asJsonArray
                        add("updatedSetting", all.firstOrNull { it.asJsonObject.string("name").equals(args.string("setting"), true) }
                            ?: error("设置已执行，但无法读取更新值；请用 get_module 重新核实"))
                        addProperty("settingsTotal", all.size())
                        add("readWith", jsonObject("tool" to "get_module", "arguments" to jsonObject("name" to string("name"))))
                    }
                    else -> result
                }
            } }
            tool("task_list", "查看本次会话的任务清单") { _, _ -> agentGson.toJsonTree(session().tasksSnapshot()) }
            tool("task_update", "创建或更新任务清单。完成后将状态改为 completed。",
                schema("id" to numberField("integer", "0 创建新条目；大于 0 必须是 task_list 的现有 id", 0, Int.MAX_VALUE),
                    "title" to textField("任务标题和可验证目标", 1, 200),
                    "status" to field("string", "状态", listOf("pending", "in_progress", "completed")))) { args, _ ->
                agentGson.toJsonTree(session().updateTask(args.int("id"), args.string("title"), args.string("status")))
            }
            tool("read_memory", "读取跨会话保留的用户偏好和脚本说明") { _, _ -> JsonPrimitive(scripts.repository.files.read("agent-memory.md", 16000) ?: "") }
            tool("write_memory", "写入跨会话说明，最多 16000 字节。不要存 API Key 或凭据。",
                schema("text" to textField("完整笔记，先 read_memory 保留有效内容；最多 16000 UTF-8 字节", max = 16000)), true) { args, signal ->
                signal.check(); scripts.repository.files.write("agent-memory.md", args.string("text"), 16000); jsonObject("saved" to true)
            }
        }
        internal val SCRIPT_API: String get() = AgentKnowledge.scriptApi
    }
}

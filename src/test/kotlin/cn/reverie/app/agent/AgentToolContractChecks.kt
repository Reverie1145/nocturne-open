package cn.reverie.app.agent

import cn.reverie.app.agent.script.*
import com.google.gson.*
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.*

class AgentToolContractChecks {
    private fun repository() = ScriptRepository(AgentFiles(Files.createTempDirectory("agent-contract")))
    private val host = object : ScriptHost {
        override fun call(owner: String, operation: String, arguments: JsonObject, cancellation: AgentCancellation): JsonElement =
            when (operation) {
                "list_modules", "list_script_modules" -> JsonArray().apply { repeat(41) { add(jsonObject("name" to "Module $it", "category" to "PLAYER", "enabled" to false)) } }
                "client_context" -> jsonObject("player" to jsonObject("inGame" to false))
                else -> JsonObject()
            }
        override fun ask(prompt: String, cancellation: AgentCancellation) = "unused"
        override fun release(owner: String) {}
    }

    @Test fun allFourModuleSettingsHaveCompleteTypedSchemasAndRejectMalformedDefinitionsBeforeMutation() {
        ScriptService(repository(), host).use { scripts ->
            val tools = AgentTools.client(scripts, host) { AgentSession() }
            val tool = tools.find("create_module")!!
            val base = jsonObject("id" to "watch-module", "script" to "watch", "name" to "Watch", "description" to "Local watch", "category" to "PLAYER")
            val cases = listOf(jsonObject("name" to "Threshold", "type" to "number", "value" to 8, "min" to 1, "max" to 20, "step" to 1),
                jsonObject("name" to "Enabled", "type" to "boolean", "value" to true),
                jsonObject("name" to "Channel", "type" to "mode", "value" to "Notify", "modes" to listOf("Notify", "Chat")),
                jsonObject("name" to "Prefix", "type" to "string", "value" to "注意"))
            val args = base.deepCopy().apply { add("settings", agentGson.toJsonTree(cases)) }
            tools.validate(tool, args)
            val schema = tool.parameters.getAsJsonObject("properties").getAsJsonObject("settings").getAsJsonObject("items")
            assertEquals(4, schema.getAsJsonArray("anyOf").size())
            assertContains(tool.description, "返回完整绑定对象")
            assertFailsWith<IllegalArgumentException> { tools.validate(tool, args.deepCopy().apply {
                getAsJsonArray("settings").first().asJsonObject.remove("min")
            }) }
            assertFailsWith<IllegalArgumentException> { tools.validate(tool, args.deepCopy().apply {
                getAsJsonArray("settings")[1].asJsonObject.addProperty("value", "true")
            }) }
            assertFailsWith<IllegalArgumentException> { tools.validate(tool, args.deepCopy().apply {
                getAsJsonArray("settings").first().asJsonObject.addProperty("step", 0)
            }) }
            assertFailsWith<IllegalArgumentException> { tools.validate(tool, args.deepCopy().apply {
                getAsJsonArray("settings").first().asJsonObject.addProperty("inventedField", "unsupported")
            }) }
            assertFailsWith<IllegalArgumentException> { tools.validate(tool, args.deepCopy().apply { addProperty("key", 999) }) }
            val readOnly = tools.specs(AgentPermissionMode.READ_ONLY).map { it.asJsonObject.string("name") }
            assertTrue("client_context" in readOnly && "read_attached_script" in readOnly && "get_script_status" in readOnly)
            assertFalse("create_module" in readOnly)
        }
    }

    @Test fun pagedListsPreserveEveryScriptAndModuleAndRuntimeStatusIncludesMissingGrants() {
        val repo = repository()
        repeat(41) { i -> repo.save(ScriptRecord(ScriptManifest("script-" + i, capabilities = setOf("ai")), "console.log('ready');")) }
        ScriptService(repo, host).use { scripts ->
            val tools = AgentTools.client(scripts, host) { AgentSession() }
            val status = tools.find("get_script_status")!!.execute(jsonObject("id" to "script-0"), AgentCancellation()).asJsonObject
            assertFalse(status.boolean("readyToStart")); assertEquals("ai", status.getAsJsonArray("missingGrants").single().asString)
            assertContains(status.string("userAction"), "脚本 → 选中 script-0 → 权限")
            for (name in listOf("list_scripts", "list_modules", "list_script_modules")) {
                val tool = tools.find(name)!!
                val items = mutableListOf<JsonElement>()
                var offset = 0; var more: Boolean
                do {
                    val args = jsonObject("offset" to offset, "limit" to 9)
                    tools.validate(tool, args)
                    val page = tool.execute(args, AgentCancellation()).asJsonObject
                    assertEquals(41, page.int("total"))
                    items += page.getAsJsonArray("items").toList()
                    offset = page.int("endOffset"); more = page.boolean("hasMore")
                } while (more)
                assertEquals(41, items.size)
                if (name == "list_scripts") {
                    assertEquals(41, items.map { it.asJsonObject.getAsJsonObject("manifest").string("id") }.distinct().size)
                    assertEquals("ai", items.first().asJsonObject.getAsJsonArray("missingGrants").first().asString)
                }
            }
        }
    }

    @Test fun largeStorageAndLogsReturnCompleteJsonPagesWithNoSilentOutputClipping() {
        val repo = repository()
        repo.save(ScriptRecord(ScriptManifest("large"), "console.log('ready');"))
        val expected = jsonObject("large" to "😀\"".repeat(6000))
        repo.storage("large", expected)
        ScriptService(repo, host).use { scripts ->
            val tools = AgentTools.client(scripts, host) { AgentSession() }
            val tool = tools.find("read_script_storage")!!
            val json = StringBuilder()
            var offset = 0; var more: Boolean
            do {
                val args = jsonObject("id" to "large", "offset" to offset, "limit" to 4001)
                tools.validate(tool, args)
                val page = tool.execute(args, AgentCancellation()).asJsonObject
                assertTrue(page.toString().length < 24000)
                json.append(page.string("json")); offset = page.int("endOffset"); more = page.boolean("hasMore")
            } while (more)
            assertEquals(expected, JsonParser.parseString(json.toString()))
            repeat(41) { scripts.recordError("large", "entry " + it) }
            val logs = tools.find("script_logs")!!.execute(jsonObject("id" to "large"), AgentCancellation()).asJsonObject
            assertEquals(30, logs.getAsJsonArray("items").size())
            assertTrue(logs.boolean("hasEarlier")); assertFalse(logs.boolean("hasMore"))
            val earlier = tools.find("script_logs")!!.execute(jsonObject("id" to "large", "offset" to 0, "limit" to 10), AgentCancellation()).asJsonObject
            assertEquals("entry 0", earlier.getAsJsonArray("items").first().asJsonObject.string("message"))
            assertTrue(earlier.boolean("hasMore"))
        }
    }

    @Test fun workspaceSummariesReportOmissionsAndScriptApiCanReadPreciseTopicPages() {
        val repo = repository()
        repeat(30) { repo.save(ScriptRecord(ScriptManifest("script-" + it, description = "详细说明".repeat(80)), "console.log('ready');")) }
        repo.files.write("agent-memory.md", "用户笔记".repeat(1000))
        ScriptService(repo, host).use { scripts ->
            val context = AgentWorkspaceContext(scripts, host).read(AgentCancellation(), 3000)
            assertTrue(context.toString().length <= 3000)
            assertTrue(context.getAsJsonObject("scripts").boolean("hasMore"))
            assertEquals(30, context.getAsJsonObject("scripts").int("total"))
            val tools = AgentTools.client(scripts, host) { AgentSession() }
            val api = tools.find("script_api")!!
            val modulePage = api.execute(jsonObject("topic" to "modules"), AgentCancellation()).asString
            assertContains(modulePage, "nocturne.modules.self()")
            assertContains(modulePage, "value:true")
            assertContains(modulePage, "modes:[")
            assertFalse(modulePage.contains("## ai"))
            assertContains(api.execute(JsonObject(), AgentCancellation()).asString, "## ai")
            assertFailsWith<IllegalArgumentException> { tools.validate(api, jsonObject("topic" to "made-up")) }
        }
    }
    @Test fun targetedModuleReadsAndMutationReceiptsAvoidUnrelatedLargeValuesWithoutChangingScriptHostOutput() {
        val module = jsonObject("name" to "Large Module", "category" to "PLAYER", "enabled" to false, "key" to -1,
            "settings" to ((0 until 23).map { jsonObject("name" to "Text-" + it, "type" to "string", "value" to "长😀".repeat(1000)) } +
                jsonObject("name" to "Threshold", "type" to "number", "value" to 8, "min" to 1, "max" to 20, "step" to 1)))
        var changes = 0
        val moduleHost = object : ScriptHost {
            override fun call(owner: String, operation: String, arguments: JsonObject, cancellation: AgentCancellation): JsonElement {
                if (operation == "set_setting") {
                    changes++
                    module.getAsJsonArray("settings").last().asJsonObject.addProperty("value", arguments.get("value").asDouble.coerceIn(1.0, 20.0))
                }
                return module.deepCopy()
            }
            override fun ask(prompt: String, cancellation: AgentCancellation) = ""
            override fun release(owner: String) {}
        }
        ScriptService(repository(), moduleHost).use { scripts ->
            val tools = AgentTools.client(scripts, moduleHost) { AgentSession() }
            val get = tools.find("get_module")!!
            val args = jsonObject("name" to "Large Module", "setting" to "threshold")
            tools.validate(get, args)
            val selected = get.execute(args, AgentCancellation()).asJsonObject
            assertEquals(24, selected.int("settingsTotal")); assertEquals("Threshold", selected.string("selectedSetting"))
            assertEquals(1, selected.getAsJsonArray("settings").size()); assertTrue(selected.toString().length < 24000)
            assertTrue(get.execute(jsonObject("name" to "Large Module"), AgentCancellation()).toString().length > 24000)
            assertFailsWith<IllegalStateException> { get.execute(jsonObject("name" to "Large Module", "setting" to "not-present"), AgentCancellation()) }
            val set = tools.find("set_setting")!!; val update = jsonObject("module" to "Large Module", "setting" to "Threshold", "value" to 99)
            tools.validate(set, update)
            val receipt = set.execute(update, AgentCancellation()).asJsonObject
            assertEquals(1, changes); assertEquals(20.0, receipt.getAsJsonObject("updatedSetting").get("value").asDouble)
            assertEquals(24, receipt.int("settingsTotal")); assertFalse(receipt.has("settings"))
            assertEquals(24, moduleHost.call("fixture-script", "get_module", JsonObject(), AgentCancellation()).asJsonObject.getAsJsonArray("settings").size())
        }
    }
}


package cn.reverie.app.agent

import cn.reverie.app.agent.script.*
import cn.reverie.module.*
import com.google.gson.*
import java.nio.file.Files
import java.util.concurrent.*
import kotlin.test.*

class ScriptModuleChecks {
    private val host = object : ScriptHost {
        override fun call(owner: String, operation: String, arguments: JsonObject, cancellation: AgentCancellation): JsonElement = JsonNull.INSTANCE
        override fun ask(prompt: String, cancellation: AgentCancellation) = ""
        override fun release(owner: String) {}
    }
    private fun definition(script: String = "bound") = jsonObject("id" to "custom-health", "script" to script, "name" to "Custom Health",
        "description" to "Script module fixture", "category" to "PLAYER", "key" to 72, "settings" to listOf(
            jsonObject("name" to "Threshold", "type" to "number", "value" to 8, "min" to 1, "max" to 20, "step" to 1),
            jsonObject("name" to "Notify", "type" to "boolean", "value" to true),
            jsonObject("name" to "Style", "type" to "mode", "value" to "Quiet", "modes" to listOf("Quiet", "Chat")),
            jsonObject("name" to "Message", "type" to "string", "value" to "Low health")))
    private fun pump(queue: ConcurrentLinkedQueue<() -> Unit>, condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        do {
            while (true) (queue.poll() ?: break).invoke()
            if (condition()) return
            Thread.sleep(5)
        } while (System.nanoTime() < until)
        assertTrue(condition())
    }
    @Test fun realClickGuiModuleRunsStopsAndRestoresScriptSettingsAndBinding() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("script-modules")))
        repository.save(ScriptRecord(ScriptManifest("bound"), "nocturne.on('start',function(){nocturne.storage.set('runs',nocturne.storage.get('runs',0)+1)});"))
        val registered = mutableListOf<Module>(); val queue = ConcurrentLinkedQueue<() -> Unit>()
        fun controller(scripts: ScriptService) = ScriptModuleService(scripts, { queue.add(it) },
            { registered.add(it) }, { registered.remove(it) }, { name -> registered.firstOrNull { it.name.equals(name, true) } })
        ScriptService(repository, host).use { scripts ->
            controller(scripts).use { modules ->
                scripts.onStatusChanged = { status -> queue.add { modules.runtimeChanged(status) } }
                val created = modules.create(definition())
                assertFalse(created.boolean("enabled")); assertEquals(Category.PLAYER, registered.single().category)
                val module = registered.single()
                assertEquals(5, module.settings.size) // Four supplied controls plus the shared Hidden control.
                module.enabled = true
                pump(queue) { scripts.status("bound").state == ScriptState.RUNNING }
                assertEquals(1, repository.storage("bound").int("runs"))
                module.enabled = false
                pump(queue) { scripts.status("bound").state == ScriptState.STOPPED }
                (module.settings.first { it.name == "Threshold" } as NumberSetting).setClamped(13.0)
                (module.settings.first { it.name == "Style" } as ModeSetting).value = "Chat"
                module.keyCode = 82; modules.onTick()
                assertEquals(13.0, modules.forScript("bound").getAsJsonArray("settings").first().asJsonObject.get("value").asDouble)
            }
            assertTrue(registered.isEmpty())
        }
        ScriptService(repository, host).use { scripts ->
            controller(scripts).use { modules ->
                modules.restore()
                val restored = registered.single()
                assertEquals(82, restored.keyCode)
                assertEquals(13.0, (restored.settings.first { it.name == "Threshold" } as NumberSetting).value)
                assertEquals("Chat", (restored.settings.first { it.name == "Style" } as ModeSetting).value)
                assertEquals("bound", modules.list().single().asJsonObject.string("script"))
                modules.remove("custom-health")
                assertTrue(registered.isEmpty()); assertEquals("bound", repository.read("bound").manifest.id)
            }
        }
    }
    @Test fun missingGrantsKeepModuleDisabledAndRemovalChecksScriptOwnership() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("module-grants")))
        repository.save(ScriptRecord(ScriptManifest("bound", capabilities = setOf("controls")), "console.log('should not start')"))
        val registered = mutableListOf<Module>()
        ScriptService(repository, host).use { scripts ->
            ScriptModuleService(scripts, {}, { registered.add(it) }, { registered.remove(it) }, { null }).use { modules ->
                modules.create(definition())
                assertFailsWith<IllegalArgumentException> { registered.single().enabled = true }
                assertFalse(registered.single().enabled); assertEquals(ScriptState.STOPPED, scripts.status("bound").state)
                assertFailsWith<IllegalArgumentException> { modules.remove("custom-health", "another-script") }
                assertEquals(1, modules.list().size()); modules.remove("custom-health", "bound")
                assertTrue(modules.list().isEmpty)
            }
        }
    }
    @Test fun queuedStaleStartNotificationsCannotRestartStoppedScriptsAndStopIsIdempotent() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("module-stop")))
        repository.save(ScriptRecord(ScriptManifest("bound"), "nocturne.on('stop',function(){console.log('cleanup')})"))
        val registered = mutableListOf<Module>()
        ScriptService(repository, host).use { scripts ->
            ScriptModuleService(scripts, {}, { registered.add(it) }, { registered.remove(it) }, { null }).use { modules ->
                modules.create(definition())
                scripts.start("bound").get(2, TimeUnit.SECONDS)
                val first = scripts.stop("bound"); val second = scripts.stop("bound")
                assertSame(first, second)
                assertEquals(ScriptState.STOPPED, first.get(2, TimeUnit.SECONDS).state)
                modules.runtimeChanged(ScriptStatus("bound", ScriptState.RUNNING))
                assertFalse(registered.single().enabled); assertEquals(ScriptState.STOPPED, scripts.status("bound").state)
                assertEquals(1, scripts.logs().count { it.message == "cleanup" })
            }
        }
    }
    @Test fun customSettingValidationRejectsUnsafeTypesAndDuplicateNames() {
        val base = definition()
        assertEquals(4, ScriptModuleService.validate(base).getAsJsonArray("settings").size())
        assertFailsWith<IllegalArgumentException> { ScriptModuleService.validate(base.deepCopy().apply { addProperty("category", "UNKNOWN") }) }
        assertFailsWith<IllegalArgumentException> { ScriptModuleService.validate(base.deepCopy().apply {
            getAsJsonArray("settings").add(getAsJsonArray("settings").first().deepCopy())
        }) }
        assertFailsWith<IllegalArgumentException> { ScriptModuleService.validate(base.deepCopy().apply {
            getAsJsonArray("settings").first().asJsonObject.addProperty("step", 0)
        }) }
    }
    @Test fun selfRegistrationIsIdempotentAndRemovedModuleReferencesCannotRestartScripts() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("module-idempotent")))
        repository.save(ScriptRecord(ScriptManifest("bound"), "console.log('bound')"))
        val registered = mutableListOf<Module>()
        ScriptService(repository, host).use { scripts ->
            ScriptModuleService(scripts, {}, { registered.add(it) }, { registered.remove(it) }, { null }).use { modules ->
                modules.create(definition())
                val original = registered.single()
                (original.settings.first { it.name == "Threshold" } as NumberSetting).value = 12.0
                modules.create(definition(), idempotent = true)
                assertEquals(1, registered.size)
                assertSame(original, registered.single())
                assertEquals(12.0, modules.forScript("bound").getAsJsonArray("settings").first().asJsonObject.get("value").asDouble)
                modules.remove("custom-health")
                original.enabled = true
                assertFalse(original.enabled); assertEquals(ScriptState.STOPPED, scripts.status("bound").state)
            }
        }
    }
    @Test fun agentCanCreateScriptsAndModuleToolsRemainSubjectToMutationPolicy() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("agent-create-script")))
        ScriptService(repository, host).use { scripts ->
            val tools = AgentTools.client(scripts, host) { AgentSession() }
            val args = jsonObject("id" to "generated", "name" to "Generated Script", "description" to "Created by model",
                "source" to "console.log('created')", "capabilities" to emptyList<String>())
            val tool = tools.find("create_script")!!
            tools.validate(tool, args); tool.execute(args, AgentCancellation())
            assertEquals("Generated Script", repository.read("generated").manifest.name)
            assertEquals(ScriptState.STOPPED, scripts.status("generated").state)
            assertFails { tool.execute(args, AgentCancellation()) }
            val readOnly = tools.specs(AgentPermissionMode.READ_ONLY).map { it.asJsonObject.string("name") }
            assertTrue("list_script_modules" in readOnly); assertFalse("create_module" in readOnly); assertFalse("create_script" in readOnly)
            assertTrue(tools.find("create_module")!!.mutating)
        }
    }
    @Test fun damagedModuleFileCannotBreakStartupOrBeOverwrittenUntilAnExplicitEditBacksItUp() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("damaged-modules")))
        repository.save(ScriptRecord(ScriptManifest("bound"), "console.log('ready');"))
        val original = "{ definitely broken module JSON"
        repository.files.write("script-modules.json", original)
        val registered = mutableListOf<Module>()
        ScriptService(repository, host).use { scripts ->
            ScriptModuleService(scripts, {}, { registered.add(it) }, { registered.remove(it) }, { null }).use { modules ->
                modules.restore(); modules.onTick()
                assertEquals(original, repository.files.read("script-modules.json"))
                assertTrue(registered.isEmpty()); assertContains(scripts.logs("@modules").single().message, "保留原文件")
            }
            assertEquals(original, repository.files.read("script-modules.json"))
            ScriptModuleService(scripts, {}, { registered.add(it) }, { registered.remove(it) }, { null }).use { modules ->
                modules.restore(); modules.create(definition())
            }
            assertEquals("custom-health", JsonParser.parseString(repository.files.read("script-modules.json")).asJsonArray.single().asJsonObject.string("id"))
            val backup = Files.list(repository.files.path("module-backups/.keep").parent).use { it.toList().single() }
            assertEquals(original, Files.readString(backup))
        }
    }
    @Test fun failedWritesAreRetriedAndTheFinalStateUsesTheSameOrderedWriter() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("module-retry")))
        repository.save(ScriptRecord(ScriptManifest("bound"), "console.log('ready');"))
        val registered = mutableListOf<Module>(); val writes = java.util.concurrent.atomic.AtomicInteger()
        val firstAttempt = CountDownLatch(1); val releaseFirst = CountDownLatch(1)
        ScriptService(repository, host).use { scripts ->
            ScriptModuleService(scripts, {}, { registered.add(it) }, { registered.remove(it) }, { null }, { content ->
                if (writes.incrementAndGet() == 1) { firstAttempt.countDown(); releaseFirst.await(3, TimeUnit.SECONDS); throw java.io.IOException("fixture write failure") }
                repository.files.write("script-modules.json", content, ScriptModuleService.FILE_LIMIT)
            }).use { modules ->
                modules.create(definition())
                assertTrue(firstAttempt.await(2, TimeUnit.SECONDS)); releaseFirst.countDown()
                pump(ConcurrentLinkedQueue()) { scripts.logs("@modules").any { it.message.contains("fixture write failure") } }
                modules.onTick()
                pump(ConcurrentLinkedQueue()) { repository.files.read("script-modules.json") != null }
                (registered.single().settings.first { it.name == "Threshold" } as NumberSetting).value = 13.0
                modules.onTick()
            }
            val persisted = JsonParser.parseString(repository.files.read("script-modules.json")).asJsonArray.single().asJsonObject
            assertEquals(13.0, persisted.getAsJsonArray("settings").first().asJsonObject.get("value").asDouble)
            assertTrue(writes.get() >= 2)
        }
    }
    @Test fun queuedFailureFromAnEarlierRunCannotDisableASuccessfulRestart() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("module-stale-failure")))
        repository.save(ScriptRecord(ScriptManifest("bound"), "throw new Error('earlier failure');"))
        val queue = ConcurrentLinkedQueue<() -> Unit>(); val registered = mutableListOf<Module>()
        ScriptService(repository, host).use { scripts ->
            ScriptModuleService(scripts, { queue.add(it) }, { registered.add(it) }, { registered.remove(it) }, { null }).use { modules ->
                modules.create(definition()); val module = registered.single(); module.enabled = true
                pump(ConcurrentLinkedQueue()) { scripts.status("bound").state == ScriptState.FAILED && queue.isNotEmpty() }
                module.enabled = false; scripts.stop("bound").get(2, TimeUnit.SECONDS)
                repository.save(ScriptRecord(ScriptManifest("bound"), "console.log('new run');"))
                module.enabled = true
                pump(ConcurrentLinkedQueue()) { scripts.status("bound").state == ScriptState.RUNNING }
                while (true) (queue.poll() ?: break).invoke()
                assertTrue(module.enabled); assertEquals(ScriptState.RUNNING, scripts.status("bound").state)
            }
        }
    }
    @Test fun largeAllowedSettingsPersistAndExceedingTotalCapacityIsRejectedBeforeRegistration() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("module-capacity")))
        val registered = mutableListOf<Module>()
        var accepted = 0; var rejected = false
        ScriptService(repository, host).use { scripts ->
            ScriptModuleService(scripts, {}, { registered.add(it) }, { registered.remove(it) }, { null }).use { modules ->
                for (index in 0 until 20) {
                    val script = "bound-" + index
                    repository.save(ScriptRecord(ScriptManifest(script), "console.log('ready');"))
                    val args = definition(script).apply {
                        addProperty("id", "large-" + index); addProperty("name", "Large " + index)
                        add("settings", agentGson.toJsonTree((0 until 24).map { jsonObject("name" to "Text-" + it, "type" to "string", "value" to "长".repeat(4096)) }))
                    }
                    try { modules.create(args); accepted++ } catch (failure: IllegalArgumentException) {
                        assertContains(failure.message!!, "4 MB"); rejected = true; break
                    }
                }
                assertTrue(rejected); assertTrue(accepted > 1)
                assertEquals(accepted, registered.size); assertEquals(accepted, modules.list().size())
            }
            val path = repository.files.path("script-modules.json")
            assertTrue(Files.size(path) > 256000); assertTrue(Files.size(path) <= ScriptModuleService.FILE_LIMIT)
            assertEquals(accepted, JsonParser.parseString(Files.readString(path)).asJsonArray.size())
        }
    }
    @Test fun oldWriteCompletingAfterCloseTimeoutCannotOverwriteTheFinalSnapshot() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("module-close-order")))
        repository.save(ScriptRecord(ScriptManifest("bound"), "console.log('ready');"))
        val registered = mutableListOf<Module>(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val finished = CountDownLatch(1); val writes = java.util.concurrent.atomic.AtomicInteger()
        ScriptService(repository, host).use { scripts ->
            val modules = ScriptModuleService(scripts, {}, { registered.add(it) }, { registered.remove(it) }, { null }, { content ->
                val index = writes.incrementAndGet()
                if (index == 1) { entered.countDown(); release.await(8, TimeUnit.SECONDS) }
                repository.files.write("script-modules.json", content, ScriptModuleService.FILE_LIMIT)
                if (index > 1) finished.countDown()
            })
            try {
                modules.create(definition()); assertTrue(entered.await(2, TimeUnit.SECONDS))
                (registered.single().settings.first { it.name == "Threshold" } as NumberSetting).value = 17.0
                modules.close() // The first write stays blocked past the three-second shutdown wait.
                assertTrue(registered.isEmpty()); release.countDown()
                assertTrue(finished.await(3, TimeUnit.SECONDS))
                val state = JsonParser.parseString(repository.files.read("script-modules.json")).asJsonArray.single().asJsonObject
                assertEquals(17.0, state.getAsJsonArray("settings").first().asJsonObject.get("value").asDouble)
                assertEquals(2, writes.get())
            } finally { release.countDown(); modules.close() }
        }
    }
}

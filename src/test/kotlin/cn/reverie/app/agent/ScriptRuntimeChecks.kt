package cn.reverie.app.agent

import cn.reverie.app.agent.script.*
import com.google.gson.*
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ScriptRuntimeChecks {
    private class Host : ScriptHost {
        val calls = CopyOnWriteArrayList<String>()
        val released = CopyOnWriteArrayList<String>()
        override fun call(owner: String, operation: String, arguments: JsonObject, cancellation: AgentCancellation): JsonElement {
            cancellation.check(); calls += operation
            return if (operation == "player_state") jsonObject("inGame" to true, "health" to 20) else JsonNull.INSTANCE
        }
        override fun ask(prompt: String, cancellation: AgentCancellation): String { cancellation.check(); return "answer:$prompt" }
        override fun release(owner: String) { released += owner }
    }
    private fun fixture(block: (ScriptRepository, ScriptService, Host) -> Unit) {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("nocturne-script-test")))
        val host = Host()
        ScriptService(repository, host).use { block(repository, it, host) }
    }
    private fun waitFor(block: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!block() && System.nanoTime() < until) Thread.sleep(10)
        assertTrue(block(), "condition did not settle")
    }
    @Test fun additionalModuleApisRunInJavascriptAndRespectDeclaredCapabilities() = fixture { repository, scripts, host ->
        val source = """
            nocturne.modules.listCustom();
            nocturne.modules.self();
            nocturne.modules.create({id:"custom",name:"Custom",description:"",category:"MISC",settings:[]});
            nocturne.modules.setKey("Custom",72);
            nocturne.modules.remove("custom");
        """.trimIndent()
        repository.save(ScriptRecord(ScriptManifest("custom", capabilities = setOf("modules")), source))
        repository.setGrants("custom", setOf("modules"))
        assertEquals(ScriptState.RUNNING, scripts.start("custom").get(3, TimeUnit.SECONDS).state)
        assertEquals(listOf("list_script_modules", "own_module", "create_module", "set_keybind", "remove_module"), host.calls.toList())
        repository.save(ScriptRecord(ScriptManifest("blocked"), "nocturne.modules.create({id:'blocked',name:'Blocked',settings:[]});"))
        assertEquals(ScriptState.FAILED, scripts.start("blocked").get(3, TimeUnit.SECONDS).state)
        assertEquals(1, host.calls.count { it == "create_module" })
    }
    @Test fun eventsTimersStorageAndCleanupRunInRealJavascript() = fixture { repository, scripts, host ->
        repository.save(ScriptRecord(ScriptManifest("lifecycle"), """
            nocturne.on("start", function () {
                nocturne.storage.set("runs", nocturne.storage.get("runs", 0) + 1);
                nocturne.setTimeout(function () { console.log("timer fired"); }, 0);
            });
            nocturne.on("tick", function (event) { if(event.tick === 1) nocturne.chat.print("tick"); });
            nocturne.on("stop", function () { console.log("cleanup"); });
        """.trimIndent()))
        assertEquals(ScriptState.RUNNING, scripts.start("lifecycle").get(3, TimeUnit.SECONDS).state)
        assertEquals(1, repository.storage("lifecycle").int("runs"))
        Thread.sleep(65); scripts.emit("tick", jsonObject("tick" to 1))
        waitFor { "local_chat" in host.calls && scripts.logs().any { it.message == "timer fired" } }
        assertEquals(ScriptState.STOPPED, scripts.stop("lifecycle").get(3, TimeUnit.SECONDS).state)
        assertTrue(scripts.logs().any { it.message == "cleanup" })
        assertTrue("lifecycle" in host.released)
        scripts.start("lifecycle").get(3, TimeUnit.SECONDS)
        assertEquals(2, repository.storage("lifecycle").int("runs"))
    }
    @Test fun infiniteLoopCannotCatchItsExecutionDeadline() = fixture { repository, scripts, _ ->
        repository.save(ScriptRecord(ScriptManifest("loop"), "try { while (true) {} } catch (e) { console.log('escaped'); }"))
        val started = System.nanoTime()
        val status = scripts.start("loop").get(3, TimeUnit.SECONDS)
        assertEquals(ScriptState.FAILED, status.state)
        assertTrue(status.error.contains("时限"))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000)
        assertFalse(scripts.logs().any { it.message == "escaped" })
    }
    @Test fun invalidScriptsCompileOnTheirWorkerAndFailWithoutExecutingTheirPrefix() = fixture { repository, scripts, host ->
        val source="nocturne.notify('must not execute'); function("
        repository.save(ScriptRecord(ScriptManifest("syntax-error"),source))
        val failedThread=java.util.concurrent.atomic.AtomicReference<String>()
        scripts.onStatusChanged={ if(it.state==ScriptState.FAILED)failedThread.set(Thread.currentThread().name) }
        assertNotNull(scripts.validate("syntax-error"))
        val status=scripts.start("syntax-error").get(3,TimeUnit.SECONDS)
        assertEquals(ScriptState.FAILED,status.state)
        assertTrue(status.error.isNotBlank());assertTrue(host.calls.isEmpty())
        assertEquals("Nocturne-Script-syntax-error",failedThread.get())
    }
    @Test fun cancellationDuringStartupSettlesBothFutures() = fixture { repository, scripts, _ ->
        repository.save(ScriptRecord(ScriptManifest("cancel"), "while (true) {}"))
        val start = scripts.start("cancel")
        val stop = scripts.stop("cancel")
        start.get(3, TimeUnit.SECONDS)
        assertEquals(ScriptState.STOPPED, stop.get(3, TimeUnit.SECONDS).state)
    }
    @Test fun safeScopeHasNoJavaPackagesReflectionOrNodeRuntime() = fixture { repository, scripts, _ ->
        repository.save(ScriptRecord(ScriptManifest("sandbox"), """
            console.log([typeof Packages,typeof java,typeof Java,typeof JavaAdapter,typeof require,
              typeof nocturne.getClass,typeof nocturne.log.getClass].join(","));
            console.log(typeof nocturne.player.state().health);
        """.trimIndent()))
        assertEquals(ScriptState.RUNNING, scripts.start("sandbox").get(3, TimeUnit.SECONDS).state)
        assertTrue(scripts.logs().any { it.message == List(7) { "undefined" }.joinToString(",") })
        assertTrue(scripts.logs().any { it.message == "number" })
    }
    @Test fun missingGrantBlocksStartupAndUndeclaredCapabilityBlocksHostCall() = fixture { repository, scripts, host ->
        repository.save(ScriptRecord(ScriptManifest("privileged", capabilities = setOf("modules")), "nocturne.modules.setEnabled('Example',true)"))
        assertFailsWith<IllegalArgumentException> { scripts.start("privileged") }
        assertTrue(host.calls.isEmpty())
        repository.setGrants("privileged", setOf("modules"))
        assertEquals(ScriptState.RUNNING, scripts.start("privileged").get(3, TimeUnit.SECONDS).state)
        repository.save(ScriptRecord(ScriptManifest("unprivileged"), "nocturne.modules.setEnabled('Example',true)"))
        assertEquals(ScriptState.FAILED, scripts.start("unprivileged").get(3, TimeUnit.SECONDS).state)
        assertEquals(1, host.calls.count { it == "set_module" })
    }
    @Test fun syntaxCheckNeverExecutesAndOversizedStorageDoesNotReplaceData() = fixture { repository, scripts, host ->
        assertNull(scripts.validate("draft", "nocturne.notify('should not execute');"))
        assertTrue(host.calls.isEmpty())
        assertNotNull(scripts.validate("draft", "function ("))
        repository.save(ScriptRecord(ScriptManifest("storage"), """
            nocturne.storage.set("keep", 42);
            try { nocturne.storage.set("oversized", "x".repeat(70000)); } catch(e) { console.log("blocked"); }
        """.trimIndent()))
        scripts.start("storage").get(3, TimeUnit.SECONDS)
        assertEquals(42, repository.storage("storage").int("keep"))
        assertFalse(repository.storage("storage").has("oversized"))
    }
    @Test fun asyncAiCallbackRunsWithScriptState() = fixture { repository, scripts, _ ->
        repository.save(ScriptRecord(ScriptManifest("ai", capabilities = setOf("ai")), "nocturne.ai.ask('hello',function(answer,error){ nocturne.storage.set('answer',answer); });"))
        repository.setGrants("ai", setOf("ai"))
        scripts.start("ai").get(3, TimeUnit.SECONDS)
        waitFor { repository.storage("ai").string("answer") == "answer:hello" }
    }
    @Test fun stopPreventsLaterEventsAndDuplicateStartIsRejected() = fixture { repository, scripts, host ->
        repository.save(ScriptRecord(ScriptManifest("once"), "nocturne.on('key',function(){nocturne.notify('key');});"))
        scripts.start("once").get(3, TimeUnit.SECONDS)
        assertFailsWith<IllegalArgumentException> { scripts.start("once") }
        scripts.stop("once").get(3, TimeUnit.SECONDS)
        scripts.emit("key", jsonObject("key" to 1)); Thread.sleep(50)
        assertTrue(host.calls.isEmpty())
    }
    @Test fun longScriptsCanBeReadAndEditedWithoutTruncationOrLostMetadata() = fixture { repository, scripts, host ->
        val source = ("// " + "\\\"".repeat(20) + "😀\n").repeat(1500) + "var answer = 42;\n"
        val manifest = ScriptManifest("large", "长脚本", "keep metadata", setOf("ai"), true)
        repository.save(ScriptRecord(manifest, source)); repository.setGrants("large", setOf("ai"))
        val tools = AgentTools.client(scripts, host) { AgentSession() }
        val read = tools.find("read_script")!!
        val reconstructed = StringBuilder()
        var offset = 0
        do {
            val arguments = jsonObject("id" to "large", "offset" to offset, "limit" to 12000)
            tools.validate(read, arguments)
            val page = read.execute(arguments, AgentCancellation()).asJsonObject
            assertEquals(AgentTools.sourceHash(source), page.string("hash"))
            assertTrue(page.toString().length <= 22000)
            reconstructed.append(page.string("source"))
            offset = page.int("endOffset")
        } while (page.boolean("hasMore"))
        assertEquals(source, reconstructed.toString())
        val replace = tools.find("replace_script")!!
        assertTrue(replace.mutating)
        val change = jsonObject("id" to "large", "expectedHash" to AgentTools.sourceHash(source),
            "oldText" to "var answer = 42;", "newText" to "var answer = 43;")
        tools.validate(replace, change)
        replace.execute(change, AgentCancellation())
        assertEquals(ScriptRecord(manifest, source.replace("42;", "43;")), repository.read("large"))
        assertEquals(setOf("ai"), repository.grants("large")); assertTrue(host.calls.isEmpty())
        assertFailsWith<IllegalArgumentException> { replace.execute(change, AgentCancellation()) }
        val ambiguous = jsonObject("id" to "large", "expectedHash" to AgentTools.sourceHash(repository.read("large").source),
            "oldText" to "//", "newText" to "/*")
        assertFailsWith<IllegalArgumentException> { replace.execute(ambiguous, AgentCancellation()) }
        assertEquals(source.replace("42;", "43;"), repository.read("large").source)
    }
}

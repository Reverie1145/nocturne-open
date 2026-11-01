package cn.reverie.app.agent

import cn.reverie.app.agent.script.*
import com.google.gson.*
import java.nio.file.Files
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class ScriptDrawingChecks {
    private class Host : ScriptHost {
        val calls = CopyOnWriteArrayList<String>()
        var keyEntered: CountDownLatch? = null
        var keyRelease: CountDownLatch? = null
        override fun call(owner: String, operation: String, arguments: JsonObject, cancellation: AgentCancellation): JsonElement {
            cancellation.check(); calls += operation
            if (operation == "player_state") {
                keyEntered?.countDown(); keyRelease?.await(200, TimeUnit.MILLISECONDS)
                return jsonObject("inGame" to true, "name" to "很长的玩家名称 Player", "health" to 12,
                    "x" to 24, "y" to 64, "z" to -18)
            }
            return JsonNull.INSTANCE
        }
        override fun ask(prompt: String, cancellation: AgentCancellation) = error("unexpected AI request")
        override fun release(owner: String) {}
    }
    private fun fixture(source: String, block: (ScriptRepository, ScriptService, Host) -> Unit) {
        val repo = ScriptRepository(AgentFiles(Files.createTempDirectory("nocturne-draw-check")))
        repo.save(ScriptRecord(ScriptManifest("draw"), source))
        val host = Host()
        ScriptService(repo, host).use { scripts ->
            assertEquals(ScriptState.RUNNING, scripts.start("draw").get(3, TimeUnit.SECONDS).state)
            block(repo, scripts, host)
        }
    }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(condition(), "drawing did not settle")
    }
    private fun frame(scripts: ScriptService, width: Float = 640f, height: Float = 360f): ScriptDrawFrame {
        val result = AtomicReference<ScriptDrawFrame>()
        waitFor {
            scripts.render(ScriptRenderInput(width, height)) { _, value -> result.set(value) }
            assertEquals(ScriptState.RUNNING, scripts.status("draw").state, scripts.status("draw").error)
            result.get() != null
        }
        return result.get()
    }
    @Test fun documentedCompleteHudRunsInRhinoWithoutGrantsAndActuallyPublishesDrawingCommands() = fixture(AgentKnowledge.renderingExample) { repo, scripts, host ->
        assertNull(scripts.validate("draw"))
        repeat(60) { scripts.emit("tick", jsonObject("tick" to (it + 1) * 5)); Thread.sleep(3) }
        val output = frame(scripts)
        assertTrue(output.commands.size in 50..1024)
        assertTrue(output.commands.any { it.op == "fillPaint" && it.paint?.kind == "boxGradient" })
        assertTrue(output.commands.any { it.op == "text" && "生命值" in it.text })
        assertTrue(output.commands.any { it.op == "stroke" })
        assertTrue(output.commands.count { it.op == "lineTo" } >= 3)
        assertTrue(scripts.renderStatus("draw").int("framesDrawn") > 0)
        assertTrue(repo.grants("draw").isEmpty())
        assertTrue(host.calls.all { it == "player_state" })
        val compact = frame(scripts, 220f, 200f)
        assertTrue(compact.commands.size < output.commands.size)
        assertTrue(compact.commands.none { it.op == "text" && "15 秒" in it.text })
    }
    @Test fun colorsPaintsAndNanoVgAliasStayPlainDataAndUseRgbaOrder() = fixture("""
        var color=nocturne.render.rgba(255,0,128,0.5);
        var paint=nocturne.nvg.linearGradient(0,0,100,100,color,"#00000000");
        console.log(typeof paint.getClass,typeof nocturne.nvg.context,typeof nocturne.nvg.beginFrame);
        nocturne.on("render2d",function(e){
          var v=nocturne.nvg;v.beginPath();v.rect(1,2,100,20);v.fillPaint(paint);v.fill();
          v.fontSize(18);v.textAlign("right","middle");v.fillColor(color);v.text(110,30,"中文文本");
        });
    """.trimIndent()) { _, scripts, _ ->
        val output = frame(scripts)
        val paint = output.commands.first { it.op == "fillPaint" }.paint!!
        assertEquals(1f, paint.inner.r); assertEquals(0f, paint.inner.g)
        assertEquals(128f / 255f, paint.inner.b); assertEquals(128f / 255f, paint.inner.a)
        assertTrue(scripts.logs("draw").any { it.message == "undefined undefined undefined" })
    }
    @Test fun stoppingImmediatelyClearsNativeOutputAndLateCallbacksCannotReviveIt() = fixture("""
        nocturne.on("render2d",function(e){var v=nocturne.render;v.beginPath();v.rect(0,0,50,50);v.fill();});
    """.trimIndent()) { _, scripts, _ ->
        frame(scripts)
        val stopped = scripts.stop("draw")
        scripts.render(ScriptRenderInput(640f,360f)) { _, _ -> fail("stopped script was drawn") }
        assertEquals(0, scripts.renderStatus("draw").int("commands"))
        assertFalse(scripts.renderStatus("draw").boolean("recentlyDrawn"))
        assertEquals(ScriptState.STOPPED, stopped.get(3, TimeUnit.SECONDS).state)
        assertFalse(scripts.hasDrawing())
    }
    @Test fun unregisteringLastRenderListenerClearsThePanel() = fixture("""
        var handle=nocturne.on("render2d",function(e){var v=nocturne.render;v.beginPath();v.circle(20,20,5);v.fill();});
        nocturne.on("key",function(){nocturne.off(handle);});
    """.trimIndent()) { _, scripts, _ ->
        frame(scripts); scripts.emit("key")
        waitFor { !scripts.renderStatus("draw").boolean("subscribed") }
        assertEquals(0, scripts.renderStatus("draw").int("commands"))
        scripts.render(ScriptRenderInput(640f,360f)) { _, _ -> fail("unregistered overlay was drawn") }
    }
    @Test fun emptyFramesReplacePreviousContentInsteadOfAccumulatingPixels() = fixture("""
        var visible=true;nocturne.on("key",function(){visible=false;});
        nocturne.on("render2d",function(e){if(!visible)return;var v=nocturne.render;v.beginPath();v.rect(0,0,50,50);v.fill();});
    """.trimIndent()) { _, scripts, _ ->
        frame(scripts); scripts.emit("key")
        waitFor { scripts.render(ScriptRenderInput(640f,360f)) { _, _ -> }; scripts.renderStatus("draw").int("commands") == 0 }
        scripts.render(ScriptRenderInput(640f,360f)) { _, _ -> fail("empty frame reused old geometry") }
        assertEquals(ScriptState.RUNNING, scripts.status("draw").state)
    }
    @Test fun busyRenderEventsAreCoalescedAndUseTheNewestViewport() = fixture("""
        nocturne.on("key",function(){nocturne.player.state();});
        nocturne.on("render2d",function(e){var v=nocturne.render;v.beginPath();v.rect(0,0,e.width,20);v.fill();});
    """.trimIndent()) { _, scripts, host ->
        host.keyEntered = CountDownLatch(1); host.keyRelease = CountDownLatch(1)
        scripts.emit("key"); assertTrue(host.keyEntered!!.await(1, TimeUnit.SECONDS))
        repeat(100) { scripts.render(ScriptRenderInput(300f + it, 360f)) { _, _ -> fail("worker should still be blocked") } }
        assertTrue(scripts.renderStatus("draw").int("coalescedFrames") >= 99)
        scripts.clearDrawing() // Invalidates even the event queued before a world/resolution change.
        scripts.render(ScriptRenderInput(800f, 500f)) { _, _ -> fail("old frame survived clear") }
        host.keyRelease!!.countDown()
        waitFor { scripts.renderStatus("draw").int("framesBuilt") > 0 }
        val output = frame(scripts, 800f, 500f)
        assertEquals(800f, output.commands.first { it.op == "rect" }.numbers[2])
        assertTrue(scripts.renderStatus("draw").int("framesBuilt") <= 3)
        assertEquals(0, scripts.status("draw").droppedEvents)
    }
    @Test fun aCachedFrameForAnotherCanvasOrScreenIsNeverReplayed() = fixture("""
        nocturne.on("render2d",function(e){var v=nocturne.render;v.beginPath();v.circle(10,10,4);v.fill();});
    """.trimIndent()) { _, scripts, _ ->
        frame(scripts)
        scripts.render(ScriptRenderInput(320f,240f,2f,screen="ClickGuiScreen")) { _, output ->
            assertEquals(320f, output.input.width); assertEquals(2f, output.input.pixelRatio)
            assertEquals("ClickGuiScreen", output.input.screen)
        }
    }
    @Test fun unbalancedSaveRejectsTheEntireFrameAndClearsPreviousDrawing() = fixture("""
        var broken=false;nocturne.on("key",function(){broken=true;});
        nocturne.on("render2d",function(e){var v=nocturne.render;if(broken)v.save();v.beginPath();v.circle(20,20,5);v.fill();});
    """.trimIndent()) { _, scripts, _ ->
        frame(scripts); scripts.emit("key")
        waitFor { scripts.render(ScriptRenderInput(640f,360f)) { _, _ -> }; scripts.status("draw").state == ScriptState.FAILED }
        assertContains(scripts.status("draw").error, "未配对")
        assertEquals(0, scripts.renderStatus("draw").int("commands"))
    }
    @Test fun renderCannotBlockOnMinecraftOrWriteStorage() {
        listOf("nocturne.player.state();", "nocturne.storage.set('bad',1);", "nocturne.ai.ask('bad',function(){});").forEach { body ->
            fixture("nocturne.on('render2d',function(e){$body});") { repo, scripts, host ->
                scripts.render(ScriptRenderInput(640f,360f)) { _, _ -> fail("invalid frame published") }
                waitFor { scripts.status("draw").state == ScriptState.FAILED }
                assertTrue(host.calls.isEmpty()); assertFalse(repo.storage("draw").has("bad"))
            }
        }
    }
    @Test fun nativeFailureIsContainedToOneScriptAndReportedToTheAgent() = fixture("""
        nocturne.on("render2d",function(e){var v=nocturne.render;v.beginPath();v.circle(10,10,4);v.fill();});
    """.trimIndent()) { _, scripts, _ ->
        frame(scripts)
        scripts.render(ScriptRenderInput(640f,360f)) { _, _ -> throw IllegalStateException("renderer fixture error") }
        waitFor { scripts.status("draw").state == ScriptState.FAILED }
        assertContains(scripts.status("draw").error, "NanoVG 绘制失败")
        assertEquals(0, scripts.renderStatus("draw").int("commands"))
    }
    @Test fun pureRecorderRejectsUnsafeArgumentsPaintMutationsAndStackUnderflow() {
        fun args(vararg values: Any) = values.map { agentGson.toJsonTree(it) }
        val recorder = ScriptDrawRecorder(ScriptRenderInput(640f,360f))
        assertFailsWith<IllegalArgumentException> { recorder.record("restore", emptyList()) }
        assertFailsWith<IllegalArgumentException> { recorder.record("lineTo", args(10,20)) }
        assertFailsWith<IllegalArgumentException> { recorder.record("fillColor", args(0xFFFFFF)) }
        assertFailsWith<IllegalArgumentException> { recorder.record("fontSize", listOf(JsonPrimitive(Double.NaN))) }
        assertFailsWith<IllegalArgumentException> { recorder.record("translate", listOf(JsonPrimitive(Double.POSITIVE_INFINITY),JsonPrimitive(0))) }
        assertFailsWith<IllegalArgumentException> { recorder.record("text", args(1,2,"bad\nline")) }
        assertFailsWith<IllegalArgumentException> { recorder.record("text", args(1,2,"bad\u0000line")) }
        recorder.record("beginPath", emptyList())
        assertFailsWith<IllegalArgumentException> { recorder.record("rect", args(0,0,-1,20)) }
        assertFailsWith<IllegalArgumentException> { ScriptDrawRecorder.gradient("radialGradient", args(0,0,10,5,"#000000","#FFFFFF")) }
        val paint=ScriptDrawRecorder.gradient("linearGradient", args(0,0,10,10,"#000000","#FFFFFF")).asJsonObject
        paint.add("values", agentGson.toJsonTree(listOf(0,0,Double.MAX_VALUE,0)))
        assertFailsWith<IllegalArgumentException> { recorder.record("fillPaint", listOf(paint)) }
        assertTrue(recorder.finish().commands.none { it.op == "fillPaint" || it.op == "rect" })
    }
    @Test fun effectiveTransformAndCommandBudgetsPreventUnboundedNativeWork() {
        val input = ScriptRenderInput(640f,360f)
        val commands = ScriptDrawRecorder(input)
        repeat(ScriptDrawRecorder.MAX_COMMANDS) { commands.record("fillColor", listOf(JsonPrimitive("#FFFFFF"))) }
        assertFailsWith<IllegalArgumentException> { commands.record("fillColor", listOf(JsonPrimitive("#FFFFFF"))) }
        val stack = ScriptDrawRecorder(input)
        repeat(ScriptDrawRecorder.MAX_DEPTH) { stack.record("save", emptyList()) }
        assertFailsWith<IllegalArgumentException> { stack.record("save", emptyList()) }
        repeat(ScriptDrawRecorder.MAX_DEPTH) { stack.record("restore", emptyList()) }; stack.finish()
        val transformed = ScriptDrawRecorder(input)
        transformed.record("scale", listOf(JsonPrimitive(16),JsonPrimitive(16)))
        assertFailsWith<IllegalArgumentException> { transformed.record("scale", listOf(JsonPrimitive(16),JsonPrimitive(16))) }
        val geometry=ScriptDrawRecorder(input)
        geometry.record("beginPath", emptyList())
        repeat(ScriptDrawRecorder.MAX_GEOMETRY/32) { geometry.record("circle", listOf(JsonPrimitive(0),JsonPrimitive(0),JsonPrimitive(1))) }
        assertFailsWith<IllegalArgumentException> { geometry.record("circle", listOf(JsonPrimitive(0),JsonPrimitive(0),JsonPrimitive(1))) }
    }
    @Test fun drawingOutsideRenderIsRejectedButColorFactoriesCanBeCached() {
        val repo=ScriptRepository(AgentFiles(Files.createTempDirectory("nocturne-outside-render")))
        ScriptService(repo,Host()).use { scripts ->
            repo.save(ScriptRecord(ScriptManifest("bad"),"nocturne.render.beginPath();"))
            assertEquals(ScriptState.FAILED,scripts.start("bad").get(3,TimeUnit.SECONDS).state)
            assertContains(scripts.status("bad").error,"只能在 render2d")
        }
    }
    @Test fun upgradeInstallsOneFullDrawingExampleAndDoesNotResurrectDeletedExamples() {
        val repo=ScriptRepository(AgentFiles(Files.createTempDirectory("nocturne-render-install")))
        repo.files.write("examples-installed.txt","1",100)
        ScriptService(repo,Host()).use { scripts ->
            scripts.installExamples()
            assertEquals(AgentKnowledge.renderingExample,repo.read("vector-hud").source)
            assertNull(scripts.validate("vector-hud")); assertTrue(repo.read("vector-hud").manifest.capabilities.isEmpty())
            repo.trash("vector-hud"); scripts.installExamples()
            assertNull(repo.readOrNull("vector-hud")); assertNull(repo.readOrNull("welcome"))
        }
    }
    @Test fun clearingWhileACallbackRunsDiscardsItsLateFrame() {
        val repo=ScriptRepository(AgentFiles(Files.createTempDirectory("nocturne-late-draw")))
        val record=ScriptRecord(ScriptManifest("late"),"""
            nocturne.on("render2d",function(e){console.log("entered");var v=nocturne.render;v.beginPath();v.rect(0,0,e.width,20);v.fill();});
        """.trimIndent())
        repo.save(record)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=java.util.concurrent.atomic.AtomicBoolean(true)
        val pool=Executors.newSingleThreadExecutor()
        val runtime=ScriptRuntime(record,repo,Host(),pool,log={ log ->
            if(log.message=="entered"&&first.compareAndSet(true,false)){entered.countDown();check(release.await(200,TimeUnit.MILLISECONDS))}
        })
        try {
            assertEquals(ScriptState.RUNNING,runtime.start().get(3,TimeUnit.SECONDS).state)
            assertNull(runtime.render(ScriptRenderInput(640f,360f)))
            assertTrue(entered.await(1,TimeUnit.SECONDS))
            runtime.clearDrawing();release.countDown()
            waitFor { !runtime.renderStatus().boolean("queued") }
            assertEquals(0,runtime.renderStatus().int("framesBuilt"))
            assertEquals(0,runtime.renderStatus().int("commands"))
            val output=AtomicReference<ScriptDrawFrame?>()
            waitFor { output.set(runtime.render(ScriptRenderInput(800f,500f)));output.get()!=null }
            assertEquals(800f,output.get()!!.input.width)
        } finally { release.countDown();runtime.stop().get(3,TimeUnit.SECONDS);pool.shutdownNow() }
    }
    @Test fun anInfiniteRenderCallbackCannotCatchItsBudgetOrLeaveADrawing() = fixture("""
        nocturne.on("render2d",function(e){try{while(true){}}catch(error){console.log("escaped");}});
    """.trimIndent()) { _, scripts, _ ->
        scripts.render(ScriptRenderInput(640f,360f)) { _, _ -> fail("infinite callback published a frame") }
        waitFor { scripts.status("draw").state==ScriptState.FAILED }
        assertContains(scripts.status("draw").error,"时限")
        assertTrue(scripts.logs("draw").none { it.message=="escaped" })
        assertEquals(0,scripts.renderStatus("draw").int("commands"))
    }
}

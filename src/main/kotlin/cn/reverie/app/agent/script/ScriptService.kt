package cn.reverie.app.agent.script

import cn.reverie.app.agent.*
import com.google.gson.JsonObject
import java.util.concurrent.*

class ScriptService(val repository: ScriptRepository, private val host: ScriptHost) : AutoCloseable {
    @Volatile private var closed = false
    private val runtimes = ConcurrentHashMap<String, ScriptRuntime>()
    private val logs = ArrayDeque<ScriptLog>()
    var onStatusChanged: (ScriptStatus) -> Unit = {}
    fun recordError(id: String, message: String) = synchronized(logs) {
        logs.addLast(ScriptLog(id = id, level = "ERROR", message = message.take(1500)))
        while (logs.size > 500) logs.removeFirst()
    }
    private val aiExecutor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
        ThreadFactory { Thread(it, "Nocturne-Script-AI").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    fun logs(id: String? = null): List<ScriptLog> = synchronized(logs) {
        logs.filter { id == null || it.id == id }
    }
    fun clearLogs() = synchronized(logs) { logs.clear() }
    fun status(id: String) = runtimes[id]?.status() ?: ScriptStatus(id, ScriptState.STOPPED)
    fun statuses(): List<ScriptStatus> = repository.list().map { status(it.id) }
    fun validate(id: String, source: String = repository.read(id).source): String? = ScriptContextFactory().validate(source, id)
    @Synchronized fun start(id: String): CompletableFuture<ScriptStatus> {
        check(!closed) { "脚本系统已关闭" }
        AgentFiles.requireId(id)
        runtimes[id]?.let {
            require(it.state !in setOf(ScriptState.RUNNING, ScriptState.STARTING, ScriptState.STOPPING)) { "脚本正在运行或停止中" }
        }
        require(runtimes.values.count { it.state in setOf(ScriptState.RUNNING, ScriptState.STARTING, ScriptState.STOPPING) } < 8) { "最多同时运行 8 个脚本" }
        val record = repository.read(id)
        val missing = record.manifest.capabilities - repository.grants(id)
        require(missing.isEmpty()) { "请先在脚本页面授予权限：${missing.joinToString()}" }
        // Compilation belongs to the script worker; ClickGUI module toggles must never parse JS on the game thread.
        val runtime = ScriptRuntime(record, repository, host, aiExecutor, onStatus = { onStatusChanged(it) }) { entry -> synchronized(logs) {
            logs.addLast(entry); while (logs.size > 500) logs.removeFirst()
        } }
        runtimes[id] = runtime
        onStatusChanged(runtime.status())
        return runtime.start()
    }
    fun stop(id: String): CompletableFuture<ScriptStatus> = runtimes[id]?.stop()
        ?: CompletableFuture.completedFuture(status(id))
    fun emit(event: String, data: JsonObject = JsonObject()) = runtimes.values.forEach { it.emit(event, data) }
    fun renderStatus(id: String): JsonObject = runtimes[id]?.renderStatus() ?: jsonObject("subscribed" to false,
        "queued" to false, "framesBuilt" to 0, "framesDrawn" to 0, "coalescedFrames" to 0, "commands" to 0, "recentlyDrawn" to false)
    fun hasDrawing() = !closed && runtimes.values.any { it.hasDrawing() }
    internal fun render(input: ScriptRenderInput, draw: (String, ScriptDrawFrame) -> Unit) {
        if (closed) return
        // Stable ordering prevents overlays from flickering when concurrent-map iteration changes.
        runtimes.entries.sortedBy { it.key }.forEach { (id, runtime) ->
            val frame = runtime.render(input) ?: return@forEach
            if (frame.commands.isEmpty()) return@forEach
            try { draw(id, frame); runtime.drawn(frame) }
            catch (failure: Throwable) { runtime.renderFailed(failure) }
        }
    }
    fun clearDrawing() = runtimes.values.forEach { it.clearDrawing() }
    fun autoStart() = repository.list().filter { it.autoStart }.forEach { manifest ->
        if (status(manifest.id).state in setOf(ScriptState.RUNNING, ScriptState.STARTING)) return@forEach
        runCatching { start(manifest.id) }.onFailure { failure -> synchronized(logs) {
            logs.addLast(ScriptLog(id = manifest.id, level = "ERROR", message = failure.agentError()))
        } }
    }
    fun stopAll() = runtimes.values.forEach { it.stop() }
    fun installExamples() {
        if (repository.files.read("examples-installed.txt") == "1") { installRenderExample(); return }
        val examples = listOf(
            ScriptRecord(ScriptManifest("welcome", "欢迎脚本", "启动时显示通知，按 G 打印玩家状态"),
                """
                nocturne.on("start", function () {
                    nocturne.notify("脚本已准备好，按 G 查看状态");
                });
                nocturne.on("key", function (event) {
                    if (event.key === 71) {
                        var player = nocturne.player.state();
                        nocturne.chat.print(player.inGame ? "生命值：" + player.health : "当前未进入世界");
                    }
                });
                """.trimIndent()),
            ScriptRecord(ScriptManifest("health-watch", "生命值提醒", "低血量时通知，通知间隔 10 秒"),
                """
                var lastAlert = 0;
                nocturne.on("tick", function (event) {
                    if (event.tick % 20 !== 0) return;
                    var player = nocturne.player.state();
                    if (player.inGame && player.health < 8 && Date.now() - lastAlert > 10000) {
                        nocturne.notify("生命值较低：" + player.health);
                        lastAlert = Date.now();
                    }
                });
                """.trimIndent()),
            ScriptRecord(ScriptManifest("ai-companion", "AI 游戏助手", "按 H 请求 AI 根据玩家状态给出建议", setOf("ai")),
                """
                nocturne.on("key", function (event) {
                    if (event.key !== 72) return;
                    var state = nocturne.player.state();
                    nocturne.ai.ask("根据游戏状态给出简短建议：" + JSON.stringify(state), function (answer, error) {
                        if (error) console.error(error);
                        else nocturne.chat.print(answer);
                    });
                });
                """.trimIndent())
        )
        examples.forEach { if (repository.files.read("scripts/${it.manifest.id}.js") == null) repository.save(it) }
        repository.files.write("examples-installed.txt", "1", 100)
        installRenderExample()
    }
    private fun installRenderExample() {
        if (repository.files.read("render-examples-installed.txt") == "1") return
        if (repository.files.read("scripts/vector-hud.js") == null) repository.save(ScriptRecord(
            ScriptManifest("vector-hud", "NanoVG 状态面板", "完整矢量 HUD：实时生命值、平滑动画、渐变、曲线图和位置；纯绘制，无需额外能力"),
            AgentKnowledge.renderingExample))
        repository.files.write("render-examples-installed.txt", "1", 100)
    }
    @Synchronized override fun close() { if (closed) return; closed = true; stopAll(); aiExecutor.shutdownNow() }
}

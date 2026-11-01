package cn.reverie.app.agent.script

import cn.reverie.app.agent.*
import com.google.gson.*
import com.google.gson.annotations.SerializedName
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

interface ScriptHost {
    fun call(owner: String, operation: String, arguments: JsonObject, cancellation: AgentCancellation): JsonElement
    fun ask(prompt: String, cancellation: AgentCancellation): String
    fun release(owner: String)
}
enum class ScriptState {
    @SerializedName("STOPPED") STOPPED, @SerializedName("STARTING") STARTING,
    @SerializedName("RUNNING") RUNNING, @SerializedName("STOPPING") STOPPING, @SerializedName("FAILED") FAILED
}
data class ScriptStatus(@field:SerializedName("id") val id: String, @field:SerializedName("state") val state: ScriptState,
                       @field:SerializedName("error") val error: String = "", @field:SerializedName("droppedEvents") val droppedEvents: Int = 0)
data class ScriptLog(@field:SerializedName("time") val time: Long = System.currentTimeMillis(), @field:SerializedName("id") val id: String,
                    @field:SerializedName("level") val level: String, @field:SerializedName("message") val message: String)

private class ScriptTerminated(message: String) : Error(message)

/** Instruction observation works in interpreter mode, including functions compiled by eval. */
internal class ScriptContextFactory : ContextFactory() {
    private data class Budget(val signal: AgentCancellation, val deadline: Long, val millis: Long)
    private val budget = ThreadLocal<Budget>()
    override fun makeContext(): Context = super.makeContext().apply {
        isInterpretedMode = true
        languageVersion = Context.VERSION_ES6
        instructionObserverThreshold = 5000
        maximumInterpreterStackDepth = 128
        setClassShutter { false }
        wrapFactory = object : WrapFactory() {
            override fun wrapAsJavaObject(cx: Context, scope: Scriptable, javaObject: Any?, staticType: Class<*>?): Scriptable =
                throw SecurityException("Java 互操作不可用")
        }
    }
    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        checkBudget()
    }
    fun checkBudget() {
        val current = budget.get() ?: throw ScriptTerminated("脚本缺少执行预算")
        if (current.signal.isCancelled || Thread.currentThread().isInterrupted) throw ScriptTerminated("脚本已停止")
        if (System.nanoTime() > current.deadline) throw ScriptTerminated("脚本超过执行时限（${current.millis} ms）")
    }
    fun <T> guarded(signal: AgentCancellation, millis: Long = 250, block: (Context) -> T): T {
        budget.set(Budget(signal, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis), millis))
        try { return call(ContextAction { cx -> checkBudget(); block(cx).also { checkBudget() } }) }
        finally { budget.remove() }
    }
    fun validate(source: String, name: String): String? = try {
        require(source.toByteArray(Charsets.UTF_8).size <= 256000) { "脚本超过 256 KB" }
        // Parsing doesn't execute user code; cold Rhino class loading must not consume its execution budget.
        guarded(AgentCancellation(), 2000) { it.compileString(source, "$name.js", 1, null) }
        null
    } catch (failure: Throwable) { describe(failure) }

    companion object {
        fun describe(failure: Throwable): String = if (failure is RhinoException)
            "${failure.details()} (${failure.sourceName()}:${failure.lineNumber()})"
        else failure.agentError()
    }
}

/** Each script has a serial worker. No Java objects, Minecraft objects or filesystem handles enter JS. */
internal class ScriptRuntime(
    val record: ScriptRecord,
    private val repository: ScriptRepository,
    private val host: ScriptHost,
    private val aiExecutor: ExecutorService,
    private val onStatus: (ScriptStatus) -> Unit = {},
    private val log: (ScriptLog) -> Unit
) {
    val id = record.manifest.id
    val cancellation = AgentCancellation()
    private val factory = ScriptContextFactory()
    private val threadRef = java.util.concurrent.atomic.AtomicReference<Thread>()
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(128),
        ThreadFactory { action -> Thread(action, "Nocturne-Script-$id").apply { isDaemon = true; threadRef.set(this) } },
        ThreadPoolExecutor.AbortPolicy())
    @Volatile var state = ScriptState.STARTING
        private set(value) { field = value; onStatus(status()) }
    @Volatile var error = ""
        private set
    private val dropped = AtomicInteger()
    private val tickQueued = AtomicBoolean()
    private var scope: ScriptableObject? = null
    private val handlers = linkedMapOf<Int, Pair<String, Function>>()
    private data class Timer(val callback: Function, var due: Long, val interval: Long)
    private val timers = linkedMapOf<Int, Timer>()
    private var nextHandle = 1
    private var lastAiAt = 0L
    private val pendingAi = AtomicInteger()
    private var storage = repository.storage(id)
    private val startedAt = System.nanoTime()
    @Volatile private var renderListeners = 0
    private val renderQueued = AtomicBoolean()
    private val renderInput = AtomicReference<ScriptRenderInput?>()
    private val drawFrame = AtomicReference<ScriptDrawFrame?>()
    private val drawGeneration = AtomicLong()
    private val framesBuilt = AtomicLong()
    private val framesDrawn = AtomicLong()
    private val skippedFrames = AtomicLong()
    private val lastDrawnAt = AtomicLong()
    private val lastRenderAt = AtomicLong()
    private var drawing: ScriptDrawRecorder? = null // Script worker only.
    @Volatile private var stopFuture: CompletableFuture<ScriptStatus>? = null
    fun status() = ScriptStatus(id, state, error, dropped.get())
    fun hasDrawing() = state == ScriptState.RUNNING && renderListeners > 0 && !cancellation.isCancelled
    fun start(): CompletableFuture<ScriptStatus> = submit {
        if (cancellation.isCancelled) return@submit status()
        try {
            val compiled = factory.guarded(cancellation, 2000) { cx ->
                require(record.source.toByteArray(Charsets.UTF_8).size <= 256000) { "脚本超过 256 KB" }
                val js = cx.initSafeStandardObjects()
                scope = js
                install(cx, js)
                cx.compileString(record.source, "$id.js", 1, null)
            }
            factory.guarded(cancellation) { cx ->
                val js = scope ?: error("脚本上下文尚未初始化")
                compiled.exec(cx, js, js)
                cancellation.check()
                state = ScriptState.RUNNING
                dispatch(cx, "start", JsonObject())
            }
            writeLog("INFO", "脚本已启动")
        } catch (failure: Throwable) { fail(failure) }
        status()
    }
    fun emit(event: String, data: JsonObject = JsonObject()) {
        if (state != ScriptState.RUNNING) return
        if (event == "tick" && !tickQueued.compareAndSet(false, true)) { dropped.incrementAndGet(); return }
        enqueue {
            try {
                if (state == ScriptState.RUNNING) factory.guarded(cancellation) { cx ->
                    dispatch(cx, event, data)
                    if (event == "tick") fireTimers(cx)
                }
            } catch (failure: Throwable) { fail(failure) }
            finally { if (event == "tick") tickQueued.set(false) }
        }
    }
    /** One pending frame per script; new render events replace the pending input, never flood its queue. */
    fun render(input: ScriptRenderInput): ScriptDrawFrame? {
        if (state != ScriptState.RUNNING || renderListeners == 0 || cancellation.isCancelled) return null
        renderInput.set(input)
        if (renderQueued.compareAndSet(false, true)) {
            try { worker.execute {
                try {
                    val generation = drawGeneration.get()
                    val current = renderInput.get() ?: return@execute
                    if (state != ScriptState.RUNNING || renderListeners == 0) return@execute
                    factory.guarded(cancellation) { cx ->
                        val recorder = ScriptDrawRecorder(current)
                        drawing = recorder
                        try {
                            val previous = lastRenderAt.getAndSet(current.nanoTime)
                            val event = jsonObject("width" to current.width, "height" to current.height, "pixelRatio" to current.pixelRatio,
                                "mouseX" to current.mouseX, "mouseY" to current.mouseY, "partialTicks" to current.partialTicks,
                                "time" to ((current.nanoTime - startedAt).coerceAtLeast(0) / 1e9),
                                "delta" to (if (previous == 0L) 0.0 else ((current.nanoTime - previous).coerceAtLeast(0) / 1e9).coerceAtMost(0.1)),
                                "screen" to current.screen, "screenOpen" to (current.screen != null), "theme" to current.theme)
                            dispatch(cx, "render2d", event)
                            val frame = recorder.finish().copy(generation = generation)
                            factory.checkBudget()
                            if (drawGeneration.get() == generation && state == ScriptState.RUNNING && renderListeners > 0 &&
                                renderInput.get()?.sameCanvas(current) == true && !cancellation.isCancelled) {
                                drawFrame.set(frame); framesBuilt.incrementAndGet()
                            }
                        } finally { drawing = null }
                    }
                } catch (failure: Throwable) { fail(failure) }
                finally { renderQueued.set(false) }
            } } catch (_: RejectedExecutionException) { renderQueued.set(false); skippedFrames.incrementAndGet() }
        } else skippedFrames.incrementAndGet()
        return drawFrame.get()?.takeIf {
            state == ScriptState.RUNNING && !cancellation.isCancelled && it.generation == drawGeneration.get() && it.input.sameCanvas(input) &&
                System.nanoTime() - it.builtAt <= TimeUnit.SECONDS.toNanos(1)
        }
    }
    fun clearDrawing() {
        drawGeneration.incrementAndGet(); drawFrame.set(null); renderInput.set(null); lastDrawnAt.set(0); lastRenderAt.set(0)
    }
    fun drawn(frame: ScriptDrawFrame) {
        if (state == ScriptState.RUNNING && frame.generation == drawGeneration.get() && !cancellation.isCancelled) {
            framesDrawn.incrementAndGet(); lastDrawnAt.set(System.nanoTime())
        }
    }
    fun renderFailed(failure: Throwable) {
        clearDrawing()
        enqueue { fail(IllegalStateException("NanoVG 绘制失败：" + failure.agentError(), failure)) }
    }
    fun renderStatus(): JsonObject {
        val frame = drawFrame.get()?.takeIf { it.generation == drawGeneration.get() }
        val drawn = lastDrawnAt.get()
        return jsonObject("subscribed" to (renderListeners > 0), "queued" to renderQueued.get(),
            "framesBuilt" to framesBuilt.get(), "framesDrawn" to framesDrawn.get(), "coalescedFrames" to skippedFrames.get(),
            "commands" to (frame?.commands?.size ?: 0), "width" to frame?.input?.width, "height" to frame?.input?.height,
            "lastFrameAgeMs" to frame?.let { TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - it.builtAt).coerceAtLeast(0)) },
            "recentlyDrawn" to (state == ScriptState.RUNNING && drawn > 0 && System.nanoTime() - drawn < TimeUnit.MILLISECONDS.toNanos(250)))
    }
    @Synchronized fun stop(): CompletableFuture<ScriptStatus> {
        stopFuture?.let { return it }
        if (state == ScriptState.STOPPED || state == ScriptState.FAILED) return CompletableFuture.completedFuture(status())
        val result = CompletableFuture<ScriptStatus>()
        stopFuture = result
        clearDrawing()
        state = ScriptState.STOPPING
        cancellation.cancel()
        threadRef.get()?.interrupt()
        host.release(id)
        submit {
            Thread.interrupted()
            try {
                // Stop handlers get a fresh, bounded cleanup budget. Host operations still use the cancelled run signal.
                factory.guarded(AgentCancellation()) { cx -> dispatch(cx, "stop", JsonObject()) }
            } catch (failure: Throwable) { writeLog("WARN", ScriptContextFactory.describe(failure)) }
            finally {
                handlers.clear(); renderListeners = 0; timers.clear(); scope = null
                state = ScriptState.STOPPED
                worker.shutdown()
            }
            writeLog("INFO", "脚本已停止")
            status()
        }.whenComplete { status, failure -> if (failure != null) result.completeExceptionally(failure) else result.complete(status) }
        return result
    }
    private fun fail(failure: Throwable) {
        if (cancellation.isCancelled) return
        error = ScriptContextFactory.describe(failure)
        clearDrawing()
        state = ScriptState.FAILED
        cancellation.cancel()
        handlers.clear(); renderListeners = 0; timers.clear(); scope = null
        host.release(id)
        writeLog("ERROR", error)
        worker.shutdown()
    }
    private fun enqueue(action: () -> Unit) {
        try { worker.execute(action) }
        catch (_: RejectedExecutionException) { dropped.incrementAndGet(); tickQueued.set(false) }
    }
    private fun <T> submit(action: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        try { worker.execute { try { result.complete(action()) } catch (failure: Throwable) { result.completeExceptionally(failure) } } }
        catch (failure: RejectedExecutionException) { result.completeExceptionally(failure) }
        return result
    }
    private fun dispatch(cx: Context, event: String, data: JsonObject) {
        val js = scope ?: return
        val value = fromJson(cx, js, data)
        handlers.values.filter { it.first == event }.toList().forEach { (_, callback) ->
            callback.call(cx, js, js, arrayOf(value))
        }
    }
    private fun fireTimers(cx: Context) {
        val js = scope ?: return
        val now = System.nanoTime()
        timers.toMap().forEach { (handle, timer) ->
            if (timer.due <= now && timers.containsKey(handle)) {
                if (timer.interval == 0L) timers.remove(handle) else timer.due = now + timer.interval
                timer.callback.call(cx, js, js, emptyArray())
            }
        }
    }
    private fun writeLog(level: String, message: String) = log(ScriptLog(id = id, level = level, message = message.take(4000)))

    private fun install(cx: Context, js: ScriptableObject) {
        val api = cx.newObject(js) as ScriptableObject
        fun group(name: String): ScriptableObject = (cx.newObject(js) as ScriptableObject).also {
            api.defineProperty(name, it, ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        }
        fun function(target: ScriptableObject, name: String, body: (Context, Array<out Any>) -> Any?) {
            val fn = object : BaseFunction(js, ScriptableObject.getFunctionPrototype(js)) {
                override fun call(context: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any? =
                    try { factory.checkBudget(); body(context, args).also { factory.checkBudget() } } catch (failure: Exception) {
                        throw Context.reportRuntimeError(failure.agentError())
                    }
            }
            target.defineProperty(name, fn, ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        }
        fun text(args: Array<out Any>, index: Int = 0) = Context.toString(args.getOrNull(index) ?: "")
        fun objectArg(context: Context, args: Array<out Any>, index: Int = 0): JsonObject =
            if (args.getOrNull(index) == null || args[index] is Undefined) JsonObject()
            else toJson(context, js, args[index]).asJsonObject
        fun invoke(context: Context, operation: String, args: JsonObject, capability: String? = null): Any? {
            cancellation.check()
            require(drawing == null) { "render2d 中不能调用客户端状态/操作；请在 tick 中读取并缓存，渲染只使用缓存" }
            if (capability != null) require(capability in record.manifest.capabilities) { "脚本需声明 $capability 权限" }
            return fromJson(context, js, host.call(id, operation, args, cancellation))
        }
        function(api, "on") { _, args ->
            val event = text(args)
            require(event in setOf("start", "stop", "tick", "world", "key", "chat", "packet", "render2d")) { "未知事件：$event" }
            require(handlers.size < 64) { "最多注册 64 个事件监听器" }
            val callback = args.getOrNull(1) as? Function ?: error("需要回调函数")
            nextHandle++.also { handlers[it] = event to callback; if (event == "render2d") renderListeners++ }
        }
        function(api, "off") { _, args ->
            if (handlers.remove(Context.toNumber(args[0]).toInt())?.first == "render2d") {
                renderListeners--; if (renderListeners == 0) clearDrawing()
            }
            Undefined.instance
        }
        val render = group("render")
        api.defineProperty("nvg", render, ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        fun drawArgs(context: Context, args: Array<out Any>): List<JsonElement> {
            require(args.size <= 8) { "绘制接口最多 8 个参数" }
            return args.map { when(it) { is Number -> JsonPrimitive(it); is CharSequence -> JsonPrimitive(it.toString()); else -> toJson(context, js, it) } }
        }
        ScriptDrawRecorder.operations.forEach { operation -> function(render, operation) { context, args ->
            val recorder = drawing ?: error("nocturne.render.$operation 只能在 render2d 回调中使用")
            recorder.record(operation, drawArgs(context, args)); Undefined.instance
        } }
        function(render, "rgba") { context, args -> fromJson(context, js, ScriptDrawRecorder.rgba(drawArgs(context, args))) }
        listOf("linearGradient", "radialGradient", "boxGradient").forEach { operation -> function(render, operation) { context, args ->
            fromJson(context, js, ScriptDrawRecorder.gradient(operation, drawArgs(context, args)))
        } }
        function(api, "log") { _, args -> writeLog("INFO", args.joinToString(" ") { Context.toString(it) }); Undefined.instance }
        function(api, "notify") { context, args -> invoke(context, "notify", jsonObject("text" to text(args))) }
        function(api, "setTimeout") { _, args -> timer(args, false) }
        function(api, "setInterval") { _, args -> timer(args, true) }
        function(api, "clearTimer") { _, args -> timers.remove(Context.toNumber(args[0]).toInt()); Undefined.instance }
        val player = group("player")
        function(player, "state") { context, _ -> invoke(context, "player_state", JsonObject()) }
        function(player, "inventory") { context, _ -> invoke(context, "inventory", JsonObject()) }
        val world = group("world")
        function(world, "entities") { context, args -> invoke(context, "nearby_entities", objectArg(context, args)) }
        val modules = group("modules")
        function(modules, "list") { context, _ -> invoke(context, "list_modules", JsonObject()) }
        function(modules, "get") { context, args -> invoke(context, "get_module", jsonObject("name" to text(args))) }
        function(modules, "self") { context, _ -> invoke(context, "own_module", JsonObject()) }
        function(modules, "listCustom") { context, _ -> invoke(context, "list_script_modules", JsonObject()) }
        function(modules, "create") { context, args -> invoke(context, "create_module", objectArg(context, args).apply { addProperty("script", id) }, "modules") }
        function(modules, "remove") { context, args -> invoke(context, "remove_module", jsonObject("id" to text(args)), "modules") }
        function(modules, "setKey") { context, args -> invoke(context, "set_keybind", jsonObject("name" to text(args), "key" to Context.toNumber(args.getOrNull(1)).toInt()), "modules") }
        function(modules, "setEnabled") { context, args -> invoke(context, "set_module", jsonObject("name" to text(args), "enabled" to Context.toBoolean(args.getOrNull(1))), "modules") }
        function(modules, "setSetting") { context, args -> invoke(context, "set_setting", jsonObject("module" to text(args), "setting" to text(args, 1), "value" to toJson(context, js, args.getOrNull(2))), "modules") }
        val chat = group("chat")
        function(chat, "print") { context, args -> invoke(context, "local_chat", jsonObject("text" to text(args))) }
        function(chat, "send") { context, args -> invoke(context, "send_chat", jsonObject("text" to text(args)), "chat") }
        val input = group("input")
        function(input, "hold") { context, args -> invoke(context, "hold_key", jsonObject("key" to text(args), "ticks" to Context.toNumber(args.getOrNull(1) ?: 1).toInt()), "controls") }
        function(input, "look") { context, args -> invoke(context, "look", jsonObject("yaw" to Context.toNumber(args.getOrNull(0)), "pitch" to Context.toNumber(args.getOrNull(1))), "controls") }
        function(input, "release") { context, _ -> invoke(context, "release_controls", JsonObject(), "controls") }
        val store = group("storage")
        function(store, "get") { context, args -> fromJson(context, js, storage.get(text(args)) ?: toJson(context, js, args.getOrNull(1))) }
        function(store, "set") { context, args ->
            require(drawing == null) { "请在 tick/key/stop 中保存存储，不在 render2d 中写磁盘" }
            val key = text(args); require(key.length in 1..100)
            val updated = storage.deepCopy().apply { add(key, toJson(context, js, args.getOrNull(1))) }
            repository.storage(id, updated); storage = updated; Undefined.instance
        }
        function(store, "remove") { _, args ->
            require(drawing == null) { "请在 tick/key/stop 中保存存储，不在 render2d 中写磁盘" }
            val updated = storage.deepCopy().apply { remove(text(args)) }
            repository.storage(id, updated); storage = updated; Undefined.instance
        }
        val ai = group("ai")
        function(ai, "ask") { _, args ->
            require(drawing == null) { "请在 key/tick 中请求 AI，不在 render2d 中发起请求" }
            require("ai" in record.manifest.capabilities) { "脚本需声明 ai 权限" }
            require("ai" in repository.grants(id)) { "ai 权限尚未授予" }
            cancellation.check()
            val prompt = text(args); require(prompt.length in 1..16000)
            val callback = args.getOrNull(1) as? Function ?: error("ai.ask 需要回调 (answer, error)")
            val now = System.currentTimeMillis()
            require(now - lastAiAt >= 5000 && pendingAi.get() < 2) { "AI 请求过于频繁" }
            lastAiAt = now; pendingAi.incrementAndGet()
            val future = CompletableFuture.supplyAsync({ host.ask(prompt, cancellation) }, aiExecutor)
            val hook = cancellation.onCancel { future.cancel(true) }
            future.whenComplete { answer, failure ->
                hook.close(); pendingAi.decrementAndGet()
                if (!cancellation.isCancelled) enqueue {
                    try { factory.guarded(cancellation) { context ->
                        callback.call(context, js, js, arrayOf(answer, failure?.cause?.agentError() ?: failure?.agentError()))
                    } } catch (error: Throwable) { fail(error) }
                }
            }
            Undefined.instance
        }
        js.defineProperty("nocturne", api, ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        val console = cx.newObject(js) as ScriptableObject
        for (level in listOf("log", "info", "warn", "error")) function(console, level) { _, args ->
            writeLog(if (level == "log") "INFO" else level.uppercase(), args.joinToString(" ") { Context.toString(it) }); Undefined.instance
        }
        js.defineProperty("console", console, ScriptableObject.READONLY or ScriptableObject.PERMANENT)
    }
    private fun timer(args: Array<out Any>, repeat: Boolean): Int {
        require(timers.size < 64) { "最多设置 64 个计时器" }
        val callback = args.getOrNull(0) as? Function ?: error("需要回调函数")
        val ms = Context.toNumber(args.getOrNull(1) ?: 50)
        require(ms.isFinite() && ms in 0.0..86400000.0) { "延时必须为 0 到 86400000 毫秒" }
        val delay = TimeUnit.MILLISECONDS.toNanos(ms.toLong().coerceAtLeast(50))
        return nextHandle++.also { timers[it] = Timer(callback, System.nanoTime() + delay, if (repeat) delay else 0L) }
    }
    private fun toJson(cx: Context, js: Scriptable, value: Any?): JsonElement {
        if (value == null || value is Undefined) return JsonNull.INSTANCE
        val encoded = NativeJSON.stringify(cx, js, value, null, null)
        if (encoded is Undefined) return JsonNull.INSTANCE
        val raw = Context.toString(encoded)
        require(raw.toByteArray(Charsets.UTF_8).size <= 64000) { "API 参数超过 64 KB" }
        return JsonParser.parseString(raw)
    }
    private fun fromJson(cx: Context, js: Scriptable, value: JsonElement): Any? = when {
        value.isJsonNull -> null
        value.isJsonArray -> cx.newArray(js, value.asJsonArray.map { fromJson(cx, js, it) }.toTypedArray())
        value.isJsonObject -> cx.newObject(js).also { obj -> value.asJsonObject.entrySet().forEach { (key, item) ->
            ScriptableObject.putProperty(obj, key, fromJson(cx, js, item))
        } }
        value.asJsonPrimitive.isBoolean -> value.asBoolean
        value.asJsonPrimitive.isNumber -> value.asDouble
        else -> value.asString
    }
}

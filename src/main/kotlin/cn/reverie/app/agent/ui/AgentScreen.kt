package cn.reverie.app.agent.ui

import cn.reverie.app.agent.*
import cn.reverie.app.agent.model.AgentModelCatalog
import cn.reverie.app.agent.script.ScriptState
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import org.lwjgl.nanovg.NanoVG.*
import cn.reverie.render.ui.NvgScreen
import cn.reverie.render.ui.UITheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.*
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW.*
import java.util.concurrent.TimeUnit

/** A single workspace: scripts and console, agent trajectory and tasks, provider settings. */
class AgentScreen(private val previous: Screen?, showAgent: Boolean = false,
                  private val dispatch: (() -> Unit) -> Unit = { Minecraft.getInstance().execute(it) }) : Screen(Component.literal("Nocturne AI Agent")), NvgScreen {
    private enum class Tab { SCRIPTS, AGENT, SETTINGS }
    private enum class Modal { NONE, NEW, DELETE, PERMISSIONS, HELP, HISTORY, DELETE_HISTORY, MODELS, MESSAGE, CLOSE, PLAN_WELCOME, SCRIPT_CONFLICT }
    private class Draft(var manifest: ScriptManifest, val editor: AgentTextArea, val sync: AgentDraftSync) {
        val record get() = ScriptRecord(manifest, editor.buffer.text)
        val savedSource get() = sync.saved?.source.orEmpty()
        val dirty get() = sync.dirty(record)
    }
    private data class Hit(val bounds: Rect2f, val enabled: Boolean, val key: String, val action: () -> Unit)
    private class ControlMotion {
        val hover = FloatAnimation(0f, 0f, 150L, CubicBezier.EASE_OUT)
        val active = FloatAnimation(0f, 0f, 210L, CubicBezier.EASE_OUT)
        val press = FloatAnimation(0f, 0f, 100L, CubicBezier.EASE_OUT)
        private var held = false
        private var pressUntil = 0L
        fun beginPress() {
            held = true; pressUntil = System.nanoTime() + 90_000_000L
            press.setImmediate(maxOf(press.value, 0.15f)); press.animateTo(1f)
        }
        fun endPress() { held = false }
        fun advancePress() {
            press.durationMs = (100L / AgentStyle.speed).toLong()
            press.animateTo(if (held || System.nanoTime() < pressUntil) 1f else 0f)
        }
    }
    private var theme = UITheme.DARK
    private var layout = AgentWorkspaceLayout(1280f, 720f)
    private var viewFrame = layout.frame(1f)
    private var inputClip = layout.bounds
    private val openAnim = FloatAnimation(0f, 0f, 320L, CubicBezier.EASE_OUT)
    private val tabAnim = FloatAnimation(1f, 1f, 180L, CubicBezier.EASE_OUT)
    private val modalAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
    private val tabIndicator = FloatAnimation(204f, 204f, 230L, CubicBezier.EASE_OUT)
    private val tabIndicatorWidth = FloatAnimation(78f, 78f, 230L, CubicBezier.EASE_OUT)
    private val motions = mutableMapOf<String, ControlMotion>()
    private val usedMotions = mutableSetOf<String>()
    private var pressedKey: String? = null
    private var closing = false
    private var lastFrameAt = 0L
    private var frameSeconds = 0f
    private var overlayKey = ""
    private var renderingOverlay = false
    private var previousFields = emptyList<AgentTextArea>()
    private var modalBounds = Rect2f.EMPTY
    private val hits = mutableListOf<Hit>()
    private val visibleFields = mutableListOf<AgentTextArea>()
    private val drafts = linkedMapOf<String, Draft>()
    private var tab = if (showAgent) Tab.AGENT else Tab.SCRIPTS
    private var modal = Modal.NONE
    private var scriptList = emptyList<ScriptManifest>()
    private var selected: String? = null
    private val listScroll = AgentScrollState()
    private val taskScroll = AgentScrollState()
    private var console = AgentTextArea(readOnly = true)
    private val transcript = AgentTranscript()
    private val prompt = AgentTextArea(maxLength = 16000).apply { placeholder = "描述任务，例如：写一个低血量提醒脚本，并检查它的日志" }
    private val modalText = AgentTextArea(readOnly = true)
    private val newId = AgentTextArea(multiline = false, maxLength = 64).apply { placeholder = "my-script" }
    private var attachScript = true
    private var status = ""
    private var statusError = false
    private var statusAt = 0L
    private var busy = false
    private var lastRefresh = 0L
    private var lastWorkspacePoll = 0L
    private var observedRevision = -1L
    private var syncingWorkspace = false
    private var refreshAgain = false
    private var conflictDiskView = false
    private var modalSignature = ""
    private var messageTitle = "消息原文"
    private var deleteHistoryId: String? = null
    private val modelSearch = AgentTextArea(multiline = false, maxLength = 200).apply { placeholder = "搜索模型名称或 ID" }
    private val modelScroll = AgentScrollState()
    private val settingsScroll = AgentScrollState()
    private var settingsHeight = 0f
    private var protocol = AgentManager.settings.settings.protocol
    private var authMode = AgentManager.settings.settings.authMode
    private var permissionMode = AgentManager.settings.settings.permissionMode
    private val form = linkedMapOf<String, AgentTextArea>()
    private var history = emptyList<AgentSessionInfo>()
    private val historyScroll = AgentScrollState()
    private var mx = 0f; private var my = 0f
    private val scripts get() = AgentManager.scripts
    private val engine get() = AgentManager.engine
    private val draft get() = selected?.let { drafts[it] }

    init {
        scriptList = scripts.repository.list()
        scriptList.firstOrNull()?.let { select(it.id) }
        observedRevision = scripts.repository.revision
        loadSettings()
    }
    override fun init() {
        super.init()
        if (!closing) {
            openAnim.durationMs = (320L / AgentStyle.speed).toLong()
            openAnim.setImmediate(0f); openAnim.animateTo(1f)
        }
        lastFrameAt = 0L
    }
    override fun isPauseScreen() = false
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}
    private fun refresh() {
        if (closing) return
        if (syncingWorkspace) { refreshAgain = true; return }
        syncingWorkspace = true
        lastWorkspacePoll = System.nanoTime()
        val cached = drafts.keys.toList()
        AgentManager.io.execute {
            val snapshot = runCatching { synchronized(scripts.repository) {
                Triple(scripts.repository.list(), cached.associateWith { runCatching { scripts.repository.readOrNull(it) } }, scripts.repository.revision)
            } }
            dispatch {
                syncingWorkspace = false
                if (closing) return@dispatch
                snapshot.onSuccess { (listed, records, revision) ->
                    // A newer save may complete before this read reaches the UI.
                    if (revision != scripts.repository.revision) { refreshAgain = true; return@onSuccess }
                    observedRevision = revision
                    records.forEach { (id, read) -> read.onSuccess { disk -> drafts[id]?.let { current ->
                        if (current.sync.observe(current.record, disk) == AgentDraftSync.Change.RELOAD && disk != null) {
                            val cursor = current.editor.buffer.cursor; val anchor = current.editor.buffer.anchor
                            current.manifest = disk.manifest; current.editor.setText(disk.source)
                            current.editor.buffer.move(anchor); current.editor.buffer.move(cursor, true)
                        }
                    } }.onFailure { setStatus("$id 同步失败，草稿已保留：" + it.agentError(), true) } }
                    scriptList = listed + drafts.values.filter { it.sync.deleted && listed.none { item -> item.id == it.manifest.id } }.map { it.manifest }
                    if (selected == null) scriptList.firstOrNull()?.let { select(it.id) }
                }.onFailure {
                    lastWorkspacePoll = System.nanoTime() + 1_700_000_000L
                    setStatus("工作区同步失败：" + it.agentError(), true)
                }
                if (refreshAgain) { refreshAgain = false; refresh() }
            }
        }
    }
    private fun select(id: String) {
        if (selected != id) { console.setText(""); lastRefresh = 0L }
        selected = id
        if (id !in drafts) {
            val record = runCatching { scripts.repository.read(id) }.getOrElse { setStatus(it.agentError(), true); return }
            drafts[id] = Draft(record.manifest, AgentTextArea(record.source, lineNumbers = true).apply { buffer.move(0) }, AgentDraftSync(record))
        }
    }
    private fun loadSettings() {
        val settings = AgentManager.settings.settings
        val values = linkedMapOf("endpoint" to settings.endpoint, "model" to settings.model,
            "key" to AgentManager.settings.keyForEditing(), "env" to settings.apiKeyEnvironment,
            "clientId" to settings.openAiClientId,
            "rounds" to settings.maxRounds.toString(), "output" to settings.maxOutputTokens.toString(),
            "budget" to settings.tokenBudget.toString(), "context" to settings.contextChars.toString(),
            "timeout" to settings.timeoutSeconds.toString(), "instructions" to settings.customInstructions)
        values.forEach { (key, value) -> form[key] = AgentTextArea(value, multiline = key == "instructions", maxLength = if (key == "instructions") 16000 else 16000).apply {
            password = key == "key"
            if (key == "model") placeholder = "填写服务支持的模型名称"
            if (key == "key") placeholder = "可留空使用环境变量或本地模型"
            if (key == "clientId") placeholder = "留空走官方动态注册；专用 Client ID 可在此填写"
        } }
        protocol = settings.protocol; permissionMode = settings.permissionMode; authMode = settings.authMode
    }
    override fun renderNvg(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (ResolutionHelper.scaledWidth <= 0f || ResolutionHelper.scaledHeight <= 0f) {
            clearEditorFocus(); hits.clear(); visibleFields.clear(); return
        }
        val now = System.nanoTime()
        val sincePoll = now - lastWorkspacePoll
        if (!syncingWorkspace && (observedRevision != scripts.repository.revision && sincePoll > 300_000_000L || sincePoll > 5_000_000_000L)) refresh()
        frameSeconds = if (lastFrameAt == 0L) 0f else ((now - lastFrameAt) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastFrameAt = now
        val progress = openAnim.value.coerceIn(0f, 1f)
        if (closing && progress < 0.015f) { finishClose(); return }
        layout = AgentWorkspaceLayout(ResolutionHelper.scaledWidth, ResolutionHelper.scaledHeight)
        viewFrame = layout.frame(progress)
        val pointer = viewFrame.pointer(mouseX, mouseY)
        mx = pointer.first; my = pointer.second
        theme = AgentStyle.theme()
        hits.clear(); visibleFields.clear(); usedMotions.clear()
        inputClip = layout.bounds
        val context = RenderEngine.nanoVG.handle
        Draw2D.rect(0f, 0f, ResolutionHelper.scaledWidth, ResolutionHelper.scaledHeight, NanoVGColor(0f, 0f, 0f, 0.35f * progress))
        nvgSave(context)
        try {
            nvgTranslate(context, viewFrame.x, viewFrame.y); nvgScale(context, viewFrame.scale, viewFrame.scale)
            if (HUDModule.widgetShadow.value) Draw2D.shadow(0f, 3f, layout.width, layout.height, 13f, 24f,
                NanoVGColor(0f, 0f, 0f, 0.47f * progress * HUDModule.shadowStrength.getFloat()))
            try {
                RenderEngine.backdropBlurRenderer.renderBackdrop(0f, 0f, layout.width, layout.height, radius = 13f,
                    tintColor = theme.background.withAlpha(HUDModule.bgOpacity.getFloat() * progress),
                    blurRadius = HUDModule.blurRadius.getFloat(), enableBlur = HUDModule.blur.value,
                    backdropOpacity = HUDModule.bgOpacity.getFloat() * progress)
            } catch (_: Throwable) {
                Draw2D.roundedRect(0f, 0f, layout.width, layout.height, 13f, theme.background.withAlpha(progress))
            }
            nvgGlobalAlpha(context, progress)
            Draw2D.roundedOutline(0f, 0f, layout.width, layout.height, 13f, theme.border.withAlpha(0.12f), 1f)
            renderHeader()
            withClip(layout.content) {
                nvgGlobalAlpha(context, progress * (0.55f + 0.45f * tabAnim.value))
                when (tab) {
                    Tab.SCRIPTS -> renderScripts(partialTicks)
                    Tab.AGENT -> renderAgent(partialTicks)
                    Tab.SETTINGS -> renderSettings(partialTicks)
                }
            }
            val footer = if (status.isNotBlank() && System.currentTimeMillis() - statusAt < 12000) status else
                if (tab == Tab.SCRIPTS) "Ctrl+S 保存 · Ctrl+Enter 保存并运行 · 关闭窗口后脚本继续运行" else "所有执行过程保存在本地 · 可随时停止任务"
            labelBox(footer, layout.footer, if (statusError && footer == status) theme.error else theme.textSecondary, 11f)
            val approval = engine.permissions.current()
            if (approval != null) renderApproval(approval, partialTicks)
            else {
                if (modal == Modal.NONE && !busy && AgentManager.openAi.needsWelcome()) showModal(Modal.PLAN_WELCOME)
                if (modal != Modal.NONE) renderModal(partialTicks) else overlayKey = ""
            }
        } finally { nvgRestore(context) }
        previousFields.filter { it !in visibleFields }.forEach { it.blur() }
        previousFields = visibleFields.toList()
        if (motions.size > 400) motions.keys.retainAll(usedMotions)
    }
    private fun renderHeader() {
        HudVectorIcons.drawMoon(20f, 22f, 16f, theme.textPrimary)
        label("NOCTURNE", 43f, 22f, theme.textPrimary, 14f)
        label("AI Agent", 43f, 44f, theme.textSecondary, 11f)
        val active = layout.tabs[tab.ordinal]
        tabIndicator.animateTo(active.x); tabIndicatorWidth.animateTo(active.width)
        Draw2D.roundedRect(tabIndicator.value, active.y, tabIndicatorWidth.value, active.height, AgentStyle.radius, theme.surfaceActive)
        layout.tabs.forEachIndexed { index, rect ->
            button(listOf("脚本", "Agent", "模型设置")[index], rect, active = index == tab.ordinal, key = "tab-" + index) {
                changeTab(Tab.entries[index])
            }
        }
        labelBox((if (busy) "处理中" else stateLabel(engine.state)), layout.status,
            if (engine.isRunning || busy) theme.primaryHover else theme.textSecondary, 12f)
        button("API 文档", layout.help, key = "help") {
            modalSignature = ""; showModal(Modal.HELP)
        }
        button("×", layout.close, key = "close") { onClose() }
        Draw2D.line(20f, 72f, layout.width - 20f, 72f, theme.border, 1f)
    }
    private fun changeTab(next: Tab) {
        if (next == tab) return
        clearEditorFocus(); tab = next
        hits.clear(); visibleFields.clear()
        tabAnim.setImmediate(0f); tabAnim.durationMs = (180L / AgentStyle.speed).toLong(); tabAnim.animateTo(1f)
    }
    private fun showModal(next: Modal) {
        clearEditorFocus(); hits.clear(); visibleFields.clear(); modal = next; overlayKey = ""
        if (next == Modal.NEW) newId.isFocused = true
    }
    private fun renderScripts(partialTicks: Float) {
        val toolbar = AgentWorkspaceLayout.row(layout.scriptToolbar, 3, 6f)
        button("+ 新建", toolbar[0], enabled = !busy, key = "script-new") { newId.setText(""); showModal(Modal.NEW) }
        button("刷新", toolbar[1], enabled = !busy, key = "script-refresh") { refresh(); setStatus("正在同步脚本和编辑器…") }
        button("目录", toolbar[2], key = "script-directory") {
            work("打开目录", { java.awt.Desktop.getDesktop().open(AgentManager.files.root.resolve("scripts").toFile()) })
        }
        val list = layout.scriptList
        listScroll.extent(scriptList.size * 58f - 8f, list.height); listScroll.advance(frameSeconds)
        withClip(list) {
            scriptList.forEachIndexed { index, manifest ->
                val row = Rect2f(list.x, list.y + index * 58f - listScroll.value, list.width - 6f, 50f)
                if (!row.intersects(list)) return@forEachIndexed
                val isSelected = selected == manifest.id
                val motion = control("script-row-" + manifest.id, row.contains(mx, my), isSelected)
                Draw2D.roundedRect(row, AgentStyle.radius, theme.surface.lerp(theme.surfaceActive, motion.active.value)
                    .lerp(theme.surfaceHover, motion.hover.value * 0.5f))
                Draw2D.roundedOutline(row, AgentStyle.radius, theme.border.lerp(theme.borderFocus, motion.active.value * 0.5f), 0.8f)
                labelBox(manifest.name, Rect2f(row.x + 10f, row.y + 9f, row.width - 20f, 18f),
                    if (isSelected) theme.textPrimary else theme.textSecondary, 13f)
                val state = scripts.status(manifest.id).state
                labelBox(manifest.id + ".js", Rect2f(row.x + 10f, row.y + 30f, row.width - 80f, 16f), theme.textSecondary, 10.5f)
                labelBox(scriptStateLabel(state), Rect2f(row.x2 - 60f, row.y + 30f, 50f, 16f),
                    if (state == ScriptState.RUNNING) theme.success else theme.textSecondary, 10.5f)
                hit(row, true, "script-row-" + manifest.id) { select(manifest.id) }
            }
            if (scriptList.isEmpty()) labelBox("没有脚本，点击新建开始", list.shrink(8f), theme.textSecondary, 12f)
        }
        scrollBar(list, scriptList.size * 58f - 8f, listScroll.value)
        val current = draft
        if (current == null) {
            labelBox("选中或新建脚本后开始编辑", layout.editor.shrink(16f), theme.textSecondary, 14f)
            return
        }
        val id = current.manifest.id
        val state = scripts.status(id)
        labelBox(id + ".js" + if (current.sync.conflict) "  · 版本冲突" else if (current.dirty) "  · 未保存" else "", layout.scriptTitle,
            if (current.sync.conflict) theme.warning else theme.textPrimary, 15f)
        val actions = layout.scriptActions
        button("保存", actions[0], enabled = !busy, key = "script-save") { saveSelected() }
        button("检查", actions[1], enabled = !busy, key = "script-check") {
            val source = current.editor.buffer.text
            work("语法检查", { scripts.validate(id, source)?.let { error(it) } })
        }
        button(if (state.state == ScriptState.RUNNING) "重载" else "运行", actions[2], active = true, enabled = !busy, key = "script-run") { saveSelected(run = true) }
        button("停止", actions[3], enabled = state.state in setOf(ScriptState.RUNNING, ScriptState.STARTING) && !busy, key = "script-stop") {
            work("停止脚本", { scripts.stop(id).get(5, TimeUnit.SECONDS) })
        }
        button("权限", actions[4], enabled = !busy, key = "script-grants") { showModal(Modal.PERMISSIONS) }
        button(if (current.sync.conflict) "冲突" else "重读", actions[5], active = current.sync.conflict,
            enabled = !busy && (!current.dirty || current.sync.conflict), key = "script-reload") {
            if (current.sync.conflict) showConflict() else { refresh(); setStatus("正在读取磁盘版本…") }
        }
        button("删除", actions[6], enabled = !busy, key = "script-delete") { showModal(Modal.DELETE) }
        labelBox(if (current.sync.conflict) "磁盘版本已更新，草稿已保留。点击“冲突”查看并选择版本。" else
            current.manifest.description.ifEmpty { "JavaScript 扩展 · 独立线程执行" }, layout.scriptDescription,
            if (current.sync.conflict) theme.warning else theme.textSecondary, 11f)
        button(if (current.manifest.autoStart) "✓ 启动时运行" else "启动时运行", layout.autoStart,
            active = current.manifest.autoStart, key = "script-auto") { current.manifest = current.manifest.copy(autoStart = !current.manifest.autoStart) }
        area(current.editor, layout.editor, partialTicks)
        val log = layout.logHeader
        label("运行日志", log.x, log.y + 4f, theme.textSecondary, 11f)
        labelBox(if (state.error.isNotEmpty()) state.error else
            "Ln " + (current.editor.buffer.line + 1) + ", Col " + (current.editor.buffer.column + 1),
            Rect2f(log.x + 80f, log.y + 4f, log.width - 148f, 18f),
            if (state.error.isNotEmpty()) theme.error else theme.textSecondary, 10.5f)
        button("清空", Rect2f(log.x2 - 56f, log.y - 3f, 56f, 26f), key = "script-clear-log") { scripts.clearLogs(); lastRefresh = 0L }
        if (System.currentTimeMillis() - lastRefresh > 400) {
            val value = scripts.logs(id).takeLast(80).joinToString("\n") { time(it.time) + " [" + it.level + "] " + it.message }.takeLast(64000)
            console.setText(wrap(value, layout.console.width - 34f), follow = true); lastRefresh = System.currentTimeMillis()
        }
        console.placeholder = "运行后在这里查看事件输出和错误"
        area(console, layout.console, partialTicks)
    }
    private fun renderAgent(partialTicks: Float) {
        labelBox(engine.session.title, layout.sessionTitle, theme.textPrimary, 16f)
        button("历史", layout.history, enabled = !busy && !engine.isRunning, key = "history") {
            var loaded = emptyList<AgentSessionInfo>()
            work("读取会话", { loaded = engine.sessions() }) { history = loaded; historyScroll.jump(0f); showModal(Modal.HISTORY) }
        }
        button("新会话", layout.newSession, enabled = !busy && !engine.isRunning, key = "new-session") {
            work("新建会话", { engine.newSession() }) { transcript.reset(); taskScroll.jump(0f) }
        }
        val tasks = engine.session.tasksSnapshot()
        labelBox("任务清单  ·  " + tasks.size, layout.taskHeading, theme.textSecondary, 11f)
        val list = layout.taskList
        taskScroll.extent(tasks.size * 46f, list.height); taskScroll.advance(frameSeconds)
        withClip(list) {
            if (tasks.isEmpty()) {
                labelBox("复杂任务会在这里分解步骤", Rect2f(list.x + 4f, list.y + 8f, list.width - 8f, 20f), theme.textSecondary, 11f)
            }
            tasks.forEachIndexed { index, task ->
                val y = list.y + index * 46f - taskScroll.value
                val row = Rect2f(list.x + 4f, y, list.width - 14f, 40f)
                if (!row.intersects(list)) return@forEachIndexed
                val symbol = when (task.status) { "completed" -> "✓"; "in_progress" -> "→"; else -> "○" }
                label(symbol, row.x, y + 2f, if (task.status == "completed") theme.success else theme.primaryHover, 12f)
                labelBox(task.title, Rect2f(row.x + 20f, y + 2f, row.width - 20f, 18f), theme.textPrimary, 11f)
                labelBox(when (task.status) { "completed" -> "已完成"; "in_progress" -> "进行中"; else -> "待开始" },
                    Rect2f(row.x + 20f, y + 23f, row.width - 20f, 16f), theme.textSecondary, 10f)
            }
        }
        scrollBar(list, tasks.size * 46f, taskScroll.value)
        label("第 " + engine.round + " 轮", layout.taskStats.x, layout.taskStats.y + 4f, theme.textSecondary, 11f)
        labelBox(engine.turnTokens.toString() + " Tokens", layout.taskStats.offset(0f, 25f).copy(height = 18f), theme.textSecondary, 11f)
        val entries = engine.session.entriesSnapshot()
        transcript.render(engine.session.id, entries, engine.live, engine.permissions.current()?.id,
            layout.transcript, mx, my, frameSeconds, theme,
            button = { text, bounds, key, action -> button(text, bounds, key = key, action = action) },
            clip = { bounds, draw -> withClip(bounds, draw) },
            copy = { Minecraft.getInstance().keyboardHandler.clipboard = it },
            link = { url -> work("打开链接", { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }) },
            linkArea = { bounds, key, action -> hit(bounds, !closing, key, action) },
            details = { title, value ->
                if (title == "任务示例") { prompt.setText(value); prompt.isFocused = true }
                else { messageTitle = title; modalText.setText(value); showModal(Modal.MESSAGE) }
            })
        area(prompt, layout.prompt, partialTicks)
        button(if (engine.isRunning) "停止" else "发送", layout.send, active = true,
            enabled = !busy && (engine.isRunning || prompt.buffer.text.isNotBlank()), key = "send") {
            if (engine.isRunning) engine.stop() else submit()
        }
        button("复制记录", layout.copy, key = "copy") {
            Minecraft.getInstance().keyboardHandler.clipboard = (entries.joinToString("\n\n") { "[" + it.kind + "] " + (it.tool?.let { name -> name + "\n" } ?: "") + it.text } + (engine.live?.let { "\n\n[streaming] " + it.text } ?: "")).takeLast(256000)
        }
        val attachmentLabel = when { draft == null -> "先选择一个脚本"; attachScript && draft?.dirty == true -> "✓ 附带未保存草稿"; attachScript -> "✓ 附带选中脚本"; else -> "附带选中脚本" }
        button(attachmentLabel, layout.attachment, active = attachScript && draft != null,
            enabled = !busy && !engine.isRunning && draft != null, key = "attachment") { attachScript = !attachScript }
        val usingPlan = AgentManager.settings.settings.authMode == AgentAuthMode.OPENAI_OAUTH
        val hint = if (usingPlan) "Ctrl+Enter · Using ChatGPT plan" else "Ctrl+Enter · " + AgentManager.settings.settings.protocol
        labelBox(hint, if (usingPlan) layout.composerHint else layout.composerHint.copy(width = layout.content.x2 - layout.composerHint.x), theme.textSecondary, 10.5f)
        if (usingPlan) button("Manage usage", layout.usage, key = "usage") { openUsage() }
    }
    private fun renderSettings(partialTicks: Float) {
        labelBox("模型连接", layout.settingsTitle, theme.textPrimary, 17f)
        button("保存设置", layout.settingsSave, active = true, enabled = !busy && !engine.isRunning, key = "settings-save") { saveSettings() }
        labelBox("两种连接独立保存；切换模式、保存连接和启动后自动更新模型列表", layout.settingsHint, theme.textSecondary, 11f)
        val viewport = layout.settingsViewport
        settingsScroll.extent(settingsHeight, viewport.height); settingsScroll.advance(frameSeconds)
        val left = viewport.x; val width = viewport.width - 12f
        var y = viewport.y + 6f - settingsScroll.value
        withClip(viewport) {
            val half = (width - 22f) / 2f
            fun field(key: String, caption: String, x: Float, fy: Float, w: Float, h: Float = 34f) {
                labelBox(caption, Rect2f(x, fy, w, 18f), theme.textSecondary, 11f)
                area(form.getValue(key), Rect2f(x, fy + 22f, w, h), partialTicks)
            }
            button(if (authMode == AgentAuthMode.OPENAI_OAUTH) "ChatGPT 订阅 · OAuth" else "API Key · 模型服务", left, y, half,
                enabled = !busy && !engine.isRunning, key = "auth-mode") {
                val updated = editedSettings(); val key = form.getValue("key").buffer.text
                val next = if (authMode == AgentAuthMode.API_KEY) AgentAuthMode.OPENAI_OAUTH else AgentAuthMode.API_KEY
                work("切换连接", { AgentManager.settings.save(updated, key); AgentManager.settings.switch(next) }) {
                    clearEditorFocus(); loadSettings(); settingsScroll.jump(0f)
                }
            }
            labelBox(if (authMode == AgentAuthMode.OPENAI_OAUTH) "通过官方授权页使用符合资格账户的订阅额度" else "使用服务商的 API Key 或本地模型",
                Rect2f(left + half + 22f, y + 8f, half, 18f), theme.textSecondary, 11f)
            y += 48f
            if (authMode == AgentAuthMode.OPENAI_OAUTH) {
                val connection = AgentManager.openAi
                val account = connection.activeAccount()
                button(if (connection.isLoggingIn) "重开登录页" else "Continue with ChatGPT", left, y, 194f,
                    enabled = !busy && !engine.isRunning, key = "oauth-login") {
                    if (connection.isLoggingIn) work("打开登录页", { connection.reopenLogin() }) {} else startOpenAiLogin(account?.id)
                }
                if (connection.isLoggingIn) button("取消登录", left + 204f, y, 96f, enabled = !busy, key = "oauth-cancel") { work("取消登录", { connection.cancelLogin() }) {} }
                else if (account != null) {
                    button(if (account.connected && !account.planEnabled) "授权订阅" else "添加账户", left + 204f, y, 96f,
                        enabled = !busy && !engine.isRunning, key = "oauth-add") {
                        if (account.connected && !account.planEnabled) startOpenAiLogin(account.id, true) else startOpenAiLogin()
                    }
                    button("退出账户", left + 310f, y, 96f, enabled = !busy && !engine.isRunning, key = "oauth-logout") { work("退出 ChatGPT", { connection.signOut() }) {} }
                }
                button("Manage usage", left + width - 144f, y, 144f, key = "oauth-usage") { openUsage() }
                y += 43f
                labelBox(connection.status, Rect2f(left, y, width, 18f), theme.textSecondary, 11f); y += 29f
                labelBox("ChatGPT 账户", Rect2f(left, y, half, 18f), theme.textSecondary, 11f)
                button(account?.label ?: "尚未登录", left, y + 22f, half,
                    enabled = !busy && !engine.isRunning && !connection.isLoggingIn && connection.accounts().size > 1, key = "oauth-account") {
                    val choices = connection.accounts()
                    val next = choices[(choices.indexOfFirst { it.id == connection.activeAccountId() } + 1) % choices.size]
                    work("切换 ChatGPT 账户", { connection.selectAccount(next.id); AgentManager.catalogs.refresh(AgentAuthMode.OPENAI_OAUTH) }) { syncOpenAiModel() }
                }
                labelBox("订阅模型", Rect2f(left + half + 22f, y, half, 18f), theme.textSecondary, 11f)
                val available = modelChoices()
                button(available.firstOrNull { it.id == form.getValue("model").buffer.text }?.label ?: "刷新并选择模型",
                    left + half + 22f, y + 22f, half - 106f, enabled = !busy && !engine.isRunning && available.isNotEmpty(), key = "oauth-model") {
                    showModelPicker()
                }
                button("刷新模型", left + width - 96f, y + 22f, 96f,
                    enabled = !busy && !engine.isRunning && !connection.isLoggingIn && account?.connected == true, key = "oauth-refresh") {
                    work("加载订阅模型", { AgentManager.catalogs.refresh(AgentAuthMode.OPENAI_OAUTH) }) { syncOpenAiModel() }
                }
                y += 64f
                labelBox(AgentManager.catalogs.state(authMode).status, Rect2f(left, y, width, 18f), theme.textSecondary, 10.5f); y += 32f
                field("clientId", "OAuth Client ID（选填，首次登录可动态注册）", left, y, width); y += 76f
                labelBox("单轮输出长度由订阅服务决定；任务仍受轮数和总 Token 预算限制", Rect2f(left, y, width, 18f), theme.textSecondary, 11f); y += 32f
                syncOpenAiModel()
            } else {
                field("endpoint", "服务地址（如 https://api.openai.com/v1）", left, y, width); y += 76f
                field("model", "模型名称", left, y, half)
                labelBox("接口协议", Rect2f(left + half + 22f, y, half, 18f), theme.textSecondary, 11f)
                button(protocolLabel(protocol), left + half + 22f, y + 22f, half, key = "protocol") {
                    protocol = AgentProtocol.entries[(protocol.ordinal + 1) % AgentProtocol.entries.size]
                }; y += 64f
                val catalog = AgentManager.catalogs.state(authMode)
                button(if (catalog.loading) "加载模型…" else "选择模型 · " + catalog.models.size, left, y, 170f,
                    enabled = !busy && !engine.isRunning && catalog.models.isNotEmpty(), key = "api-models") { showModelPicker() }
                button("刷新列表", left + 180f, y, 94f, enabled = !busy && !engine.isRunning && !catalog.loading, key = "api-refresh") {
                    val updated = editedSettings(); val key = form.getValue("key").buffer.text
                    work("加载模型", { AgentManager.settings.save(updated, key); AgentManager.catalogs.refresh(AgentAuthMode.API_KEY) }) { syncOpenAiModel() }
                }
                labelBox(catalog.status, Rect2f(left + 288f, y + 8f, width - 288f, 18f), theme.textSecondary, 10.5f); y += 44f
                syncOpenAiModel()
                field("key", "API Key（加密保存；环境变量优先）", left, y, half)
                field("env", "API Key 环境变量", left + half + 22f, y, half); y += 76f
            }
            labelBox("工具操作权限", Rect2f(left, y, half, 18f), theme.textSecondary, 11f)
            button(permissionLabel(permissionMode), left, y + 22f, half, key = "permission-mode") {
                permissionMode = AgentPermissionMode.entries[(permissionMode.ordinal + 1) % AgentPermissionMode.entries.size]
            }
            labelBox(when (permissionMode) { AgentPermissionMode.ASK -> "修改前显示具体参数，允许后执行"; AgentPermissionMode.READ_ONLY -> "仅查看状态、代码和日志"; AgentPermissionMode.AUTO -> "直接执行客户端操作；脚本能力仍由你授予" },
                Rect2f(left + half + 22f, y + 29f, half, 20f), theme.textSecondary, 11f)
            y += 76f
            val third = (width - 32f) / 3f
            field("rounds", "最大调用轮数（1–64）", left, y, third)
            if (authMode == AgentAuthMode.API_KEY) field("output", "API 单次输出 Tokens", left + third + 16f, y, third)
            else {
                labelBox("单次输出", Rect2f(left + third + 16f, y, third, 18f), theme.textSecondary, 11f)
                labelBox("由订阅服务决定", Rect2f(left + third + 16f, y + 30f, third, 20f), theme.textDisabled, 12f)
            }
            field("budget", "每个任务 Token 预算", left + (third + 16f) * 2f, y, third); y += 76f
            field("context", "上下文字符上限", left, y, half)
            field("timeout", "请求超时（10–300 秒）", left + half + 22f, y, half); y += 76f
            field("instructions", "自定义指令", left, y, width, 106f); y += 144f
        }
        settingsHeight = y + settingsScroll.value - viewport.y + 8f
        settingsScroll.extent(settingsHeight, viewport.height)
        scrollBar(viewport, settingsHeight, settingsScroll.value)
    }
    private fun renderApproval(approval: AgentApproval, partialTicks: Float) {
        if (modalSignature != approval.id) { modalText.setText(approval.arguments); modalText.scrollLine = 0; modalSignature = approval.id }
        overlay("Agent 请求执行操作", 650f, 440f, "approval-" + approval.id) { dialog ->
            val body = dialog.body
            labelBox(approval.tool, body.copy(height = 24f), theme.primaryHover, 16f)
            labelBox("查看具体参数，允许后才执行；拒绝会作为结果返回给模型",
                body.offset(0f, 30f).copy(height = 20f), theme.textSecondary, 11f)
            area(modalText, Rect2f(body.x, body.y + 58f, body.width, body.height - 58f), partialTicks)
            val buttons = dialogButtons(dialog, 2)
            button("允许本次", buttons[0], active = true, key = "approve") { engine.permissions.answer(approval.id, true); clearOverlayInput() }
            button("拒绝", buttons[1], key = "reject") { engine.permissions.answer(approval.id, false); clearOverlayInput() }
        }
    }
    private fun renderModal(partialTicks: Float) {
        when (modal) {
            Modal.NEW -> overlay("新建 JavaScript 脚本", 520f, 264f) { dialog ->
                labelBox("脚本 id（字母、数字、下划线、短横线）", dialog.body.copy(height = 20f), theme.textSecondary, 11f)
                area(newId, dialog.body.offset(0f, 30f).copy(height = 36f), partialTicks)
                val buttons = dialogButtons(dialog, 2)
                button("创建", buttons[0], active = true, enabled = !busy, key = "create") {
                    val id = newId.buffer.text.trim()
                    work("创建脚本", {
                        AgentFiles.requireId(id)
                        require(scripts.repository.files.read("scripts/" + id + ".js") == null) { "同名脚本已存在" }
                        scripts.repository.save(ScriptRecord(ScriptManifest(id), "nocturne.on(\"start\", function () {\n    console.log(\"Script started\");\n});\n"))
                    }) { showModal(Modal.NONE); refresh(); select(id) }
                }
                button("取消", buttons[1], key = "modal-cancel") { showModal(Modal.NONE) }
            }
            Modal.DELETE -> overlay("删除脚本", 520f, 236f) { dialog ->
                labelBox("将 " + selected.orEmpty() + ".js 移入本地 trash 并停止运行",
                    dialog.body.copy(height = 26f), theme.textSecondary, 12f)
                val buttons = dialogButtons(dialog, 2, 138f)
                button("移入回收目录", buttons[0], enabled = !busy, key = "confirm-delete") {
                    val id = selected ?: return@button
                    work("删除脚本", { scripts.stop(id).get(5, TimeUnit.SECONDS); scripts.repository.trash(id); scripts.repository.setGrants(id, emptySet()) }) {
                        drafts.remove(id); selected = null; showModal(Modal.NONE); refresh()
                    }
                }
                button("取消", buttons[1], key = "modal-cancel") { showModal(Modal.NONE) }
            }
            Modal.PERMISSIONS -> overlay("脚本能力与授权", 620f, 430f) { dialog ->
                val current = draft ?: return@overlay
                val grants = scripts.repository.grants(current.manifest.id)
                val body = dialog.body
                labelBox("声明随脚本保存；授权只由你操作，Agent 无权更改",
                    body.copy(height = 20f), theme.textSecondary, 11f)
                ScriptRepository.SCRIPT_CAPABILITIES.forEachIndexed { index, capability ->
                    val row = Rect2f(body.x, body.y + 38f + index * 54f, body.width, 40f)
                    labelBox(when (capability) { "modules" -> "modules · 修改模块和设置"; "chat" -> "chat · 发送消息和命令"; "controls" -> "controls · 玩家输入与朝向"; else -> "ai · 使用模型（计入服务用量）" },
                        Rect2f(row.x, row.y + 9f, row.width - 202f, 20f), theme.textPrimary, 11f)
                    val declared = capability in current.manifest.capabilities
                    button(if (declared) "✓ 已声明" else "声明", Rect2f(row.x2 - 188f, row.y, 88f, 30f),
                        active = declared, key = "declare-" + capability) {
                        current.manifest = current.manifest.copy(capabilities = if (declared) current.manifest.capabilities - capability else current.manifest.capabilities + capability)
                    }
                    button(if (capability in grants) "撤销授权" else "授予", Rect2f(row.x2 - 88f, row.y, 88f, 30f),
                        enabled = !busy, key = "grant-" + capability) {
                        val id = current.manifest.id
                        work("更新脚本授权", {
                            scripts.stop(id).get(5, TimeUnit.SECONDS)
                            scripts.repository.setGrants(id, if (capability in grants) grants - capability else grants + capability)
                        })
                    }
                }
                val buttons = dialogButtons(dialog, 2)
                button("保存声明", buttons[0], active = true, enabled = !busy, key = "save-manifest") { saveSelected { showModal(Modal.NONE) } }
                button("返回", buttons[1], key = "modal-return") { showModal(Modal.NONE) }
            }
            Modal.HELP -> overlay("JavaScript API", 820f, 540f) { dialog ->
                val signature = "api:" + dialog.body.width
                if (modalSignature != signature) {
                    modalText.setText(wrap(AgentTools.SCRIPT_API, dialog.body.width - 34f)); modalText.scrollLine = 0
                    modalSignature = signature
                }
                area(modalText, dialog.body, partialTicks)
                val buttons = dialogButtons(dialog, 2)
                button("复制文档", buttons[0], key = "copy-docs") { Minecraft.getInstance().keyboardHandler.clipboard = AgentTools.SCRIPT_API }
                button("返回", buttons[1], key = "modal-return") { showModal(Modal.NONE) }
            }
            Modal.HISTORY -> overlay("恢复会话", 640f, 500f) { dialog ->
                historyScroll.extent(history.size * 43f - 9f, dialog.body.height); historyScroll.advance(frameSeconds)
                withClip(dialog.body) {
                    history.forEachIndexed { index, info ->
                        val row = Rect2f(dialog.body.x, dialog.body.y + index * 43f - historyScroll.value, dialog.body.width - 75f, 34f)
                        if (!row.intersects(dialog.body)) return@forEachIndexed
                        val suffix = "  · " + date(info.createdAt)
                        val title = fit(info.title, row.width - RenderEngine.fontRenderer.getStringWidth(suffix, 12f) - 32f, 12f)
                        button(title + suffix, row, enabled = !busy && !engine.isRunning, key = "history-" + info.id) {
                            work("恢复会话", { engine.resume(info.id) }) { transcript.reset(); taskScroll.jump(0f); showModal(Modal.NONE) }
                        }
                        button("删除", Rect2f(row.x2 + 8f, row.y, 60f, row.height), enabled = !busy && !engine.isRunning, key = "delete-history-" + info.id) {
                            deleteHistoryId = info.id; showModal(Modal.DELETE_HISTORY)
                        }
                    }
                    if (history.isEmpty()) labelBox("还没有已保存的会话", dialog.body.copy(height = 26f), theme.textSecondary)
                }
                scrollBar(dialog.body, history.size * 43f - 9f, historyScroll.value)
                button("返回", dialogButtons(dialog, 1)[0], key = "modal-return") { showModal(Modal.NONE) }
            }
            Modal.DELETE_HISTORY -> overlay("删除历史对话", 520f, 244f) { dialog ->
                labelBox("删除此对话及其消息、任务记录；已创建的脚本保留。", dialog.body.copy(height = 24f), theme.textSecondary, 11f)
                labelBox(history.firstOrNull { it.id == deleteHistoryId }?.title ?: "当前对话", dialog.body.offset(0f, 32f).copy(height = 24f), theme.textPrimary, 13f)
                val buttons = dialogButtons(dialog, 2)
                button("删除对话", buttons[0], enabled = !busy && !engine.isRunning, key = "confirm-history-delete") {
                    val id = deleteHistoryId ?: return@button
                    work("删除对话", { engine.deleteSession(id); history = engine.sessions() }) { transcript.reset(); showModal(Modal.HISTORY) }
                }
                button("取消", buttons[1], key = "history-delete-cancel") { showModal(Modal.HISTORY) }
            }
            Modal.MODELS -> overlay("选择模型", 660f, 510f) { dialog ->
                area(modelSearch, dialog.body.copy(height = 34f), partialTicks)
                val list = dialog.body.offset(0f, 46f).copy(height = dialog.body.height - 46f)
                val choices = modelChoices().filter { it.id.contains(modelSearch.buffer.text, true) || it.label.contains(modelSearch.buffer.text, true) }
                modelScroll.extent(choices.size * 54f, list.height); modelScroll.advance(frameSeconds)
                withClip(list) {
                    choices.forEachIndexed { index, info ->
                        val row = Rect2f(list.x, list.y + index * 54f - modelScroll.value, list.width - 10f, 46f)
                        if (!row.intersects(list)) return@forEachIndexed
                        button(info.label + (if (info.id == form.getValue("model").buffer.text) "  ✓" else ""), row.copy(height = 27f),
                            active = info.id == form.getValue("model").buffer.text, enabled = !busy, key = "select-model-" + info.id) {
                            form.getValue("model").setText(info.id)
                            val updated = editedSettings(); val key = form.getValue("key").buffer.text
                            work("保存模型", { AgentManager.settings.save(updated, key) }) { showModal(Modal.NONE) }
                        }
                        labelBox(info.id, row.offset(8f, 30f).copy(width = row.width - 16f, height = 15f), theme.textSecondary, 10f)
                    }
                    if (choices.isEmpty()) labelBox("没有匹配的模型；API 模式支持手动填写名称", list.copy(height = 24f), theme.textSecondary, 11f)
                }
                scrollBar(list, choices.size * 54f, modelScroll.value)
                button("返回", dialogButtons(dialog, 1)[0], key = "model-return") { showModal(Modal.NONE) }
            }
            Modal.MESSAGE -> overlay(messageTitle, 820f, 540f) { dialog ->
                area(modalText, dialog.body, partialTicks)
                val buttons = dialogButtons(dialog, 2)
                button("复制原文", buttons[0], key = "copy-message-raw") { Minecraft.getInstance().keyboardHandler.clipboard = modalText.buffer.text }
                button("返回", buttons[1], key = "message-return") { showModal(Modal.NONE) }
            }
            Modal.CLOSE -> overlay("有未保存的脚本", 580f, 268f) { dialog ->
                labelBox("选择保存或放弃草稿；运行中的脚本和 Agent 会继续",
                    dialog.body.copy(height = 26f), theme.textSecondary, 11f)
                val buttons = dialogButtons(dialog, 3, 140f)
                button("保存并退出", buttons[0], active = true, enabled = !busy, key = "save-close") {
                    val conflict = drafts.values.firstOrNull { it.sync.conflict }
                    if (conflict != null) { selected = conflict.manifest.id; changeTab(Tab.SCRIPTS); showConflict(); return@button }
                    val changed = drafts.values.filter { it.dirty }.map { Triple(it, it.record, it.sync.saved) }
                    work("保存脚本", { changed.forEach { (draft, record, base) -> saveDraft(draft, record, base) } }) { closeNow() }
                }
                button("放弃草稿", buttons[1], enabled = !busy, key = "discard-close") { closeNow() }
                button("返回编辑", buttons[2], key = "modal-return") { showModal(Modal.NONE) }
            }
            Modal.PLAN_WELCOME -> overlay("正在使用你的 ChatGPT 订阅", 620f, 280f) { dialog ->
                labelBox("符合资格的 AI 请求会计入你的 ChatGPT 计划用量。",
                    dialog.body.copy(height = 26f), theme.textPrimary, 12f)
                labelBox("可在 ChatGPT 设置中管理此应用的额度与连接权限。",
                    dialog.body.offset(0f, 36f).copy(height = 26f), theme.textSecondary, 11f)
                val buttons = dialogButtons(dialog, 2, 152f)
                button("Manage usage", buttons[0], key = "welcome-usage") { openUsage() }
                button("知道了", buttons[1], active = true, enabled = !busy, key = "welcome-done") {
                    work("保存订阅提示", { AgentManager.openAi.acknowledgeWelcome() }) { showModal(Modal.NONE) }
                }
            }
            Modal.SCRIPT_CONFLICT -> overlay("脚本版本冲突 · " + selected.orEmpty(), 780f, 540f) { dialog ->
                val current = draft ?: return@overlay
                val body = dialog.body
                labelBox(if (current.sync.deleted) "磁盘文件已删除。保留草稿后可重新保存；原草稿会先备份。" else
                    "选择前自动备份。保留草稿后，下次保存将覆盖当前磁盘版本。", body.copy(height = 22f), theme.warning, 11f)
                val tabs = AgentWorkspaceLayout.row(body.offset(0f, 32f).copy(height = 30f), 2, 10f)
                button("未保存草稿", tabs[0], active = !conflictDiskView, key = "conflict-view-draft") { conflictDiskView = false; modalSignature = "" }
                button("磁盘 / AI 版本", tabs[1], active = conflictDiskView, key = "conflict-view-disk") { conflictDiskView = true; modalSignature = "" }
                val record = if (conflictDiskView) current.sync.incoming else current.record
                val text = record?.let { "名称：${it.manifest.name}\n说明：${it.manifest.description}\n权限：${it.manifest.capabilities.joinToString()}\n启动时运行：${it.manifest.autoStart}\n\n${it.source}" } ?: "磁盘文件已删除"
                if (modalText.buffer.text != text) modalText.setText(text)
                area(modalText, body.offset(0f, 74f).copy(height = body.height - 74f), partialTicks)
                val buttons = dialogButtons(dialog, 3, 126f)
                button("采用磁盘版本", buttons[0], active = true, enabled = !busy && current.sync.incoming != null, key = "conflict-use-disk") { resolveConflict(useDisk = true) }
                button("保留草稿", buttons[1], enabled = !busy && current.sync.conflict, key = "conflict-keep-draft") { resolveConflict(useDisk = false) }
                button("返回", buttons[2], key = "conflict-return") { showModal(Modal.NONE) }
            }
            Modal.NONE -> Unit
        }
    }
    private fun dialogButtons(dialog: AgentDialogLayout, count: Int, preferredWidth: Float = 112f): List<Rect2f> {
        val width = minOf(dialog.actions.width, preferredWidth * count + (count - 1) * 12f)
        return AgentWorkspaceLayout.row(Rect2f(dialog.actions.x2 - width, dialog.actions.y, width, dialog.actions.height), count, 12f)
    }
    private fun overlay(title: String, width: Float, height: Float, key: String = modal.name, content: (AgentDialogLayout) -> Unit) {
        hits.clear(); visibleFields.clear()
        if (overlayKey != key) {
            clearEditorFocus(); overlayKey = key
            if (modal == Modal.NEW) newId.isFocused = true
            modalAnim.setImmediate(0f); modalAnim.durationMs = (180L / AgentStyle.speed).toLong(); modalAnim.animateTo(1f)
        }
        val progress = modalAnim.value.coerceIn(0f, 1f)
        Draw2D.roundedRect(layout.bounds, 13f, NanoVGColor(0f, 0f, 0f, 0.55f))
        val base = layout.dialog(width, height)
        val dialog = AgentDialogLayout(base.bounds.offset(0f, (1f - progress) * 8f))
        modalBounds = dialog.bounds
        val context = RenderEngine.nanoVG.handle
        nvgSave(context)
        try {
            nvgGlobalAlpha(context, openAnim.value.coerceIn(0f, 1f) * progress)
            Draw2D.shadow(dialog.bounds, 12f, 24f, NanoVGColor(0f, 0f, 0f, 0.45f))
            Draw2D.roundedRect(dialog.bounds, 12f, theme.background)
            Draw2D.roundedOutline(dialog.bounds, 12f, theme.border.withAlpha(0.16f), 1f)
            labelBox(title, dialog.title, theme.textPrimary, 18f)
            renderingOverlay = true
            withClip(dialog.bounds.shrink(2f)) { content(dialog) }
            if (statusError && System.currentTimeMillis() - statusAt < 12000) labelBox(status, dialog.error, theme.error, 10.5f)
        } finally { renderingOverlay = false; nvgRestore(context) }
    }
    private fun clearOverlayInput() { clearEditorFocus(); hits.clear(); visibleFields.clear(); overlayKey = "" }
    private fun clearEditorFocus() {
        previousFields.forEach { it.blur() }; visibleFields.forEach { it.blur() }
        pressedKey?.let { motions[it]?.endPress() }; pressedKey = null
    }
    private inline fun withClip(bounds: Rect2f, draw: () -> Unit) {
        val previous = inputClip
        inputClip = previous.intersect(bounds)
        if (inputClip.isEmpty) { inputClip = previous; return }
        val context = RenderEngine.nanoVG.handle
        nvgSave(context); nvgIntersectScissor(context, bounds.x, bounds.y, bounds.width, bounds.height)
        try { draw() } finally { nvgRestore(context); inputClip = previous }
    }
    private fun label(text: String, x: Float, y: Float, color: NanoVGColor = theme.textPrimary, size: Float = 13f) =
        RenderEngine.fontRenderer.drawString(text, x, y, color, size)
    private fun labelBox(text: String, bounds: Rect2f, color: NanoVGColor = theme.textPrimary, size: Float = 13f) {
        if (bounds.isEmpty || !bounds.intersects(inputClip)) return
        withClip(bounds) { label(fit(text.replace('\n', ' '), bounds.width, size), bounds.x, bounds.y, color, size) }
    }
    private fun control(key: String, hovered: Boolean, active: Boolean): ControlMotion {
        usedMotions += key
        return motions.getOrPut(key) { ControlMotion() }.apply {
            hover.durationMs = (150L / AgentStyle.speed).toLong()
            this.active.durationMs = (210L / AgentStyle.speed).toLong()
            hover.animateTo(if (hovered && inputClip.contains(mx, my) && (overlayKey.isEmpty() || renderingOverlay)) 1f else 0f)
            this.active.animateTo(if (active) 1f else 0f)
            advancePress()
        }
    }
    private fun button(text: String, bounds: Rect2f, active: Boolean = false, enabled: Boolean = true,
                       key: String = text + ":" + bounds.x.toInt(), action: () -> Unit) {
        if (bounds.isEmpty || !bounds.intersects(inputClip)) return
        val interactive = enabled && !closing
        val motion = control(key, interactive && bounds.contains(mx, my), active && enabled)
        // Compression stays inside its hit rectangle; animation cannot cover adjacent controls.
        val inset = motion.press.value * 1.2f
        val shown = bounds.shrink(inset)
        val hover = motion.hover.value
        val selected = motion.active.value
        val color = theme.surface.lerp(theme.surfaceHover, hover)
            .lerp(theme.primary.withAlpha(0.14f + hover * 0.05f), selected)
        Draw2D.roundedRect(shown, AgentStyle.radius, color)
        Draw2D.roundedOutline(shown, AgentStyle.radius, theme.border.lerp(theme.borderFocus, selected * 0.5f + hover * 0.15f), 0.8f)
        val font = RenderEngine.fontRenderer
        val value = fit(text, shown.width - 16f, 12f)
        font.drawString(value, shown.x + (shown.width - font.getStringWidth(value, 12f)) * 0.5f,
            shown.centerY - 7f, if (!enabled) theme.textDisabled else theme.textPrimary.lerp(theme.primaryHover, selected), 12f)
        hit(bounds, interactive, key, action)
    }
    private fun button(text: String, x: Float, y: Float, w: Float, active: Boolean = false, enabled: Boolean = true,
                       key: String = text + ":" + x.toInt(), action: () -> Unit) =
        button(text, Rect2f(x, y, w, 30f), active, enabled, key, action)
    private fun hit(bounds: Rect2f, enabled: Boolean, key: String, action: () -> Unit) {
        val clipped = bounds.intersect(inputClip)
        if (!clipped.isEmpty) hits += Hit(clipped, enabled, key, action)
    }
    private fun area(field: AgentTextArea, bounds: Rect2f, partialTicks: Float) {
        field.x = bounds.x; field.y = bounds.y; field.width = bounds.width; field.height = bounds.height
        field.inputClip = bounds.intersect(inputClip)
        if (field.inputClip.isEmpty) return
        visibleFields += field; field.render(mx, my, partialTicks)
    }
    private fun scrollBar(viewport: Rect2f, contentHeight: Float, position: Float) {
        if (contentHeight <= viewport.height || viewport.height <= 0f) return
        val height = (viewport.height * viewport.height / contentHeight).coerceIn(minOf(18f, viewport.height), viewport.height)
        val y = viewport.y + (viewport.height - height) * position.coerceIn(0f, contentHeight - viewport.height) / (contentHeight - viewport.height)
        Draw2D.roundedRect(viewport.x2 - 3f, y, 2f, height, 1f, theme.textSecondary.withAlpha(0.35f))
    }
    private fun fit(text: String, width: Float, size: Float = 13f): String =
        AgentTextLayout.fit(text, width) { RenderEngine.fontRenderer.getStringWidth(it, size) }
    private fun wrap(text: String, width: Float): String {
        val font = RenderEngine.fontRenderer
        val widths = mutableMapOf<Int, Float>()
        return buildString {
            var used = 0f
            text.codePoints().forEach { code ->
                if (code == 10) { append('\n'); used = 0f } else {
                    val char = String(Character.toChars(code))
                    val measured = widths.getOrPut(code) { font.getStringWidth(char, 13f) }
                    if (used + measured > width && used > 0f) { append('\n'); used = 0f }
                    append(char); used += measured
                }
            }
        }
    }
    private fun saveDraft(current: Draft, record: ScriptRecord, base: ScriptRecord?) {
        synchronized(scripts.repository) {
            val disk = scripts.repository.readOrNull(record.manifest.id)
            require(disk == base) { "${record.manifest.id} 已被外部或 Agent 修改；请点击冲突选择版本" }
            scripts.validate(record.manifest.id, record.source)?.let { error(it) }
            scripts.repository.save(record)
            dispatch { current.sync.markSaved(record); refresh() }
        }
    }
    private fun showConflict() { conflictDiskView = false; modalSignature = ""; showModal(Modal.SCRIPT_CONFLICT) }
    private fun resolveConflict(useDisk: Boolean) {
        val current = draft ?: return
        val local = current.record; val incoming = current.sync.incoming
        val wasDeleted = current.sync.deleted
        work("解决版本冲突", {
            synchronized(scripts.repository) {
                require(scripts.repository.readOrNull(local.manifest.id) == incoming && (incoming != null || wasDeleted)) { "磁盘版本再次变化，请刷新后重新选择" }
                scripts.repository.backupDraft(local, "conflict-local")
                if (incoming != null) scripts.repository.backupDraft(incoming, "conflict-disk")
            }
        }) {
            // The choice refers to the displayed version; a newer read must be reviewed again.
            if (current.sync.incoming != incoming || current.sync.deleted != wasDeleted) { setStatus("磁盘版本再次变化，双方已备份；请重新选择", true); return@work }
            if (useDisk) {
                val record = current.sync.adoptDisk()
                current.manifest = record.manifest; current.editor.setText(record.source)
            } else current.sync.keepLocal()
            showModal(Modal.NONE); refresh(); setStatus("已选择版本；双方备份位于 draft-backups/${local.manifest.id}")
        }
    }
    private fun saveSelected(run: Boolean = false, done: () -> Unit = {}) {
        val current = draft ?: return
        if (current.sync.conflict) { showConflict(); return }
        val saved = current.editor.buffer.text; val manifest = current.manifest
        val record = ScriptRecord(manifest, saved); val base = current.sync.saved
        work(if (run) "保存并运行" else "保存脚本", {
            saveDraft(current, record, base)
            if (run) {
                scripts.stop(manifest.id).get(5, TimeUnit.SECONDS)
                val result = scripts.start(manifest.id).get(5, TimeUnit.SECONDS)
                require(result.state == ScriptState.RUNNING) { result.error }
            }
        }) { current.sync.markSaved(record); refresh(); done() }
    }
    private fun saveSettings() {
        val updated = editedSettings()
        val key = form.getValue("key").buffer.text
        work("保存模型设置", { AgentManager.settings.save(updated, key) }) { syncOpenAiModel() }
    }
    private fun editedSettings(): AgentSettings {
        fun number(key: String) = form.getValue(key).buffer.text.trim().toIntOrNull() ?: error("${key} 必须为整数")
        return AgentSettings(if (authMode == AgentAuthMode.OPENAI_OAUTH) "https://api.openai.com/v1" else form.getValue("endpoint").buffer.text.trim(),
            form.getValue("model").buffer.text.trim(), if (authMode == AgentAuthMode.OPENAI_OAUTH) AgentProtocol.RESPONSES else protocol, permissionMode,
            number("rounds"), number("output"), number("budget"), number("context"), number("timeout"), form.getValue("instructions").buffer.text,
            form.getValue("env").buffer.text.trim(), authMode, form.getValue("clientId").buffer.text.trim())
    }
    private fun startOpenAiLogin(accountId: String? = null, consent: Boolean = false) {
        val updated = editedSettings()
        val key = form.getValue("key").buffer.text
        work("连接 ChatGPT", {
            AgentManager.settings.save(updated, key)
            AgentManager.openAi.beginLogin(updated.openAiClientId, accountId, consent)
        }) {}
    }
    private fun modelChoices() = if (authMode == AgentAuthMode.OPENAI_OAUTH && AgentManager.openAi.models.isNotEmpty())
        AgentManager.openAi.models else AgentManager.catalogs.state(authMode).models
    private fun showModelPicker() { modelSearch.setText(""); modelScroll.jump(0f); showModal(Modal.MODELS) }
    private fun syncOpenAiModel() {
        val choices = modelChoices()
        val current = form.getValue("model").buffer.text
        if (choices.isNotEmpty() && (current.isBlank() || authMode == AgentAuthMode.OPENAI_OAUTH && choices.none { it.id == current }))
            form.getValue("model").setText(choices.firstOrNull { it.id == AgentManager.settings.profile(authMode).model }?.id
                ?: AgentModelCatalog.preferredModel(choices).id)
    }
    private fun openUsage() {
        work("打开订阅用量", { java.awt.Desktop.getDesktop().browse(java.net.URI("https://chatgpt.com/settings/usage")) }) {}
    }
    private fun submit() {
        val text = prompt.buffer.text.trim()
        if (text.isEmpty()) return
        syncOpenAiModel()
        val options = editedSettings()
        if (options.model.isBlank()) {
            changeTab(Tab.SETTINGS); setStatus("请先选择模型；保存连接后会自动加载模型列表", true); return
        }
        if (options.authMode == AgentAuthMode.OPENAI_OAUTH &&
            AgentManager.openAi.activeAccount()?.let { it.connected && it.planEnabled } != true) {
            changeTab(Tab.SETTINGS); setStatus("请先登录 ChatGPT 并允许使用订阅额度", true); return
        }
        val attachment = draft?.takeIf { attachScript }?.let { current ->
            AgentScriptAttachment(current.manifest, current.editor.buffer.text, AgentTools.sourceHash(current.savedSource), current.dirty)
        }
        val send = {
            runCatching { engine.submit(text, attachment).whenComplete { _, _ -> dispatch { refresh() } }; prompt.setText("") }
                .onFailure { setStatus(it.agentError(), true) }
            Unit
        }
        val key = form.getValue("key").buffer.text
        if (options != AgentManager.settings.settings ||
            options.authMode == AgentAuthMode.API_KEY && key.trim() != AgentManager.settings.keyForEditing())
            work("保存模型配置", { AgentManager.settings.save(options, key) }, send)
        else send()
    }
    private fun work(message: String, action: () -> Unit, done: () -> Unit = {}) {
        if (busy) return
        busy = true; setStatus("$message…")
        AgentManager.io.execute {
            val outcome = runCatching(action)
            dispatch {
                busy = false
                outcome.onSuccess { setStatus("$message 完成"); done() }.onFailure { setStatus(it.agentError(), true); refresh() }
            }
        }
    }
    private fun setStatus(message: String, error: Boolean = false) { status = message; statusError = error; statusAt = System.currentTimeMillis() }
    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (closing) return true
        val (x, y) = viewFrame.pointer(event.x.toFloat(), event.y.toFloat())
        val button = hits.asReversed().firstOrNull { it.bounds.contains(x, y) }
        if (button != null) {
            if (button.enabled && event.button() == 0) {
                clearEditorFocus(); pressedKey = button.key
                motions[button.key]?.beginPress()
                runCatching(button.action).onFailure { setStatus(it.agentError(), true) }
            }
            return true
        }
        if (event.button() == 0 && modal == Modal.NONE && engine.permissions.current() == null && tab == Tab.AGENT && transcript.mouseDown(x, y)) return true
        if (event.button() == 0) visibleFields.forEach { it.mouseClicked(x, y, event.button()) }
        return true
    }
    override fun onMouseReleased(event: MouseButtonEvent): Boolean {
        val (x, y) = viewFrame.pointer(event.x.toFloat(), event.y.toFloat())
        transcript.mouseUp()
        visibleFields.forEach { it.mouseReleased(x, y, event.button()) }
        pressedKey?.let { motions[it]?.endPress() }; pressedKey = null
        return true
    }
    override fun onMouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (closing) return true
        val (x, y) = viewFrame.pointer(event.x.toFloat(), event.y.toFloat())
        if (modal == Modal.NONE && tab == Tab.AGENT) transcript.mouseDrag(y)
        visibleFields.forEach { it.mouseDragged(x, y, event.button(), dx.toFloat() / viewFrame.scale, dy.toFloat() / viewFrame.scale) }
        return true
    }
    override fun onMouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        if (closing) return true
        val (px, py) = viewFrame.pointer(x.toFloat(), y.toFloat())
        if (visibleFields.any { it.mouseScrolled(px, py, scrollX.toFloat(), scrollY.toFloat()) }) return true
        if (engine.permissions.current() != null) return true
        if (modal != Modal.NONE) {
            if (modal == Modal.HISTORY && AgentDialogLayout(modalBounds).body.contains(px, py)) historyScroll.scroll(-scrollY.toFloat() * 43f)
            if (modal == Modal.MODELS && AgentDialogLayout(modalBounds).body.contains(px, py)) modelScroll.scroll(-scrollY.toFloat() * 54f)
            return true
        }
        when (tab) {
            Tab.SCRIPTS -> if (layout.scriptList.contains(px, py)) listScroll.scroll(-scrollY.toFloat() * 46f)
            Tab.AGENT -> if (layout.taskList.contains(px, py)) taskScroll.scroll(-scrollY.toFloat() * 40f) else transcript.wheel(px, py, scrollY.toFloat())
            Tab.SETTINGS -> if (layout.settingsViewport.contains(px, py)) settingsScroll.scroll(-scrollY.toFloat() * 42f)
        }
        return true
    }
    override fun onKeyPressed(event: KeyEvent): Boolean {
        if (closing) return true
        val ctrl = event.modifiers and GLFW_MOD_CONTROL != 0 || event.modifiers and GLFW_MOD_SUPER != 0
        val shift = event.modifiers and GLFW_MOD_SHIFT != 0
        if (event.key == GLFW_KEY_ESCAPE) {
            val approval = engine.permissions.current()
            when {
                approval != null -> { engine.permissions.answer(approval.id, false); clearOverlayInput() }
                modal != Modal.NONE -> showModal(Modal.NONE)
                else -> onClose()
            }
            return true
        }
        if (modal == Modal.NONE && engine.permissions.current() == null && ctrl) {
            if (event.key == GLFW_KEY_S) {
                runCatching { if (tab == Tab.SCRIPTS) saveSelected() else if (tab == Tab.SETTINGS && !engine.isRunning) saveSettings() }
                    .onFailure { setStatus(it.agentError(), true) }
                return true
            }
            if (event.key == GLFW_KEY_ENTER || event.key == GLFW_KEY_KP_ENTER) {
                runCatching { if (tab == Tab.SCRIPTS) saveSelected(run = true) else if (tab == Tab.AGENT && !engine.isRunning && !busy) submit() }
                    .onFailure { setStatus(it.agentError(), true) }
                return true
            }
        }
        if (modal == Modal.NEW && engine.permissions.current() == null && event.key in setOf(GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER)) {
            hits.firstOrNull { it.key == "create" && it.enabled }?.let { runCatching(it.action).onFailure { setStatus(it.agentError(), true) } }
            return true
        }
        if (event.key == GLFW_KEY_TAB && (shift || !visibleFields.any { it.isFocused && it.multiline && !it.readOnly })) {
            val index = visibleFields.indexOfFirst { it.isFocused }
            if (visibleFields.isNotEmpty()) {
                clearEditorFocus()
                val next = if (index < 0) (if (shift) visibleFields.lastIndex else 0) else (index + if (shift) -1 else 1).mod(visibleFields.size)
                visibleFields[next].isFocused = true
                reveal(visibleFields[next])
            }
            return true
        }
        val field = visibleFields.firstOrNull { it.isFocused } ?: return false
        val handled = field.keyPressed(event.key, event.scancode, event.modifiers)
        if (handled) reveal(field)
        return handled
    }
    private fun reveal(field: AgentTextArea) {
        if (tab != Tab.SETTINGS || modal != Modal.NONE || engine.permissions.current() != null) return
        val viewport = layout.settingsViewport
        val delta = when {
            field.y - 22f < viewport.y -> field.y - 22f - viewport.y
            field.y + field.height + 6f > viewport.y2 -> field.y + field.height + 6f - viewport.y2
            else -> 0f
        }
        if (delta != 0f) settingsScroll.jump(settingsScroll.value + delta)
    }
    override fun onCharTyped(event: CharacterEvent): Boolean {
        if (closing) return false
        val field = visibleFields.firstOrNull { it.isFocused } ?: return false
        val handled = field.codepoint(event.codepoint)
        if (handled) reveal(field)
        return handled
    }
    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean = onMouseDragged(
        MouseButtonEvent(ResolutionHelper.toCanvasX(event.x), ResolutionHelper.toCanvasY(event.y), event.buttonInfo), ResolutionHelper.toCanvasDelta(dx), ResolutionHelper.toCanvasDelta(dy))
    override fun onClose() {
        if (closing) return
        if (busy) { setStatus("当前文件操作完成后即可关闭"); return }
        if (engine.permissions.current() != null) {
            engine.permissions.current()?.let { engine.permissions.answer(it.id, false) }
            clearOverlayInput()
        }
        if (drafts.values.any { it.dirty || it.sync.conflict }) showModal(Modal.CLOSE) else closeNow()
    }
    private fun closeNow() {
        if (closing) return
        clearEditorFocus(); hits.clear(); visibleFields.clear(); modal = Modal.NONE
        closing = true; openAnim.durationMs = (180L / AgentStyle.speed).toLong(); openAnim.animateTo(0f)
    }
    private fun finishClose() {
        Minecraft.getInstance().gui.setScreen(previous)
        cn.reverie.module.misc.AIAgentModule.onScreenClosed()
    }
    override fun removed() { clearEditorFocus(); cn.reverie.module.misc.AIAgentModule.onScreenClosed(); super.removed() }
    companion object {
        private fun time(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(8)
        private fun date(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        private fun protocolLabel(value: AgentProtocol) = when (value) { AgentProtocol.CHAT_COMPLETIONS -> "Chat Completions（兼容服务）"; AgentProtocol.RESPONSES -> "OpenAI Responses"; AgentProtocol.ANTHROPIC -> "Anthropic Messages" }
        private fun permissionLabel(value: AgentPermissionMode) = when (value) { AgentPermissionMode.ASK -> "每次修改前确认"; AgentPermissionMode.READ_ONLY -> "只读"; AgentPermissionMode.AUTO -> "自动执行" }
        private fun stateLabel(value: AgentState) = when (value) { AgentState.THINKING -> "思考中"; AgentState.EXECUTING -> "执行工具"; AgentState.WAITING_APPROVAL -> "等待确认"; AgentState.COMPLETED -> "已完成"; AgentState.CANCELLED -> "已停止"; AgentState.FAILED -> "执行出错"; AgentState.LIMIT_REACHED -> "达到上限"; else -> "就绪" }
        private fun scriptStateLabel(value: ScriptState) = when (value) { ScriptState.RUNNING -> "运行中"; ScriptState.STARTING -> "启动中"; ScriptState.STOPPING -> "停止中"; ScriptState.FAILED -> "错误"; else -> "已停止" }
    }
}

package cn.reverie.app.agent.ui

import cn.reverie.app.agent.*
import cn.reverie.app.agent.auth.*
import cn.reverie.app.agent.model.AgentModelCatalog
import cn.reverie.app.agent.script.*
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontManager
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import com.google.gson.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import net.minecraft.client.Minecraft
import net.minecraft.client.input.*
import cn.reverie.render.math.Rect2f
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Opt-in, hidden OpenGL preview of the production screen. Uses temporary data and no model network. */
internal object AgentUiPreview {
    @JvmStatic fun main(args: Array<String>) {
        val output = Path.of(args.firstOrNull() ?: "build/agent-ui-preview").toAbsolutePath()
        Files.createDirectories(output)
        GLFWErrorCallback.createPrint(System.err).set()
        check(glfwInit()) { "GLFW initialization failed" }
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_STENCIL_BITS, 8)
        val window = glfwCreateWindow(1280, 720, "Nocturne Agent offscreen preview", 0, 0)
        check(window != 0L)
        glfwMakeContextCurrent(window); GL.createCapabilities()
        // Screen's constructor reads Minecraft.font; no Minecraft lifecycle or user data is started.
        val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe
        Minecraft::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, unsafe.allocateInstance(Minecraft::class.java))
        val files = AgentFiles(Files.createTempDirectory("nocturne-ui-preview"))
        val repository = ScriptRepository(files)
        val host = object : ScriptHost {
            override fun call(owner: String, operation: String, arguments: JsonObject, cancellation: AgentCancellation): JsonElement = JsonNull.INSTANCE
            override fun ask(prompt: String, cancellation: AgentCancellation) = "Preview"
            override fun release(owner: String) {}
        }
        val scripts = ScriptService(repository, host)
        val options = AgentSettings(model = "preview-model")
        val settings = AgentSettingsStore(files).apply { save(options, "") }
        val engine = AgentEngine(AgentModel { _, _, _, _ -> AgentReply(AgentMessage("assistant", "Preview"), 0) },
            { options }, AgentTools(), AgentSessionStore(files))
        val oauth = OpenAiOAuth(files)
        val catalogs = AgentModelCatalog(files, settings, oauth)
        try {
            mapOf("files" to files, "settings" to settings, "scripts" to scripts, "engine" to engine, "openAi" to oauth, "catalogs" to catalogs).forEach { (name, value) ->
                AgentManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, value)
            }
            repeat(24) { index ->
                val id = if (index == 0) "long-script-id-" + "x".repeat(48) else "sample-" + index
                repository.save(ScriptRecord(ScriptManifest(id, name = "低血量提醒与自动状态检测脚本 " + index,
                    description = "包含很长的中文描述，检查标题和工具栏始终保留各自空间。".repeat(4)),
                    "nocturne.on(\"tick\", function (event) {\n    var player = nocturne.player.state();\n    if (player.health < 8) {\n        console.log(\"低血量提醒\", player.health);\n    }\n});\n".repeat(25)))
            }
            engine.session.title = "这是一个很长的会话标题，用来检查历史和新会话按钮不会盖住文字。".repeat(5)
            repeat(30) { engine.session.updateTask(0, "读取客户端状态并验证低血量脚本的执行结果 " + it,
                if (it < 3) "completed" else if (it == 3) "in_progress" else "pending") }
            engine.session.addEntry("user", "创建一个低血量提醒脚本，检查语法并解释权限。")
            engine.session.addEntry("tool", "{\"id\":\"health-alert\",\"offset\":0,\"limit\":8000}", "preview-read", "read_script")
            engine.session.addEntry("result", "已读取脚本；当前状态为停止，尚未执行。\n".repeat(35), "preview-read", "read_script")
            engine.session.addEntry("assistant", "## 低血量提醒已创建\n\n- **状态**：源码已保存，语法检查通过。\n- 通过 `nocturne.modules.self()` 读取可调设置。\n\n```javascript\nnocturne.on(\"tick\", function (event) {\n    if (event.tick % 20 !== 0) return;\n    var player = nocturne.player.state();\n    if (player.health < 8) nocturne.notify(\"生命值较低\");\n});\n```\n\n| 检查 | 结果 |\n| --- | --- |\n| 语法 | 通过 |\n| 运行 | 等待授权 |\n\n> 在 ClickGUI 的 Player 分类中开启模块即可运行。")
            check(RenderEngine.nanoVG.init())
            FontManager(RenderEngine.nanoVG.handle).use { fonts ->
                val font = fonts.loadFontFromFile("default", Path.of("src/main/resources/assets/reverie/fonts/misans.ttf").toFile()) ?: error("Font missing")
                RenderEngine::class.java.getDeclaredField("fontRenderer").apply { isAccessible = true }.set(null, FontRenderer(font))
                HUDModule.blur.value = false
                val uiQueue = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()
                val screen = AgentScreen(null, dispatch = { uiQueue.add(it) })
                fun pumpUi() { while (true) (uiQueue.poll() ?: break).invoke() }
                fun awaitUi() {
                    val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                    do {
                        pumpUi()
                        if (!field(screen, "syncingWorkspace", Boolean::class.javaObjectType) && !field(screen, "busy", Boolean::class.javaObjectType)) return
                        Thread.sleep(5)
                    } while (System.nanoTime() < until)
                    error("UI work did not complete")
                }
                fun refreshWorkspace() { AgentScreen::class.java.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(screen); awaitUi() }
                field(screen, "openAnim", FloatAnimation::class.java).setImmediate(1f)
                fun capture(name: String, width: Int = 1280, height: Int = 720) {
                    pumpUi()
                    glfwSetWindowSize(window, width, height); glfwPollEvents()
                    val fbWidth = IntArray(1); val fbHeight = IntArray(1)
                    glfwGetFramebufferSize(window, fbWidth, fbHeight)
                    glViewport(0, 0, fbWidth[0], fbHeight[0])
                    glClearColor(0.07f, 0.09f, 0.12f, 1f); glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
                    ResolutionHelper.update(width, height, fbWidth[0], fbHeight[0], (width * 0.75f).toInt(), (height * 0.75f).toInt(), 1)
                    val ctx = RenderEngine.nanoVG
                    ctx.beginFrame(ResolutionHelper.scaledWidth, ResolutionHelper.scaledHeight, ResolutionHelper.devicePixelRatio)
                    Draw2D.gradientLinear(0f, 0f, ResolutionHelper.scaledWidth, ResolutionHelper.scaledHeight, 0f,
                        NanoVGColor(0.08f, 0.14f, 0.20f, 1f), NanoVGColor(0.16f, 0.10f, 0.18f, 1f), horizontal = true)
                    screen.renderNvg(-100f, -100f, 0f)
                    ctx.endFrame()
                    Thread.sleep(240)
                    pumpUi()
                    glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
                    ctx.beginFrame(ResolutionHelper.scaledWidth, ResolutionHelper.scaledHeight, ResolutionHelper.devicePixelRatio)
                    Draw2D.gradientLinear(0f, 0f, ResolutionHelper.scaledWidth, ResolutionHelper.scaledHeight, 0f,
                        NanoVGColor(0.08f, 0.14f, 0.20f, 1f), NanoVGColor(0.16f, 0.10f, 0.18f, 1f), horizontal = true)
                    screen.renderNvg(-100f, -100f, 0f)
                    ctx.endFrame(); glFinish()
                    check(glGetError() == GL_NO_ERROR) { "OpenGL error while rendering " + name }
                    val controls = (field(screen, "hits", List::class.java).map { field(it!!, "bounds", Rect2f::class.java) } +
                        field(screen, "visibleFields", List::class.java).map { (it as AgentTextArea).inputClip })
                    controls.forEachIndexed { index, rect ->
                        check(!rect.isEmpty)
                        check(field(screen, "layout", AgentWorkspaceLayout::class.java).bounds.contains(rect))
                        controls.drop(index + 1).forEach { next -> check(!rect.intersects(next)) { "Overlapping active controls: " + name + " " + rect + " / " + next } }
                    }
                    val pixels = MemoryUtil.memAlloc(fbWidth[0] * fbHeight[0] * 4)
                    try {
                        glReadPixels(0, 0, fbWidth[0], fbHeight[0], GL_RGBA, GL_UNSIGNED_BYTE, pixels)
                        val image = BufferedImage(fbWidth[0], fbHeight[0], BufferedImage.TYPE_INT_ARGB)
                        for (y in 0 until fbHeight[0]) for (x in 0 until fbWidth[0]) {
                            val index = ((fbHeight[0] - y - 1) * fbWidth[0] + x) * 4
                            image.setRGB(x, y, (0xff shl 24) or ((pixels.get(index).toInt() and 255) shl 16) or
                                ((pixels.get(index + 1).toInt() and 255) shl 8) or (pixels.get(index + 2).toInt() and 255))
                        }
                        ImageIO.write(image, "png", output.resolve(name + ".png").toFile())
                    } finally { MemoryUtil.memFree(pixels) }
                    println("Rendered " + name + " (" + fbWidth[0] + "x" + fbHeight[0] + ")")
                }
                capture("scripts-wide"); capture("scripts-small", 800, 600); capture("scripts-portrait", 720, 960)
                val selectedId = field(screen, "selected", String::class.java)
                val cached = field(screen, "drafts", Map::class.java).entries.first { it.key == selectedId }.value!!
                val editor = field(cached, "editor", AgentTextArea::class.java)
                val sync = field(cached, "sync", AgentDraftSync::class.java)
                val original = repository.read(selectedId)
                val aiVersion = original.copy(source = "console.log('AI saved version');\n")
                repository.save(aiVersion); refreshWorkspace()
                check(editor.buffer.text == aiVersion.source && !sync.conflict)
                capture("scripts-ai-sync")
                editor.buffer.insert("// 用户仍在编辑的草稿\n")
                val localText = editor.buffer.text
                val nextDisk = aiVersion.copy(source = "console.log('AI changed again');\n")
                repository.save(nextDisk); refreshWorkspace()
                check(editor.buffer.text == localText && sync.incoming == nextDisk)
                capture("scripts-conflict", 800, 600)
                enum(screen, "modal", "SCRIPT_CONFLICT"); capture("modal-script-conflict", 800, 600)
                AgentScreen::class.java.getDeclaredField("conflictDiskView").apply { isAccessible = true }.setBoolean(screen, true)
                capture("modal-script-conflict-disk")
                AgentScreen::class.java.getDeclaredMethod("resolveConflict", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(screen, false)
                awaitUi()
                check(editor.buffer.text == localText && sync.saved == nextDisk && !sync.conflict)
                check(Files.list(files.path("draft-backups/$selectedId/.keep").parent).use { it.count() } == 2L)
                repository.trash(selectedId); refreshWorkspace()
                check(editor.buffer.text == localText && sync.deleted)
                capture("scripts-deleted-draft", 800, 600)
                enum(screen, "modal", "SCRIPT_CONFLICT"); capture("modal-script-deleted", 800, 600)
                AgentScreen::class.java.getDeclaredMethod("resolveConflict", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(screen, false)
                awaitUi(); check(sync.saved == null && !sync.conflict)
                AgentScreen::class.java.getDeclaredMethod("saveSelected", Boolean::class.javaPrimitiveType, Function0::class.java)
                    .apply { isAccessible = true }.invoke(screen, false, {})
                awaitUi(); check(repository.read(selectedId).source == localText && !sync.dirty(ScriptRecord(repository.read(selectedId).manifest, editor.buffer.text)))
                println("Agent edits sync automatically; conflict choices back up both versions; deleted drafts can be restored")
                val preserved = repository.read(selectedId)
                files.write("scripts/$selectedId.json", "broken manifest")
                val otherId = "sample-1"
                AgentScreen::class.java.getDeclaredMethod("select", String::class.java).apply { isAccessible = true }.invoke(screen, otherId)
                val other = repository.read(otherId).copy(source = "console.log('other scripts still sync');\n")
                repository.save(other); refreshWorkspace()
                val otherDraft = field(screen, "drafts", Map::class.java).entries.first { it.key == otherId }.value!!
                check(field(otherDraft, "editor", AgentTextArea::class.java).buffer.text == other.source)
                check(editor.buffer.text == localText)
                capture("scripts-partial-sync-error", 800, 600)
                repository.save(preserved); refreshWorkspace()
                AgentScreen::class.java.getDeclaredMethod("select", String::class.java).apply { isAccessible = true }.invoke(screen, selectedId)
                println("A broken script manifest preserves its draft without blocking other scripts from syncing")
                enum(screen, "tab", "AGENT"); capture("agent-wide"); capture("agent-small", 800, 600)
                val timeline = field(screen, "transcript", AgentTranscript::class.java)
                timeline.scroll.jump(0f); capture("agent-tool-cards")
                @Suppress("UNCHECKED_CAST")
                val expanded = field(timeline, "expanded", MutableSet::class.java) as MutableSet<String>
                expanded.add("preview-read"); capture("agent-tool-expanded", 800, 600)
                expanded.clear()
                AgentEngine::class.java.getDeclaredField("live").apply { isAccessible = true }.set(engine, AgentLiveResponse(
                    "### 正在创建脚本\n\n**下一步**：保存代码并运行语法检查。\n\n\u0060\u0060\u0060javascript\nnocturne.on('tick', function (event) {\n    var health = nocturne.player.state().health;\n",
                    listOf(AgentToolCall("live-create", "create_script", jsonObject("preview" to "{\"id\":\"health-alert\",\"source\":")))))
                timeline.scroll.jump(timeline.scroll.maximum); capture("agent-streaming")
                AgentEngine::class.java.getDeclaredField("live").apply { isAccessible = true }.set(engine, null)
                field(screen, "taskScroll", AgentScrollState::class.java).jump(800f)
                capture("agent-many-tasks")
                enum(screen, "tab", "SETTINGS"); capture("settings-api")
                field(screen, "settingsScroll", AgentScrollState::class.java).jump(500f); capture("settings-api-bottom")
                enum(screen, "authMode", "OPENAI_OAUTH"); field(screen, "settingsScroll", AgentScrollState::class.java).jump(0f)
                capture("settings-oauth")
                field(oauth, "credentials", OpenAiCredentialStore::class.java).transaction(AgentCancellation()) { data ->
                    val id = "preview-account"
                    data.accounts += OpenAiCredentials(id, "preview_client", "preview_subject", "a-very-long-account-label@example.test", "Preview", "preview_only", "", "",
                        setOf(OPENAI_PLAN_SCOPE), System.currentTimeMillis() / 1000 + 3600, 0)
                    data.activeId = id; data.planWelcomeSeen = true
                }
                OpenAiOAuth::class.java.getDeclaredField("models").apply { isAccessible = true }.set(oauth,
                    listOf(OpenAiModelInfo("preview-model", "包含较长名称的订阅模型 / Long model display name")))
                catalogs.acceptOAuth(oauth.models)
                capture("settings-oauth-connected")
                enum(screen, "tab", "SCRIPTS")
                for (modal in listOf("NEW", "DELETE", "PERMISSIONS", "HELP", "HISTORY", "DELETE_HISTORY", "MODELS", "MESSAGE", "CLOSE", "PLAN_WELCOME")) {
                    if (modal == "HELP") field(screen, "modalText", AgentTextArea::class.java).setText(AgentTools.SCRIPT_API)
                    if (modal == "HISTORY") AgentScreen::class.java.getDeclaredField("history").apply { isAccessible = true }.set(screen,
                        List(18) { AgentSessionInfo("session-" + it, "很长的历史会话标题，检查日期和操作区域 " + it, System.currentTimeMillis()) })
                    enum(screen, "modal", modal)
                    capture("modal-" + modal.lowercase(), 800, 600)
                }
                enum(screen, "modal", "NONE")
                val permissionSignal = AgentCancellation()
                val permission = CompletableFuture.supplyAsync {
                    engine.permissions.request(AgentToolCall("preview-call", "write_script", jsonObject("id" to "health-alert", "source" to "console.log('health');")), permissionSignal)
                }
                val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                while (engine.permissions.current() == null && System.nanoTime() < until) Thread.sleep(5)
                check(engine.permissions.current() != null)
                capture("modal-approval", 800, 600)
                screen.onKeyPressed(KeyEvent(GLFW_KEY_ESCAPE, 0, 0))
                check(!permission.get(2, TimeUnit.SECONDS)); check(engine.permissions.current() == null)
                // Exercise production input handlers against the last rendered transform.
                field(screen, "openAnim", FloatAnimation::class.java).setImmediate(0.45f)
                capture("animation-frame")
                fun click(bounds: Rect2f) {
                    val point = field(screen, "viewFrame", AgentViewFrame::class.java).point(bounds.centerX, bounds.centerY)
                    val event = MouseButtonEvent(point.first.toDouble(), point.second.toDouble(), MouseButtonInfo(0, 0))
                    screen.onMouseClicked(event, false); screen.onMouseReleased(event)
                }
                click(field(screen, "layout", AgentWorkspaceLayout::class.java).tabs[1])
                check(field(screen, "tab", Enum::class.java).name == "AGENT")
                capture("input-agent")
                click(field(screen, "layout", AgentWorkspaceLayout::class.java).prompt)
                check(screen.onCharTyped(CharacterEvent('测'.code)))
                check(screen.onCharTyped(CharacterEvent('试'.code)))
                check(field(screen, "prompt", AgentTextArea::class.java).buffer.text == "测试")
                click(field(screen, "layout", AgentWorkspaceLayout::class.java).help)
                capture("input-help")
                check(!screen.onCharTyped(CharacterEvent('错'.code)))
                check(field(screen, "prompt", AgentTextArea::class.java).buffer.text == "测试")
                screen.onKeyPressed(KeyEvent(GLFW_KEY_ESCAPE, 0, 0)); capture("input-return")
                click(field(screen, "layout", AgentWorkspaceLayout::class.java).tabs[2]); enum(screen, "authMode", "API_KEY")
                capture("input-settings")
                val form = field(screen, "form", Map::class.java)
                val endpoint = form["endpoint"] as AgentTextArea
                click(endpoint.bounds)
                check(endpoint.isFocused)
                screen.onKeyPressed(KeyEvent(GLFW_KEY_TAB, 0, 0)); check((form["model"] as AgentTextArea).isFocused)
                screen.onKeyPressed(KeyEvent(GLFW_KEY_TAB, 0, GLFW_MOD_SHIFT)); check(endpoint.isFocused)
                val position = field(screen, "viewFrame", AgentViewFrame::class.java).point(endpoint.bounds.centerX, endpoint.bounds.centerY)
                val scroll = field(screen, "settingsScroll", AgentScrollState::class.java)
                val before = scroll.target
                screen.onMouseScrolled(position.first.toDouble(), position.second.toDouble(), 0.0, -0.25)
                check(scroll.target > before)
                println("Animated pointer, prompt input, modal isolation, approval rejection, Tab/Shift+Tab and wheel propagation verified")
                // Missing configuration preserves the user's task and opens the actionable settings page.
                enum(screen, "tab", "AGENT")
                (form["model"] as AgentTextArea).setText("")
                field(screen, "prompt", AgentTextArea::class.java).setText("请帮我创建一个脚本")
                capture("send-readiness")
                click(field(screen, "layout", AgentWorkspaceLayout::class.java).send)
                check(field(screen, "tab", Enum::class.java).name == "SETTINGS")
                check(field(screen, "prompt", AgentTextArea::class.java).buffer.text == "请帮我创建一个脚本")
                check(!engine.isRunning)
                capture("send-model-required")
                println("Send readiness opens settings and preserves the pending prompt")
                (form["model"] as AgentTextArea).setText("preview-model")
                field(screen, "openAnim", FloatAnimation::class.java).setImmediate(1f)
                enum(screen, "tab", "AGENT")
                engine.session.title = "新会话"; engine.session.entries.clear(); engine.session.tasks.clear()
                field(screen, "prompt", AgentTextArea::class.java).setText("")
                capture("agent-preview")
                println("Production AgentScreen previews saved to " + output)
            }
        } finally {
            engine.close(); scripts.close(); catalogs.close(); oauth.close(); runCatching { RenderEngine.nanoVG.close() }
            glfwDestroyWindow(window); glfwTerminate()
        }
    }
    private fun <T> field(target: Any, name: String, type: Class<T>): T = type.cast(target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target))
    private fun enum(target: Any, name: String, value: String) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.let { field ->
            field.set(target, field.type.enumConstants.first { (it as Enum<*>).name == value })
        }
    }
}

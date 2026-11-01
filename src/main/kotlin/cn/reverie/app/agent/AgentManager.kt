package cn.reverie.app.agent

import cn.reverie.app.agent.model.HttpAgentModel
import cn.reverie.app.agent.model.AgentModelCatalog
import cn.reverie.app.agent.auth.OpenAiOAuth
import cn.reverie.app.agent.script.ScriptService
import cn.reverie.app.agent.script.ScriptModuleService
import cn.reverie.app.agent.script.ScriptRenderInput
import cn.reverie.app.agent.script.NanoVGScriptRenderer
import cn.reverie.app.agent.ui.AgentStyle
import cn.reverie.app.event.EventBus
import cn.reverie.app.event.EventSubscription
import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.app.event.render.ResolutionChangeEvent
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.app.agent.ui.AgentScreen
import net.minecraft.client.Minecraft
import java.util.concurrent.Executors

/** Composition root; lifecycle mixins depend on the existing managers, never this feature directly. */
object AgentManager {
    private var initialized = false
    private var packetSubscription: EventSubscription? = null
    private var renderSubscription: EventSubscription? = null
    private var resolutionSubscription: EventSubscription? = null
    private val scriptRenderer = NanoVGScriptRenderer()
    lateinit var files: AgentFiles
        private set
    lateinit var settings: AgentSettingsStore
        private set
    lateinit var scripts: ScriptService
        private set
    lateinit var scriptModules: ScriptModuleService
        private set
    lateinit var engine: AgentEngine
        private set
    lateinit var openAi: OpenAiOAuth
        private set
    lateinit var catalogs: AgentModelCatalog
        private set
    private lateinit var host: MinecraftScriptHost
    private var tick = 0L
    val io = Executors.newSingleThreadExecutor { Thread(it, "Nocturne-Agent-IO").apply { isDaemon = true } }
    fun init() {
        if (initialized) return
        files = AgentFiles(Minecraft.getInstance().gameDirectory.toPath().resolve("nocturne/agent"))
        settings = AgentSettingsStore(files)
        openAi = OpenAiOAuth(files)
        catalogs = AgentModelCatalog(files, settings, openAi)
        openAi.onModelsChanged = catalogs::acceptOAuth
        settings.onChanged = { catalogs.request(it.authMode) }
        val model = HttpAgentModel(settings::apiKey, openAi)
        val repository = ScriptRepository(files)
        host = MinecraftScriptHost(repository, settings, model)
        scripts = ScriptService(repository, host)
        scriptModules = ScriptModuleService(scripts, { action -> Minecraft.getInstance().execute(action) })
        host.scriptModules = scriptModules
        scripts.onStatusChanged = { status -> Minecraft.getInstance().execute { scriptModules.runtimeChanged(status) } }
        val workspace = AgentWorkspaceContext(scripts, host)
        val tools = AgentTools.client(scripts, host, attachment = { engine.attachment }) { engine.session }
        engine = AgentEngine(model, { settings.settings }, tools, AgentSessionStore(files),
            environment = { signal -> workspace.read(signal).toString() },
            releaseControls = { host.release("@agent") },
            providerIdentity = { options -> AgentTools.sourceHash(options.authMode.name + ":" + options.model + ":" +
                if (options.authMode == AgentAuthMode.OPENAI_OAUTH) openAi.activeAccountId()
                else options.endpoint + ":" + settings.apiKey()) })
        if (settings.loadWarning.isNotEmpty()) engine.session.addEntry("error", settings.loadWarning)
        scripts.installExamples()
        packetSubscription = EventBus.register<PacketEvent> { event ->
            scripts.emit("packet", jsonObject("type" to event.packet.javaClass.simpleName, "side" to event.side.name))
        }
        renderSubscription = EventBus.register<Render2DEvent> { event -> renderScripts(event.partialTicks) }
        resolutionSubscription = EventBus.register<ResolutionChangeEvent> { scripts.clearDrawing() }
        initialized = true
        scriptModules.restore()
        scripts.autoStart()
        catalogs.request(force = true)
        if (settings.settings.authMode != AgentAuthMode.OPENAI_OAUTH && openAi.activeAccount()?.connected == true)
            catalogs.request(AgentAuthMode.OPENAI_OAUTH, force = true)
    }
    fun open(showAgent: Boolean = false) {
        init()
        val mc = Minecraft.getInstance()
        mc.gui.setScreen(AgentScreen(mc.gui.screen(), showAgent))
    }
    fun onTick() {
        if (!initialized) return
        host.onTick()
        scripts.emit("tick", jsonObject("tick" to ++tick))
        if (tick % 20 == 0L) scriptModules.onTick()
    }
    fun onKey(key: Int) { if (initialized) scripts.emit("key", jsonObject("key" to key)) }
    fun onChat(text: String, direction: String) {
        if (initialized) scripts.emit("chat", jsonObject("text" to text.take(4000), "direction" to direction))
    }
    fun onMoveInput(event: MoveInputEvent) { if (initialized) host.applyInput(event) }
    fun onWorldChanged() {
        if (!initialized) return
        scripts.clearDrawing()
        host.onWorldChanged()
        scripts.emit("world", jsonObject("inGame" to (Minecraft.getInstance().player != null)))
    }
    private fun renderScripts(partialTicks: Float) {
        if (!initialized || !scripts.hasDrawing() || !RenderEngine.nanoVG.isFrameActive) return
        val font = RenderEngine.defaultFont ?: return
        if (font.handle < 0) return
        val mc = Minecraft.getInstance()
        val window = mc.window
        val width = ResolutionHelper.scaledWidth; val height = ResolutionHelper.scaledHeight
        if (mc.player == null || width <= 0 || height <= 0) return
        val theme = AgentStyle.theme()
        // NanoVGColor.toHex(true) uses ARGB; the script contract deliberately uses CSS-style RGBA.
        fun rgba(color: NanoVGColor) = "#" + color.toRgba().toUInt().toString(16).padStart(8, '0')
        val input = ScriptRenderInput(width, height, ResolutionHelper.devicePixelRatio,
            ResolutionHelper.toCanvasX(mc.mouseHandler.getScaledXPos(window)).toFloat(),
            ResolutionHelper.toCanvasY(mc.mouseHandler.getScaledYPos(window)).toFloat(), partialTicks,
            mc.gui.screen()?.javaClass?.simpleName,
            jsonObject("background" to rgba(theme.background), "surface" to rgba(theme.surface), "border" to rgba(theme.border),
                "accent" to rgba(theme.primary), "text" to rgba(theme.textPrimary), "muted" to rgba(theme.textSecondary),
                "radius" to AgentStyle.radius, "speed" to AgentStyle.speed))
        val fonts = mapOf("default" to font.handle, "ui" to (RenderEngine.uiFont?.handle ?: font.handle),
            "mono" to (RenderEngine.consolasFont?.handle ?: font.handle))
        scripts.render(input) { _, frame -> scriptRenderer.draw(RenderEngine.context, frame, fonts) }
    }
    fun shutdown() {
        if (!initialized) return
        packetSubscription?.close(); packetSubscription = null
        renderSubscription?.close(); renderSubscription = null
        resolutionSubscription?.close(); resolutionSubscription = null
        engine.close(); scriptModules.close(); scripts.close(); catalogs.close(); openAi.close(); io.shutdownNow()
    }
}

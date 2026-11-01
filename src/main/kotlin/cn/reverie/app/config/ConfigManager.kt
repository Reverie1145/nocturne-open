package cn.reverie.app.config

import cn.reverie.gui.clickgui.ClickGUIModule
import cn.reverie.module.*
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.hud.HudAlignment
import cn.reverie.render.hud.HudConfigManager
import cn.reverie.render.hud.HudElement
import cn.reverie.render.nanovg.NanoVGColor
import com.google.gson.*
import net.minecraft.client.Minecraft
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
object ConfigManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configNamePattern = Regex("[\\p{L}\\p{N}_ -]{1,32}")
    private val reservedConfigNames = setOf("CON", "PRN", "AUX", "NUL") +
        (1..9).flatMap { listOf("COM$it", "LPT$it") }

    /** Optional root override for isolated config round-trip checks. */
    internal var configDirectoryOverride: File? = null
    internal var modulesOverride: List<Module>? = null

    private val configModules: List<Module>
        get() = modulesOverride ?: ModuleManager.modules

    var activeConfig: String = "default"
        private set

    var isInitialized: Boolean = false
        private set

    var isLoading: Boolean = false
        private set

    private var isDirty: Boolean = false
    private var lastDirtyTime: Long = 0L
    private const val DEBOUNCE_DELAY_MS: Long = 450L

    val configsDir: File
        get() {
            configDirectoryOverride?.let { override ->
                if (!override.exists()) override.mkdirs()
                return override
            }
            val base = try {
                Minecraft.getInstance().gameDirectory
            } catch (e: Throwable) {
                File(".")
            }
            val dir = File(base, "nocturne/configs")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private val profileFile: File
        get() = File(configsDir, "active_profile.txt")

    fun markDirty() {
        if (!isInitialized || isLoading) return
        isDirty = true
        lastDirtyTime = System.currentTimeMillis()
    }

    fun onTick() {
        if (isDirty && System.currentTimeMillis() - lastDirtyTime >= DEBOUNCE_DELAY_MS) {
            flush()
        }
    }

    fun flush() {
        if (isDirty) {
            isDirty = false
            saveActiveConfig()
        }
    }

    private fun setActiveProfile(name: String) {
        val safeName = if (isValidConfigName(name)) name else "default"
        activeConfig = safeName
        try {
            profileFile.writeText(safeName, Charsets.UTF_8)
        } catch (e: Exception) {
            System.err.println("[Nocturne] Failed to write active profile: ${e.message}")
        }
    }

    fun init() {
        configsDir
        val profileToLoad = if (profileFile.exists()) {
            try {
                profileFile.readText(Charsets.UTF_8).trim()
            } catch (e: Exception) {
                "default"
            }
        } else "default"

        val targetProfile = if (isValidConfigName(profileToLoad) && File(configsDir, "$profileToLoad.json").exists()) {
            profileToLoad
        } else {
            "default"
        }

        val targetFile = File(configsDir, "$targetProfile.json")
        if (targetFile.exists()) {
            loadConfig(targetProfile)
        } else {
            saveConfig(targetProfile)
        }
        isInitialized = true
    }

    fun saveConfig(name: String): Boolean {
        if (!isValidConfigName(name)) return false
        return try {
            val root = JsonObject()
            root.addProperty("version", "1.0")
            root.addProperty("name", name)
            root.addProperty("timestamp", System.currentTimeMillis())

            val modulesObj = JsonObject()
            for (module in configModules) {
                val modObj = JsonObject()

                val isEnabled = if (module is ClickGUIModule) false else module.enabled
                modObj.addProperty("enabled", isEnabled)
                modObj.addProperty("key", module.keyCode)

                if (module is HudElement) {
                    val hudObj = JsonObject()
                    hudObj.addProperty("x", module.x)
                    hudObj.addProperty("y", module.y)
                    hudObj.addProperty("scale", module.widgetScale.value)
                    hudObj.addProperty("alignment", module.alignment.name)
                    hudObj.addProperty("horizontalAnchor", module.horizontalAnchor.name)
                    hudObj.addProperty("verticalAnchor", module.verticalAnchor.name)
                    hudObj.addProperty("canvasWidth", cn.reverie.render.core.ResolutionHelper.scaledWidth)
                    hudObj.addProperty("canvasHeight", cn.reverie.render.core.ResolutionHelper.scaledHeight)
                    modObj.add("hud", hudObj)
                }

                val settingsObj = JsonObject()
                for (setting in module.settings) {
                    when (setting) {
                        is BooleanSetting -> settingsObj.addProperty(setting.name, setting.value)
                        is NumberSetting -> settingsObj.addProperty(setting.name, setting.value)
                        is ModeSetting -> settingsObj.addProperty(setting.name, setting.value)
                        is StringSetting -> settingsObj.addProperty(setting.name, setting.value)
                        is BindSetting -> settingsObj.addProperty(setting.name, setting.value)
                        is ColorSetting -> {
                            val colorObj = JsonObject()
                            colorObj.addProperty("mode", setting.colorMode.name)
                            colorObj.addProperty("color", setting.primaryColor.toHex(true))
                            colorObj.addProperty("speed", setting.flowSpeed)
                            val stopsArr = JsonArray()
                            setting.colorStops.forEachIndexed { index, stop ->
                                stopsArr.add(JsonPrimitive((if (index == 0) setting.primaryColor else stop).toHex(true)))
                            }
                            colorObj.add("stops", stopsArr)
                            settingsObj.add(setting.name, colorObj)
                        }
                    }
                }
                modObj.add("settings", settingsObj)
                modulesObj.add(module.name, modObj)
            }
            root.add("modules", modulesObj)

            val targetFile = File(configsDir, "$name.json")
            val tempFile = File(configsDir, "$name.json.tmp")

            FileWriter(tempFile).use { writer ->
                gson.toJson(root, writer)
                writer.flush()
            }

            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: Exception) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            try {
                HudConfigManager.save(RenderEngine.hudRenderer.elements)
            } catch (ignored: Throwable) {}

            setActiveProfile(name)
            isDirty = false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun loadConfig(name: String): Boolean {
        if (!isValidConfigName(name)) return false
        val file = File(configsDir, "$name.json")
        if (!file.exists()) return false

        return try {
            isLoading = true
            val root = FileReader(file).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }

            val modulesObj = root.getAsJsonObject("modules") ?: return false
            for (module in configModules) {
                val modObj = modulesObj.getAsJsonObject(module.name) ?: continue

                if (module !is ClickGUIModule && modObj.has("enabled")) {
                    val enabled = modObj.get("enabled").asBoolean
                    if (module.enabled != enabled) {
                        module.enabled = enabled
                    }
                }

                if (modObj.has("key")) {
                    module.keyCode = modObj.get("key").asInt
                }

                if (module is HudElement && modObj.has("hud")) {
                    val hudObj = modObj.getAsJsonObject("hud")
                    if (hudObj.has("horizontalAnchor")) {
                        runCatching { cn.reverie.render.hud.HudHorizontalAnchor.valueOf(hudObj.get("horizontalAnchor").asString) }
                            .getOrNull()?.let { module.horizontalAnchor = it }
                    }
                    if (hudObj.has("verticalAnchor")) {
                        runCatching { cn.reverie.render.hud.HudVerticalAnchor.valueOf(hudObj.get("verticalAnchor").asString) }
                            .getOrNull()?.let { module.verticalAnchor = it }
                    }
                    val curW = cn.reverie.render.core.ResolutionHelper.scaledWidth
                    val curH = cn.reverie.render.core.ResolutionHelper.scaledHeight
                    val savedW = runCatching { hudObj.get("canvasWidth")?.asFloat }.getOrNull()?.takeIf { it.isFinite() && it > 0f }
                    val savedH = runCatching { hudObj.get("canvasHeight")?.asFloat }.getOrNull()?.takeIf { it.isFinite() && it > 0f }
                    val dW = if (savedW != null && curW > 0f) curW - savedW else 0f
                    val dH = if (savedH != null && curH > 0f) curH - savedH else 0f
                    if (hudObj.has("x")) module.x = hudObj.get("x").asFloat + dW * module.horizontalAnchor.resizeFactor
                    if (hudObj.has("y")) module.y = hudObj.get("y").asFloat + dH * module.verticalAnchor.resizeFactor
                    if (hudObj.has("scale")) {
                        try {
                            module.widgetScale.value = hudObj.get("scale").asDouble
                        } catch (ignored: Exception) {}
                    }
                    if (hudObj.has("alignment")) {
                        try {
                            module.alignment = HudAlignment.valueOf(hudObj.get("alignment").asString)
                        } catch (ignored: Exception) {}
                    }
                }

                val settingsObj = modObj.getAsJsonObject("settings") ?: continue
                for (setting in module.settings) {
                    if (!settingsObj.has(setting.name)) continue
                    val elem = settingsObj.get(setting.name)

                    when (setting) {
                        is BooleanSetting -> {
                            if (elem.isJsonPrimitive && elem.asJsonPrimitive.isBoolean) {
                                setting.value = elem.asBoolean
                            }
                        }
                        is NumberSetting -> {
                            if (elem.isJsonPrimitive && elem.asJsonPrimitive.isNumber) {
                                setting.setClamped(elem.asDouble)
                            }
                        }
                        is ModeSetting -> {
                            if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                                val modeStr = elem.asString
                                val compatibleMode = when {
                                    module.name == "NoSlow" && setting.name == "Mode" &&
                                        modeStr.equals("Hypixel", ignoreCase = true) -> "HypixelA"
                                    module.name == "KillAura" && setting.name == "AutoBlock Mode" ->
                                        if (modeStr.equals("Fake", ignoreCase = true)) "Fake" else "None"
                                    else -> modeStr
                                }
                                if (setting.modes.any { it.equals(compatibleMode, ignoreCase = true) }) {
                                    setting.value = setting.modes.first { it.equals(compatibleMode, ignoreCase = true) }
                                }
                            }
                        }
                        is StringSetting -> {
                            if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                                setting.value = elem.asString
                            }
                        }
                        is BindSetting -> {
                            if (elem.isJsonPrimitive && elem.asJsonPrimitive.isNumber) {
                                setting.value = elem.asInt
                            }
                        }
                        is ColorSetting -> {
                            if (elem.isJsonObject) {
                                val cObj = elem.asJsonObject
                                if (cObj.has("mode")) {
                                    try {
                                        setting.colorMode = ColorMode.valueOf(cObj.get("mode").asString)
                                    } catch (ignored: Exception) {}
                                }
                                if (cObj.has("speed")) {
                                    setting.flowSpeed = cObj.get("speed").asFloat
                                }
                                if (cObj.has("stops") && cObj.get("stops").isJsonArray) {
                                    val stopsArr = cObj.getAsJsonArray("stops")
                                    val loadedStops = stopsArr.map { NanoVGColor.fromHex(it.asString) }
                                    if (loadedStops.isNotEmpty()) {
                                        setting.colorStops.clear()
                                        setting.colorStops.addAll(loadedStops)
                                    }
                                }
                                if (cObj.has("color")) {
                                    setting.setPrimaryColor(NanoVGColor.fromHex(cObj.get("color").asString))
                                } else if (setting.colorStops.isNotEmpty()) {
                                    setting.setPrimaryColor(setting.colorStops[0])
                                }
                            } else if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                                setting.setPrimaryColor(NanoVGColor.fromHex(elem.asString))
                            }
                        }
                    }
                }
            }

            try {
                HudConfigManager.save(RenderEngine.hudRenderer.elements)
            } catch (ignored: Throwable) {}

            setActiveProfile(name)
            isDirty = false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            isLoading = false
        }
    }

    fun deleteConfig(name: String): Boolean {
        if (!isValidConfigName(name)) return false
        val file = File(configsDir, "$name.json")
        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted && activeConfig == name) {
                activeConfig = "default"
                setActiveProfile("default")
                if (File(configsDir, "default.json").exists()) {
                    loadConfig("default")
                } else {
                    saveConfig("default")
                }
            }
            deleted
        } else false
    }

    fun listConfigs(): List<String> {
        val files = configsDir.listFiles { _, name -> name.endsWith(".json") && !name.endsWith(".tmp.json") && !name.endsWith(".json.tmp") } ?: return emptyList()
        return files.map { it.name.removeSuffix(".json") }.filter(::isValidConfigName).sorted()
    }

    fun saveActiveConfig() {
        saveConfig(activeConfig)
    }

    /** Export the active profile (or one module) for an explicitly accepted IRC transfer. */
    fun exportSharedConfig(moduleName: String? = null): String? {
        if (!saveConfig(activeConfig)) return null
        return try {
            val root = FileReader(File(configsDir, "$activeConfig.json")).use { JsonParser.parseReader(it).asJsonObject }
            if (moduleName != null) {
                val modules = root.getAsJsonObject("modules") ?: return null
                val module = modules.getAsJsonObject(moduleName) ?: return null
                val selected = JsonObject().apply { add(moduleName, module.deepCopy()) }
                root.add("modules", selected)
            }
            gson.toJson(root)
        } catch (_: Exception) {
            null
        }
    }

    /** Import validated shared JSON through the regular loader; caller must obtain user consent first. */
    fun importSharedConfig(json: String): Boolean {
        if (json.length !in 1..(256 * 1024)) return false
        val root = try { JsonParser.parseString(json).asJsonObject } catch (_: Exception) { return false }
        val modules = try { root.getAsJsonObject("modules") } catch (_: Exception) { null } ?: return false
        if (modules.size() == 0 || modules.size() > configModules.size) return false
        val knownNames = configModules.map { it.name }.toSet()
        if (modules.entrySet().any { it.key !in knownNames || !it.value.isJsonObject }) return false

        val original = activeConfig
        val staging = "irc-${java.util.UUID.randomUUID().toString().replace("-", "").take(12)}"
        val stageFile = File(configsDir, "$staging.json")
        return try {
            stageFile.writeText(gson.toJson(root), Charsets.UTF_8)
            loadConfig(staging)
        } catch (_: Exception) {
            false
        } finally {
            try { stageFile.delete() } catch (_: Exception) {}
            setActiveProfile(original)
        }
    }

    /** Profile names are file names; keep them portable and prevent path traversal. */
    fun isValidConfigName(name: String): Boolean {
        if (name != name.trim() || !configNamePattern.matches(name)) return false
        if (name.uppercase() in reservedConfigNames) return false
        return name.codePointCount(0, name.length) <= 32
    }
}

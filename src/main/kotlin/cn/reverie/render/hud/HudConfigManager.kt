package cn.reverie.render.hud

import cn.reverie.render.core.ResolutionHelper
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import java.io.File

object HudConfigManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val configFile: File by lazy {
        val mcDir = try {
            Minecraft.getInstance().gameDirectory
        } catch (e: Throwable) {
            File(".")
        }
        val dir = File(mcDir, "nocturne")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "hud_layout.json")
    }

    fun save(elements: List<HudElement>) {
        try {
            val root = JsonObject()
            root.addProperty("canvasWidth", ResolutionHelper.scaledWidth)
            root.addProperty("canvasHeight", ResolutionHelper.scaledHeight)
            val elementsObj = JsonObject()

            for (element in elements) {
                val elemObj = JsonObject()
                elemObj.addProperty("x", element.x)
                elemObj.addProperty("y", element.y)
                elemObj.addProperty("isEnabled", element.enabled)
                elemObj.addProperty("scale", element.widgetScale.value)
                elemObj.addProperty("alignment", element.alignment.name)
                elemObj.addProperty("horizontalAnchor", element.horizontalAnchor.name)
                elemObj.addProperty("verticalAnchor", element.verticalAnchor.name)
                elementsObj.add(element.name, elemObj)
            }

            root.add("elements", elementsObj)
            configFile.writeText(gson.toJson(root), Charsets.UTF_8)
        } catch (e: Exception) {
            System.err.println("[Nocturne] Failed to save HUD layout config: ${e.message}")
        }
    }

    fun load(elements: List<HudElement>) {
        if (!configFile.exists()) return

        try {
            val content = configFile.readText(Charsets.UTF_8)
            val root = JsonParser.parseString(content).asJsonObject
            if (!root.has("elements")) return

            val savedWidth = runCatching { root.get("canvasWidth")?.asFloat }.getOrNull()?.takeIf { it.isFinite() && it > 0f }
            val savedHeight = runCatching { root.get("canvasHeight")?.asFloat }.getOrNull()?.takeIf { it.isFinite() && it > 0f }
            val widthDelta = savedWidth?.let { ResolutionHelper.scaledWidth - it } ?: 0f
            val heightDelta = savedHeight?.let { ResolutionHelper.scaledHeight - it } ?: 0f
            val elementsObj = root.getAsJsonObject("elements")
            for (element in elements) {
                if (elementsObj.has(element.name)) {
                    val elemObj = elementsObj.getAsJsonObject(element.name)
                    if (elemObj.has("horizontalAnchor")) {
                        runCatching { HudHorizontalAnchor.valueOf(elemObj.get("horizontalAnchor").asString) }
                            .getOrNull()?.let { element.horizontalAnchor = it }
                    }
                    if (elemObj.has("verticalAnchor")) {
                        runCatching { HudVerticalAnchor.valueOf(elemObj.get("verticalAnchor").asString) }
                            .getOrNull()?.let { element.verticalAnchor = it }
                    }
                    if (elemObj.has("x")) {
                        element.x = elemObj.get("x").asFloat + widthDelta * element.horizontalAnchor.resizeFactor
                    }
                    if (elemObj.has("y")) {
                        element.y = elemObj.get("y").asFloat + heightDelta * element.verticalAnchor.resizeFactor
                    }
                    if (elemObj.has("isEnabled")) element.enabled = elemObj.get("isEnabled").asBoolean
                    if (elemObj.has("scale")) {
                        try {
                            element.widgetScale.value = elemObj.get("scale").asDouble
                        } catch (ignored: Exception) {}
                    }
                    if (elemObj.has("alignment")) {
                        try {
                            element.alignment = HudAlignment.valueOf(elemObj.get("alignment").asString)
                        } catch (ignored: Exception) {}
                    }
                }
            }

            // No clamping here: element sizes and canvas are not final at load time and
            // clamping is lossy. Viewport clamping happens on later resolution changes.
            for (element in elements) {
                if (!element.x.isFinite()) element.x = 0f
                if (!element.y.isFinite()) element.y = 0f
            }
        } catch (e: Exception) {
            System.err.println("[Nocturne] Failed to load HUD layout config: ${e.message}")
        }
    }
}

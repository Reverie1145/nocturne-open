package cn.reverie.module.render

import cn.reverie.module.render.esp.*

import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.render.hud.HUDModule
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import org.lwjgl.glfw.GLFW
object ESP : Module(
    name = "ESP",
    description = "Highlight entities through walls with Zinc Precision overlay",
    category = Category.RENDER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val mode = ModeSetting(
        "Mode",
        "ESP visual rendering style",
        "2D Box",
        listOf("2D Box", "Corners", "3D Box", "Glow", "2D + Glow")
    )

    val boxFill = BooleanSetting(
        "Box Fill",
        "Subtle translucent interior fill for 2D box",
        false,
        visibility = { mode.isMode("2D Box") || mode.isMode("2D + Glow") }
    )

    val healthBar = BooleanSetting("Health Bar", "Draw high-precision vertical health bar", true)
    val healthText = BooleanSetting("Health Text", "Draw exact numeric HP text next to bar", false, { healthBar.value })
    val nameTags = BooleanSetting("Nametags", "Floating acrylic badge with name and distance", true)
    val items = BooleanSetting("Held Item", "Show main hand item beneath the bounding box", true)
    val tracers = BooleanSetting("Tracers", "Draw snaplines to targets", false)
    val tracerOrigin = ModeSetting("Tracer Origin", "Origin point for tracers", "Bottom", listOf("Bottom", "Center"), { tracers.value })
    val offscreen = BooleanSetting("Offscreen", "Show radar chevron arrows for offscreen targets", true)

    val players = BooleanSetting("Players", "Highlight player entities", true)
    val self = BooleanSetting("Self", "Highlight local player in 3rd person view", false, { players.value })
    val monsters = BooleanSetting("Monsters", "Highlight hostile monsters", false)
    val animals = BooleanSetting("Animals", "Highlight passive animals", false)
    val invisibles = BooleanSetting("Invisibles", "Highlight invisible entities", true)
    val droppedItems = BooleanSetting("Dropped Items", "Highlight dropped item entities", false)
    val crystals = BooleanSetting("Crystals", "Highlight end crystal entities", false)

    val maxDistance = NumberSetting("Max Distance", "Maximum highlight distance in blocks", 128.0, 16.0, 256.0, 8.0)
    val cornerLength = NumberSetting("Corner Ratio", "Length ratio for corner brackets", 0.25, 0.10, 0.45, 0.05, { mode.isMode("Corners") })

    init {

        listen(Render2DEvent::class.java) { this.render2D(it.partialTicks) }
    }

    override val suffix: String
        get() = mode.value

    @JvmStatic
    fun shouldGlow(entity: Entity): Boolean {
        if (!enabled) return false
        val isGlowMode = mode.isMode("Glow") || mode.isMode("2D + Glow")
        if (!isGlowMode) return false
        val player = Minecraft.getInstance().player ?: return false
        return isValidForRender(player, entity)
    }

    @JvmStatic
    fun getGlowColor(entity: Entity): Int? {
        if (!enabled) return null
        val isGlowMode = mode.isMode("Glow") || mode.isMode("2D + Glow")
        if (!isGlowMode) return null
        val player = Minecraft.getInstance().player ?: return null
        if (!isValidForRender(player, entity)) return null

        return HUDModule.sampleColor(0f).toArgb()
    }
}

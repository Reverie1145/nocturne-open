package cn.reverie.render.hud.impl.keybind
import cn.reverie.gui.clickgui.ClickGUIModule
import cn.reverie.module.BooleanSetting
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.ModuleManager
import cn.reverie.module.NumberSetting
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudElement
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.util.misc.KeyUtil
import org.lwjgl.glfw.GLFW
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
internal class KeybindAnimState(initial: Float = 0f) {
    val collapse = FloatAnimation(initial, initial, 240L, CubicBezier.EASE_OUT)
    val slide = FloatAnimation(initial, initial, 220L, CubicBezier.EASE_OUT)
    val alpha = FloatAnimation(initial, initial, 190L, CubicBezier.EASE_OUT)
    val scale = FloatAnimation(initial, initial, 220L, CubicBezier.EASE_OUT)
    val switchAnim = FloatAnimation(initial, initial, 180L, CubicBezier.EASE_OUT)
    var flashAlpha = 0f
    var wasEnabled = false
    var currentY: Float = -1f
}

data class KeybindDisplayItem(
    val module: Module?,
    val name: String,
    val keyName: String,
    val stateText: String?,
    val isEnabled: Boolean,
    val slideFactor: Float,
    val collapseFactor: Float,
    val alphaFactor: Float,
    val scaleFactor: Float,
    val switchProgress: Float,
    val flashAlpha: Float
)

class KeybindHUDWidget : HudElement(
    name = "Keybinds",
    defaultX = 12f,
    defaultY = 220f,
    width = 110f,
    height = 80f,
    defaultEnabled = true
) {
    val animStyle = ModeSetting(
        "Animation",
        "Physics motion curve",
        "Fluid Spring",
        listOf("Fluid Spring", "Smooth Bezier", "Snappy")
    )
    val style = ModeSetting("Style", "Visual design style", "Pill", listOf("Pill", "Card", "Compact", "Minimal"))
    val switchLayout = ModeSetting(
        "Switch Layout",
        "Toggle switch arrangement",
        "Separate",
        listOf("Separate", "Connected", "None"),
        { style.isMode("Pill") }
    )
    val iconColorMode = ModeSetting(
        "Icon Color",
        "Header icon accent color",
        "Cyan",
        listOf("Cyan", "Accent", "White")
    )
    val iconType = ModeSetting("Icon", "Header icon symbol", "Keyboard", listOf("Keyboard", "Command", "Keycap"))
    val showKey = BooleanSetting("Show Key", "Show keybind character beside module name", true)
    val keyStyle = ModeSetting("Key Style", "Keycap visual formatting", "Capsule", listOf("Capsule", "Brackets", "Plain"), { showKey.value })
    val onlyEnabled = BooleanSetting("Only Enabled", "Only display modules that are currently enabled", true)
    val hideWhenEmpty = BooleanSetting("Hide When Empty", "Hide the HUD completely when no keybinds are active", false)
    val showCount = BooleanSetting("Header Count", "Show active count badge in header", false, { style.isMode("Card") })
    val cornerRadius = NumberSetting("Corner Radius", "Card/Pill fillet radius", 6.0, 2.0, 14.0, 0.5)
    val pillSpacing = NumberSetting("Pill Spacing", "Vertical gap between pills", 3.5, 1.0, 10.0, 0.5, { style.isMode("Pill") })
    val background = BooleanSetting("Background", "Draw frosted card backdrop", true, { !style.isMode("Minimal") })
    val outline = BooleanSetting("Outline", "Draw hairline outline", true, { !style.isMode("Minimal") && background.value })

    internal val cardBg = NanoVGColor(0.040f, 0.042f, 0.052f, 1f)
    internal val animMap = ConcurrentHashMap<Module, KeybindAnimState>()

    internal val widthAnim = FloatAnimation(110f, 110f, 220L, CubicBezier.EASE_OUT)
    internal val emptyFadeAnim = FloatAnimation(0f, 0f, 200L, CubicBezier.EASE_OUT)
    internal val headerPulseAnim = FloatAnimation(0f, 0f, 250L, CubicBezier.EASE_OUT)

    init {
        addSetting(animStyle)
        addSetting(style)
        addSetting(switchLayout)
        addSetting(iconColorMode)
        addSetting(iconType)
        addSetting(showKey)
        addSetting(keyStyle)
        addSetting(onlyEnabled)
        addSetting(hideWhenEmpty)
        addSetting(showCount)
        addSetting(cornerRadius)
        addSetting(pillSpacing)
        addSetting(background)
        addSetting(outline)
    }

    override fun resetPosition() {
        x = 12f
        y = 220f
    }

    private fun isEligible(module: Module): Boolean {
        if (module is HudElement || module is ClickGUIModule || module == HUDModule) return false
        if (module.hidden.value) return false
        if (module.keyCode == GLFW.GLFW_KEY_UNKNOWN || module.keyCode == 0) return false
        return true
    }

    private fun shouldDisplay(module: Module): Boolean {
        if (!isEligible(module)) return false
        return if (onlyEnabled.value) module.enabled else true
    }

    override fun onTick() {
        super.onTick()

        for (module in ModuleManager.modules) {
            if (!isEligible(module)) continue

            val target = if (shouldDisplay(module)) 1f else 0f
            val state = animMap.getOrPut(module) {
                val s = KeybindAnimState(target)
                s.wasEnabled = module.enabled
                s
            }

            if (module.enabled != state.wasEnabled) {
                state.wasEnabled = module.enabled
                state.flashAlpha = 1.0f
                headerPulseAnim.setImmediate(1.0f)
                headerPulseAnim.animateTo(0.0f)
            }
            state.flashAlpha = (state.flashAlpha * 0.72f).coerceAtLeast(0f)

            state.collapse.animateTo(target)
            state.slide.animateTo(target)
            state.alpha.animateTo(target)
            state.scale.animateTo(if (target > 0.5f) 1f else 0f)
            state.switchAnim.animateTo(if (module.enabled) 1f else 0f)
        }

        animMap.entries.removeIf { (module, state) ->
            !isEligible(module) || (!shouldDisplay(module) &&
                state.collapse.value <= 0.002f &&
                state.alpha.value <= 0.002f &&
                abs(state.collapse.value - 0f) < 0.002f)
        }
    }

    private fun getDisplayItems(isEditing: Boolean): List<KeybindDisplayItem> {
        val items = mutableListOf<KeybindDisplayItem>()

        for (module in ModuleManager.modules) {
            if (!isEligible(module)) continue

            val state = animMap[module]
            val animVal = state?.collapse?.value ?: (if (shouldDisplay(module)) 1f else 0f)
            val isVis = animVal > 0.002f || (isEditing && shouldDisplay(module))

            if (!isVis && !isEditing) continue

            val keyName = KeyUtil.getKeyName(module.keyCode)
            val stateText = if (style.value == "Card") {
                if (module.enabled) "[ON]" else "[OFF]"
            } else null

            val slide = state?.slide?.value ?: 1f
            val collapse = state?.collapse?.value ?: 1f
            val alpha = state?.alpha?.value ?: 1f
            val scale = state?.scale?.value ?: 1f
            val swProgress = state?.switchAnim?.value ?: (if (module.enabled) 1f else 0f)
            val flash = state?.flashAlpha ?: 0f

            items.add(
                KeybindDisplayItem(
                    module = module,
                    name = module.name,
                    keyName = keyName,
                    stateText = stateText,
                    isEnabled = module.enabled,
                    slideFactor = if (isEditing && !module.enabled) 1f else slide,
                    collapseFactor = if (isEditing && !module.enabled) 1f else collapse,
                    alphaFactor = if (isEditing && !module.enabled) 1f else alpha,
                    scaleFactor = if (isEditing && !module.enabled) 1f else scale,
                    switchProgress = if (isEditing && !module.enabled) 1f else swProgress,
                    flashAlpha = flash
                )
            )
        }

        if (items.isEmpty() && isEditing) {
            items.add(KeybindDisplayItem(null, "Chest Stealer", "X", null, true, 1f, 1f, 1f, 1f, 1f, 0f))
            items.add(KeybindDisplayItem(null, "Legit Aura", "R", null, true, 1f, 1f, 1f, 1f, 1f, 0f))
            items.add(KeybindDisplayItem(null, "Inventory", "E", null, true, 1f, 1f, 1f, 1f, 1f, 0f))
        }

        return items
    }

    internal fun resolveIconColor(): NanoVGColor {
        return when (iconColorMode.value) {
            "Cyan" -> NanoVGColor.fromHex("#00B4D8")
            "Accent" -> HUDModule.sampleColor(0f)
            else -> NanoVGColor(0.96f, 0.98f, 1.0f, 0.95f)
        }
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return

        val font: FontRenderer = RenderEngine.fontRenderer
        val items = getDisplayItems(isEditing)

        val hasAnyActive = items.any { it.collapseFactor > 0.005f }
        if (!hasAnyActive && !isEditing && hideWhenEmpty.value) {
            width = 90f
            height = 0f
            return
        }

        when (style.value) {
            "Pill" -> renderPillStyle(font, items, isEditing, partialTicks)
            "Compact" -> renderCompact(font, items, isEditing, partialTicks)
            "Minimal" -> renderMinimal(font, items, isEditing, partialTicks)
            else -> renderCard(font, items, isEditing, partialTicks)
        }
    }

    internal fun renderEditingBounds(partialTicks: Float) = renderEditBoundingBox(partialTicks)

    /**
     * Pill / Floating Capsule style with Elastic Pop & Liquid Switches
     */

    /**
     * Renders a fluid liquid toggle switch with elastic stretching knob and ripple
     */


    /**
     * Card style
     */


    /**
     * Compact style
     */
    private fun renderCompact(
        font: FontRenderer,
        items: List<KeybindDisplayItem>,
        isEditing: Boolean,
        partialTicks: Float
    ) {
        val textShadow = HUDModule.textShadow.value
        val baseRowH = 15.5f
        val padTop = 5f
        val padBottom = 5f
        val radius = cornerRadius.getFloat()
        val iconColor = resolveIconColor()

        val nameFs = 8.5f
        val keyFs = 8.0f

        var maxContentW = 130f
        for (item in items) {
            val nameW = font.getStringWidth(item.name, nameFs)
            val keyStrW = font.getStringWidth(item.keyName, keyFs)
            val keyBadgeW = when (keyStyle.value) {
                "Capsule" -> keyStrW + 8f
                "Brackets" -> font.getStringWidth("[${item.keyName}]", keyFs)
                else -> keyStrW
            }
            val itemTotalW = 24f + nameW + 6f + keyBadgeW + 8f
            maxContentW = max(maxContentW, itemTotalW)
        }

        widthAnim.animateTo(maxContentW)
        width = widthAnim.value

        val totalActiveRowsH = items.sumOf { (baseRowH * it.collapseFactor.coerceIn(0f, 1f)).toDouble() }.toFloat()
        val isEmpty = items.isEmpty() || totalActiveRowsH <= 0.5f
        emptyFadeAnim.animateTo(if (isEmpty) 1f else 0f)

        val cardH = if (isEmpty) 24f else padTop + padBottom + totalActiveRowsH
        height = cardH

        val bgAlpha = HUDModule.bgOpacity.getFloat()
        if (background.value) {
            drawWidgetShadow(x, y, width, height, radius, 0.45f, blur = 6f)
            drawBackdrop(x, y, width, height, radius, cardBg.copy(a = bgAlpha))
            if (outline.value) {
                NanoVGRenderer.drawRoundedOutline(
                    x, y, width, height, radius,
                    NanoVGColor(1f, 1f, 1f, 0.075f), 0.75f
                )
            }
        }

        var curY = y + padTop
        if (emptyFadeAnim.value > 0.01f && isEmpty) {
            val emptyMsg = "No active keybinds"
            val emptyFs = 8.0f
            val emptyW = font.getStringWidth(emptyMsg, emptyFs)
            font.drawString(
                emptyMsg,
                x + (width - emptyW) * 0.5f,
                curY + 3f,
                NanoVGColor(0.55f, 0.58f, 0.65f, 0.70f * emptyFadeAnim.value),
                emptyFs,
                shadow = textShadow
            )
        } else {
            for (item in items) {
                val rowH = renderAnimatedCardRow(font, item, curY, baseRowH, nameFs, keyFs, textShadow, iconColor)
                curY += rowH
            }
        }

        if (isEditing) {
            renderEditBoundingBox(partialTicks)
        }
    }

    /**
     * Minimal style
     */
    private fun renderMinimal(
        font: FontRenderer,
        items: List<KeybindDisplayItem>,
        isEditing: Boolean,
        partialTicks: Float
    ) {
        val textShadow = HUDModule.textShadow.value
        val baseRowH = 15f
        val iconColor = resolveIconColor()

        val nameFs = 9.0f
        val keyFs = 8.5f

        var maxContentW = 110f
        for (item in items) {
            val nameW = font.getStringWidth(item.name, nameFs)
            val keyStrW = font.getStringWidth(item.keyName, keyFs)
            val keyBadgeW = when (keyStyle.value) {
                "Capsule" -> keyStrW + 8f
                "Brackets" -> font.getStringWidth("[${item.keyName}]", keyFs)
                else -> keyStrW
            }
            val itemTotalW = 20f + nameW + 8f + keyBadgeW
            maxContentW = max(maxContentW, itemTotalW)
        }

        widthAnim.animateTo(maxContentW + 8f)
        width = widthAnim.value

        val totalActiveRowsH = items.sumOf { (baseRowH * it.collapseFactor.coerceIn(0f, 1f)).toDouble() }.toFloat()
        val isEmpty = items.isEmpty() || totalActiveRowsH <= 0.5f
        emptyFadeAnim.animateTo(if (isEmpty) 1f else 0f)

        val cardH = if (isEmpty) 18f else totalActiveRowsH
        height = cardH

        var curY = y
        if (emptyFadeAnim.value > 0.01f && isEmpty) {
            val emptyMsg = "No active keybinds"
            val emptyFs = 8.5f
            font.drawString(
                emptyMsg,
                x + 4f,
                curY + 3f,
                NanoVGColor(0.55f, 0.58f, 0.65f, 0.70f * emptyFadeAnim.value),
                emptyFs,
                shadow = textShadow
            )
        } else {
            for (item in items) {
                val rowH = renderAnimatedCardRow(font, item, curY, baseRowH, nameFs, keyFs, textShadow, iconColor)
                curY += rowH
            }
        }

        if (isEditing) {
            renderEditBoundingBox(partialTicks)
        }
    }
}

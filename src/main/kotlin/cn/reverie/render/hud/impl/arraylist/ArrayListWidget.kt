package cn.reverie.render.hud.impl.arraylist
import cn.reverie.gui.clickgui.ClickGUIModule
import cn.reverie.module.BooleanSetting
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.ModuleManager
import cn.reverie.module.NumberSetting
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudElement
import cn.reverie.render.hud.HudHorizontalAnchor
import cn.reverie.render.hud.HudVerticalAnchor
import cn.reverie.render.nanovg.NanoVGColor
import java.util.concurrent.ConcurrentHashMap

internal fun FontRenderer.drawHorizontalFadeString(
    text: String,
    x: Float,
    y: Float,
    fontSize: Float,
    alpha: Float,
    alignment: TextAlignment,
    shadow: Boolean,
    blur: Float = 0f
): Float {
    if (text.isEmpty()) return 0f

    val glyphs = text.codePoints().toArray().map { codePoint ->
        val glyph = String(Character.toChars(codePoint))
        glyph to getStringWidth(glyph, fontSize)
    }
    val textWidth = glyphs.sumOf { it.second.toDouble() }.toFloat()
    val leftX = when (alignment) {
        TextAlignment.TOP_RIGHT, TextAlignment.MIDDLE_RIGHT, TextAlignment.BOTTOM_RIGHT, TextAlignment.BASELINE_RIGHT -> x - textWidth
        TextAlignment.TOP_CENTER, TextAlignment.MIDDLE_CENTER, TextAlignment.BOTTOM_CENTER, TextAlignment.BASELINE_CENTER -> x - textWidth * 0.5f
        else -> x
    }

    var cursorX = leftX
    var cursorWidth = 0f
    for ((glyph, glyphWidth) in glyphs) {
        val position = if (textWidth > 0.001f) {
            ((cursorWidth + glyphWidth * 0.5f) / textWidth).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        val color = HUDModule.sampleFadeColor(position).multiplyAlpha(alpha)
        drawString(glyph, cursorX, y, color, fontSize, TextAlignment.TOP_LEFT, shadow, blur)
        cursorX += glyphWidth
        cursorWidth += glyphWidth
    }

    return textWidth
}

private class ModuleAnimState(initial: Float = 0f) {
    val slide = FloatAnimation(initial, initial, 360L, CubicBezier.EASE_OUT)
    val collapse = FloatAnimation(initial, initial, 320L, CubicBezier.EASE_OUT)
}

internal class DisplayEntry(
    val mainName: String,
    val suffix: String?,
    val totalText: String,
    val mainNameWidth: Float,
    val suffixWidth: Float,
    val textWidth: Float,
    val slideFactor: Float,
    val collapseFactor: Float
)

internal class RowBox(
    val entry: DisplayEntry,
    val index: Int,
    val color: NanoVGColor,
    val glowColor: NanoVGColor,
    val suffixColor: NanoVGColor,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

class ArrayListWidget : HudElement(
    name = "ArrayList",
    defaultX = 840f,
    defaultY = 12f,
    width = 120f,
    height = 200f,
    defaultEnabled = true,
    horizontalAnchor = HudHorizontalAnchor.RIGHT,
    verticalAnchor = HudVerticalAnchor.TOP
) {
    val style = ModeSetting("Style", "ArrayList visual style", "Modern", listOf("Modern", "Legacy", "Zinc"))
    val bar = ModeSetting("Bar", "Item accent bar position", "None", listOf("None", "Left", "Right", "Both"))
    val sortMode = ModeSetting("Sort", "Sorting order", "Length", listOf("Length", "Alphabetical"))
    val suffixStyle = ModeSetting("Suffix", "Module mode display style", "Space", listOf("Brackets", "Space", "None"))
    val textCase = ModeSetting("Text Case", "Text capitalization", "Normal", listOf("Normal", "Upper", "Lower"))
    val anchor = ModeSetting("Anchor", "Screen edge the list aligns to", "Auto", listOf("Auto", "Right", "Left"))
    val background = BooleanSetting("Background", "Draw per-row background bar", true)
    val outline = BooleanSetting("Outline", "Draw hairline row outline", false)
    val cornerRadius = NumberSetting("Corner Radius", "Fillet radius for modern style", 4.5, 1.0, 10.0, 0.5)
    val fontSize = NumberSetting("Font Size", "Row text size", 13.0, 7.0, 22.0, 0.5)
    val spacing = NumberSetting("Spacing", "Row height multiplier", 1.40, 1.0, 2.5, 0.05)
    val padding = NumberSetting("Padding", "Horizontal text padding", 5.5, 0.0, 15.0, 0.5)

    internal val modernSuffixColor = NanoVGColor(0.61f, 0.64f, 0.67f, 0.95f)
    internal val legacySuffixColor = NanoVGColor(0.83f, 0.85f, 0.89f, 0.95f)
    internal val zincSuffixColor = NanoVGColor(0.65f, 0.68f, 0.76f, 0.85f)

    private val animMap = ConcurrentHashMap<Module, ModuleAnimState>()

    init {
        outline.visibility = { background.value }
        cornerRadius.visibility = { style.isMode("Modern") && background.value }
        fontSize.visibility = { !style.isMode("Zinc") }
        spacing.visibility = { !style.isMode("Zinc") }
        padding.visibility = { !style.isMode("Zinc") }

        addSetting(style)
        addSetting(bar)
        addSetting(sortMode)
        addSetting(suffixStyle)
        addSetting(textCase)
        addSetting(anchor)
        addSetting(background)
        addSetting(outline)
        addSetting(cornerRadius)
        addSetting(fontSize)
        addSetting(spacing)
        addSetting(padding)
    }

    override fun resetPosition() {
        val screenW = ResolutionHelper.scaledWidth
        x = (screenW - (width + 8f)).coerceAtLeast(8f)
        y = 12f
    }

    override fun onTick() {
        super.onTick()

        for (module in ModuleManager.modules) {
            if (isExcluded(module)) continue

            val state = animMap.getOrPut(module) {
                val initial = if (module.enabled) 1f else 0f
                ModuleAnimState(initial)
            }
            val target = if (module.enabled) 1f else 0f
            state.slide.animateTo(target)
            state.collapse.animateTo(target)
        }
    }

    private fun isExcluded(module: Module): Boolean {
        if (module is HudElement || module is ClickGUIModule || module == HUDModule) return true
        return module.hidden.value
    }

    private fun getModuleSuffix(module: Module): String? {
        if (suffixStyle.value == "None") return null
        val modeVal = module.suffix ?: module.settings.filterIsInstance<ModeSetting>().firstOrNull {
            it.name.equals("Mode", ignoreCase = true) || it.name.equals("Type", ignoreCase = true)
        }?.value ?: module.settings.filterIsInstance<ModeSetting>().firstOrNull()?.value ?: return null

        return when (suffixStyle.value) {
            "Brackets" -> " [$modeVal]"
            "Space" -> " $modeVal"
            else -> null
        }
    }

    private fun formatName(name: String): String {
        return when (textCase.value) {
            "Upper" -> name.uppercase()
            "Lower" -> name.lowercase()
            else -> name
        }
    }

    internal fun collectEntries(fontSize: Float, isEditing: Boolean): MutableList<DisplayEntry> {
        val font: FontRenderer = RenderEngine.fontRenderer
        val entries = mutableListOf<DisplayEntry>()

        for (module in ModuleManager.modules) {
            if (isExcluded(module)) continue

            val state = animMap.getOrPut(module) {
                val initial = if (module.enabled) 1f else 0f
                ModuleAnimState(initial)
            }
            val target = if (module.enabled) 1f else 0f
            state.slide.animateTo(target)
            state.collapse.animateTo(target)

            val collapse = state.collapse.value
            val slide = state.slide.value
            if (collapse <= 0.005f && slide <= 0.005f && !module.enabled) continue

            val mainName = formatName(module.name)
            val suffix = getModuleSuffix(module)
            val totalText = if (suffix != null) mainName + suffix else mainName

            val nameW = font.getStringWidth(mainName, fontSize)
            val suffW = if (suffix != null) font.getStringWidth(suffix, fontSize) else 0f
            val totalW = if (suffix != null) nameW + suffW else nameW

            val effectiveSlide = if (isEditing && !module.enabled) 1f else slide
            val effectiveCollapse = if (isEditing && !module.enabled) 1f else collapse

            entries.add(
                DisplayEntry(
                    mainName = mainName,
                    suffix = suffix,
                    totalText = totalText,
                    mainNameWidth = nameW,
                    suffixWidth = suffW,
                    textWidth = totalW,
                    slideFactor = effectiveSlide,
                    collapseFactor = effectiveCollapse
                )
            )
        }

        if (entries.isEmpty() && isEditing) {
            val preview = listOf(
                "KillAura" to "Adaptive",
                "Scaffold" to "Telly Bridge",
                "ESP" to "2D Box"
            )
            for ((name, mode) in preview) {
                val suffix = " $mode"
                val totalText = "$name$suffix"
                val nameW = font.getStringWidth(name, fontSize)
                val suffW = font.getStringWidth(suffix, fontSize)
                entries.add(
                    DisplayEntry(
                        mainName = name,
                        suffix = suffix,
                        totalText = totalText,
                        mainNameWidth = nameW,
                        suffixWidth = suffW,
                        textWidth = nameW + suffW,
                        slideFactor = 1f,
                        collapseFactor = 1f
                    )
                )
            }
        }

        return entries
    }

    internal fun sortEntries(entries: MutableList<DisplayEntry>) {
        if (sortMode.value == "Alphabetical") {
            entries.sortBy { it.totalText }
        } else {
            entries.sortByDescending { it.textWidth }
        }
    }

    internal fun resolveColor(index: Int, total: Int, alpha: Float): NanoVGColor {
        val offset = if (total > 1) index.toFloat() / (total - 1).toFloat() else 0f
        val colorOffset = if (HUDModule.colorMode.isMode("Fade")) 0.5f else offset
        return HUDModule.sampleColor(colorOffset).multiplyAlpha(alpha)
    }

    internal fun alignsRight(): Boolean {
        return when (anchor.value) {
            "Right" -> true
            "Left" -> false
            else -> (x + width * 0.5f) > ResolutionHelper.scaledWidth * 0.5f
        }
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return

        when (style.value) {
            "Modern" -> renderModern(partialTicks, isEditing)
            "Zinc" -> renderZinc(partialTicks, isEditing)
            else -> renderLegacy(partialTicks, isEditing)
        }
    }



    internal fun renderEditingBounds(partialTicks: Float) = renderEditBoundingBox(partialTicks)

}

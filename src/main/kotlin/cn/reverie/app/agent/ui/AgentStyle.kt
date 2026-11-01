package cn.reverie.app.agent.ui

import cn.reverie.gui.clickgui.ClickGUIModule
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UITheme

/** The same glass tint, white outlines, corner setting and accent as ClickGUI. */
internal object AgentStyle {
    val radius get() = ClickGUIModule.cornerRadius.getFloat()
    val speed get() = ClickGUIModule.animSpeed.getFloat().coerceIn(0.5f, 3f)
    fun theme(): UITheme {
        val accent = if (ClickGUIModule.style.isMode("Window")) NanoVGColor(0.91f, 0.92f, 0.94f, 1f) else HUDModule.sampleColor(0f)
        return UITheme.DARK.copy(
            background = NanoVGColor(0.047f, 0.051f, 0.067f, 0.96f),
            surface = NanoVGColor(1f, 1f, 1f, 0.035f),
            surfaceHover = NanoVGColor(1f, 1f, 1f, 0.075f),
            surfaceActive = NanoVGColor(1f, 1f, 1f, 0.10f),
            primary = accent, primaryHover = accent.lerp(NanoVGColor.WHITE, 0.15f),
            textSecondary = NanoVGColor(0.62f, 0.64f, 0.69f, 1f),
            textDisabled = NanoVGColor(0.39f, 0.41f, 0.46f, 1f),
            border = NanoVGColor(1f, 1f, 1f, 0.09f), borderFocus = accent.withAlpha(0.72f))
    }
}

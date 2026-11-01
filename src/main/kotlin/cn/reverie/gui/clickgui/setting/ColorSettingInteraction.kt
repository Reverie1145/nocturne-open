package cn.reverie.gui.clickgui.setting
import cn.reverie.module.*
import cn.reverie.render.nanovg.NanoVGColor
internal fun ColorSettingComponent.updateSV(mouseX: Float, mouseY: Float, bx: Float, by: Float, bw: Float, bh: Float) {
    hsv[1] = ((mouseX - bx) / bw).coerceIn(0f, 1f)
    hsv[2] = (1f - (mouseY - by) / bh).coerceIn(0f, 1f)
    applyHSV()
}

internal fun ColorSettingComponent.updateHue(mouseX: Float, sliderX: Float, sliderW: Float) {
    val pct = ((mouseX - sliderX) / sliderW).coerceIn(0f, 1f)
    hsv[0] = (pct * 360f).coerceIn(0f, 360f)
    applyHSV()
}

internal fun ColorSettingComponent.updateAlpha(mouseX: Float, sliderX: Float, sliderW: Float) {
    val pct = ((mouseX - sliderX) / sliderW).coerceIn(0f, 1f)
    hsv[3] = pct.coerceIn(0f, 1f)
    applyHSV()
}

internal fun ColorSettingComponent.updateSpeed(mouseX: Float, sliderX: Float, sliderW: Float) {
    val pct = ((mouseX - sliderX) / sliderW).coerceIn(0f, 1f)
    colorSetting.flowSpeed = (0.2f + pct * 2.8f).coerceIn(0.2f, 3.0f)
}

private fun ColorSettingComponent.applyHSV() {
    colorSetting.setActiveColor(NanoVGColor.fromHsv(hsv[0], hsv[1], hsv[2], hsv[3]))
    noteColorChanged()
}

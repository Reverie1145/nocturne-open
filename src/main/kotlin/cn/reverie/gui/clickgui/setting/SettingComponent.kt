package cn.reverie.gui.clickgui.setting
import cn.reverie.module.*
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
abstract class SettingComponent(val setting: Setting<*>) {

    var x: Float = 0f
    var y: Float = 0f
    var width: Float = 140f
    open var height: Float = 20f

    val isVisible: Boolean
        get() = setting.visibility()

    val visibilityAnim = FloatAnimation(
        if (setting.visibility()) 1f else 0f,
        if (setting.visibility()) 1f else 0f,
        260L,
        CubicBezier.EASE_OUT
    )

    val effectiveHeight: Float
        get() = height * visibilityAnim.value

    fun updateVisibilityAnim(animMultiplier: Float) {
        visibilityAnim.durationMs = (260L / animMultiplier).toLong().coerceIn(60L, 600L)
        visibilityAnim.animateTo(if (isVisible) 1f else 0f)
    }

    fun isMouseOver(mouseX: Float, mouseY: Float): Boolean {
        val effH = height * visibilityAnim.value
        return visibilityAnim.value > 0.4f && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + effH
    }

    abstract fun render(mouseX: Float, mouseY: Float, partialTicks: Float)

    open fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean = false
    open fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean = false
    open fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean = false
    open fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false
    open fun charTyped(codePoint: Char, modifiers: Int): Boolean = false
}

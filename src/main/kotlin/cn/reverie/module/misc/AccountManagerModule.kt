package cn.reverie.module.misc

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.render.ui.screens.AccountManagerScreen
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object AccountManagerModule : Module(
    name = "AccountManager",
    description = "Open the Minecraft account manager (Microsoft / offline / token)",
    category = Category.MISC,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    private var screen: AccountManagerScreen? = null
    private var closingFromScreen = false

    override fun onEnable() {
        val mc = Minecraft.getInstance()
        mc.execute {
            val target = AccountManagerScreen(mc.gui.screen())
            screen = target
            mc.gui.setScreen(target)
        }
    }

    override fun onDisable() {
        val mc = Minecraft.getInstance()
        mc.execute {
            if (!closingFromScreen && mc.gui.screen() === screen) {
                mc.gui.setScreen(null)
            }
            screen = null
        }
    }

    fun onScreenClosed() {
        if (!enabled) return
        closingFromScreen = true
        try {
            enabled = false
        } finally {
            closingFromScreen = false
        }
    }
}

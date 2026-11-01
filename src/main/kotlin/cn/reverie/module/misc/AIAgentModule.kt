package cn.reverie.module.misc

import cn.reverie.app.agent.AgentManager
import cn.reverie.app.agent.ui.AgentScreen
import cn.reverie.module.Category
import cn.reverie.module.Module
import net.minecraft.client.Minecraft

object AIAgentModule : Module("AIAgent", "Open the AI Agent and JavaScript script workspace", Category.MISC) {
    private var closing = false
    override fun onEnable() { Minecraft.getInstance().execute { AgentManager.open() } }
    override fun onDisable() {
        if (closing) return
        val mc = Minecraft.getInstance()
        mc.execute { if (mc.gui.screen() is AgentScreen) (mc.gui.screen() as AgentScreen).onClose() }
    }
    fun onScreenClosed() {
        if (!enabled) return
        closing = true
        try { enabled = false } finally { closing = false }
    }
}

package cn.reverie.module.misc

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.app.music.ui.CloudMusicScreen
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * MusicPlayerModule allows opening the CloudMusic player GUI with a keybind (default: 'M').
 * Action module: Opening the GUI does not keep the module state enabled.
 */
object MusicPlayerModule : Module(
    name = "MusicPlayer",
    description = "Next-Gen CloudMusic player for Nocturne with smooth animations & lyrics",
    category = Category.MISC,
    keyCode = GLFW.GLFW_KEY_M
) {

    override fun onEnable() {
        val mc = Minecraft.getInstance()
        mc.execute {
            val target = CloudMusicScreen(mc.gui.screen())
            mc.gui.setScreen(target)
            enabled = false
        }
    }

    override fun onDisable() {
        // Instant action module; remains disabled
    }
}

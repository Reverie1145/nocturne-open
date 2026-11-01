package cn.reverie.gui.clickgui.irc
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.ui.NvgScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/** Full-screen view for an IRC image link. */
internal class IrcImageViewerScreen(private val imageUrl: String) : Screen(Component.literal("IRC Image Preview")), NvgScreen {
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}
    override fun extractMenuBackground(graphics: GuiGraphicsExtractor) {}
    override fun extractMenuBackground(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {}
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}

    override fun renderNvg(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val sw = ResolutionHelper.scaledWidth
        val sh = ResolutionHelper.scaledHeight
        try {
            RenderEngine.backdropBlurRenderer.renderBackdrop(0f, 0f, sw, sh,
                radius = 0f,
                tintColor = NanoVGColor(0.012f, 0.014f, 0.02f, 0.85f),
                blurRadius = 18f,
                enableBlur = true,
                backdropOpacity = 0.85f)
        } catch (_: Throwable) {
            Draw2D.roundedRect(0f, 0f, sw, sh, 0f, NanoVGColor(0.012f, 0.014f, 0.02f, 0.90f))
        }
        NanoVGRenderer.drawString("IRC Image Preview", 18f, 15f, NanoVGColor(0.90f, 0.91f, 0.94f, 0.92f), 10f)
        NanoVGRenderer.drawRightVerticallyCenteredString("Click or press Esc to close", sw - 18f, 22f, NanoVGColor(0.53f, 0.56f, 0.62f, 0.80f), 8.5f)

        val margin = 28f
        val imageX = margin
        val imageY = 42f
        val imageW = (sw - margin * 2f).coerceAtLeast(1f)
        val imageH = (sh - 42f - margin).coerceAtLeast(1f)
        IrcImageCache.draw(imageUrl, imageX, imageY, imageW, imageH, 1f)
        NanoVGRenderer.drawRoundedOutline(imageX, imageY, imageW, imageH, 6f, NanoVGColor(1f, 1f, 1f, 0.12f), 0.8f)
    }

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0) closeViewer()
        return true
    }

    override fun onKeyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeViewer()
            return true
        }
        return false
    }

    override fun onCharTyped(event: CharacterEvent): Boolean = false

    private fun closeViewer() {
        Minecraft.getInstance().gui.setScreen(null)
    }

    companion object {
        fun open(url: String) {
            Minecraft.getInstance().gui.setScreen(IrcImageViewerScreen(url))
        }
    }
}

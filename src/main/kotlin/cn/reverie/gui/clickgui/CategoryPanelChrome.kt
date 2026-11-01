package cn.reverie.gui.clickgui

import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer

/** Shared border, header hover, and divider treatment for floating ClickGUI panels. */
internal object CategoryPanelChrome {
    fun draw(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        headerHeight: Float,
        cornerRadius: Float,
        hover: Float,
        expansion: Float
    ) {
        NanoVGRenderer.drawRoundedOutline(
            x, y, width, height, cornerRadius,
            NanoVGColor(1f, 1f, 1f, 0.045f), 1.0f
        )

        if (hover > 0.01f) {
            NanoVGRenderer.drawRoundedRect(
                x + 1f, y + 1f, width - 2f, headerHeight - 1f,
                CornerRadii(cornerRadius, cornerRadius, 0f, 0f),
                NanoVGColor(1f, 1f, 1f, 0.04f * hover)
            )
        }

        if (expansion > 0.05f) {
            Draw2D.line(
                x + 6f, y + headerHeight, x + width - 6f, y + headerHeight,
                NanoVGColor(1f, 1f, 1f, 0.05f * expansion), 1.0f
            )
        }
    }
}

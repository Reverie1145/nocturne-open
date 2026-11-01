package cn.reverie.render.draw

import cn.reverie.render.font.FontRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.nanovg.NanoVGColor

object BadgeRenderer {

    fun drawPillBadge(
        context: Long,
        fontRenderer: FontRenderer,
        x: Float,
        y: Float,
        text: String,
        backgroundColor: NanoVGColor,
        textColor: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 10f,
        paddingHorizontal: Float = 6f,
        paddingVertical: Float = 2f
    ): Float {
        val textWidth = fontRenderer.getStringWidth(text, fontSize)
        val textHeight = fontRenderer.getStringHeight(fontSize)
        val badgeWidth = textWidth + paddingHorizontal * 2f
        val badgeHeight = textHeight + paddingVertical * 2f

        RoundedRect.draw(context, x, y, badgeWidth, badgeHeight, CornerRadii(badgeHeight * 0.5f), backgroundColor)

        fontRenderer.drawString(
            text,
            x + badgeWidth * 0.5f,
            y + paddingVertical,
            textColor,
            fontSize,
            TextAlignment.TOP_CENTER
        )

        return badgeWidth
    }
}

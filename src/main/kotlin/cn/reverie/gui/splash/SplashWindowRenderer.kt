package cn.reverie.gui.splash

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlin.math.sin
/** Paints the splash window from an immutable snapshot of its current display state. */
internal object SplashWindowRenderer {
    private const val CORNER_RADIUS = 24f
    fun render(g2d: Graphics2D, w: Int, h: Int, background: BufferedImage?, displayedProgress: Float, currentElapsed: Long, currentOperation: String, protectionStatus: String) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // 1. Rounded rectangle clip
        val clipShape = RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), CORNER_RADIUS, CORNER_RADIUS)
        g2d.clip = clipShape

        // 2. Draw frosted glass background
        val bg = background
        if (bg != null) {
            g2d.drawImage(bg, 0, 0, w, h, null)
        } else {
            g2d.color = Color(15, 17, 26)
            g2d.fillRect(0, 0, w, h)
        }

        // 3. Translucent Acrylic Wash
        val acrylic = GradientPaint(
            0f, 0f, Color(8, 10, 18, 90),
            0f, h.toFloat(), Color(4, 6, 12, 175)
        )
        g2d.paint = acrylic
        g2d.fillRect(0, 0, w, h)

        // 4. Center radial soft vignette for high text contrast
        val centerDarkness = RadialGradientPaint(
            w / 2f, h / 2f - 10f, 260f,
            floatArrayOf(0.0f, 0.5f, 1.0f),
            arrayOf(Color(0, 0, 0, 75), Color(0, 0, 0, 30), Color(0, 0, 0, 0))
        )
        g2d.paint = centerDarkness
        g2d.fillRect(0, 0, w, h)

        // 5. 1px subtle frosted border highlight
        g2d.clip = null
        val borderGrad = GradientPaint(
            0f, 0f, Color(255, 255, 255, 55),
            0f, h.toFloat(), Color(255, 255, 255, 18)
        )
        g2d.paint = borderGrad
        g2d.stroke = BasicStroke(1f)
        g2d.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, CORNER_RADIUS, CORNER_RADIUS))

        // 6. Title: "Protection By Reverie"
        val titleFont = Font("Segoe UI", Font.BOLD, 26)
        g2d.font = titleFont
        val fm = g2d.fontMetrics
        val title = "Protection By Reverie"
        val titleW = fm.stringWidth(title)
        val titleX = (w - titleW) / 2
        val titleY = 152

        // Soft text shadow
        g2d.color = Color(0, 0, 0, 70)
        g2d.drawString(title, titleX, titleY + 2)
        g2d.color = Color(0, 0, 0, 40)
        g2d.drawString(title, titleX, titleY + 1)

        // Crisp white highlight text
        g2d.color = Color(255, 255, 255, 255)
        g2d.drawString(title, titleX, titleY)

        // 7. Subtitle: "For the Nocturne Client with love."
        val subFont = Font("Segoe UI", Font.PLAIN, 12)
        g2d.font = subFont
        val fmSub = g2d.fontMetrics
        val sub = "For the Nocturne Client with love."
        val subW = fmSub.stringWidth(sub)
        val subX = (w - subW) / 2
        val subY = 176

        g2d.color = Color(0, 0, 0, 60)
        g2d.drawString(sub, subX, subY + 1)

        g2d.color = Color(203, 213, 225, 210) // #CBD5E1
        g2d.drawString(sub, subX, subY)

        // 8. Slim Glowing Progress Bar
        val barW = 280
        val barH = 3
        val barX = (w - barW) / 2
        val barY = 212

        // Frosted track
        g2d.color = Color(255, 255, 255, 25)
        g2d.fillRoundRect(barX, barY, barW, barH, barH, barH)

        // Ice blue to white gradient fill — painted from the smoothed REAL value
        val shown = displayedProgress.coerceIn(0f, 1f)
        val fillW = (barW * shown).roundToInt()
        if (fillW > 0) {
            val grad = GradientPaint(
                barX.toFloat(), barY.toFloat(), Color(175, 200, 255, 210),
                (barX + fillW).toFloat(), barY.toFloat(), Color(255, 255, 255, 255)
            )
            g2d.paint = grad
            g2d.fillRoundRect(barX, barY, fillW, barH, barH, barH)

            // 120 FPS Subtle Pulsing Bloom
            val elapsed = currentElapsed
            val bloomAlpha = (140 + 35 * sin(elapsed * 0.008)).toInt().coerceIn(80, 255)
            val bloomSize = 13
            val orbBloom = RadialGradientPaint(
                (barX + fillW).toFloat(), barY + 1.5f, bloomSize / 2f,
                floatArrayOf(0.0f, 0.4f, 1.0f),
                arrayOf(
                    Color(255, 255, 255, bloomAlpha),
                    Color(180, 210, 255, bloomAlpha / 3),
                    Color(180, 210, 255, 0)
                )
            )
            g2d.paint = orbBloom
            g2d.fillOval(
                barX + fillW - bloomSize / 2,
                (barY + 1.5f - bloomSize / 2f).roundToInt(),
                bloomSize,
                bloomSize
            )

            // Core bright orb
            g2d.color = Color.WHITE
            val orbSize = 5
            g2d.fillOval(
                barX + fillW - orbSize / 2,
                (barY + 1.5f - orbSize / 2f).roundToInt(),
                orbSize,
                orbSize
            )
        }

        // 8. Percentage label
        val pct = (shown * 100).roundToInt().coerceIn(0, 100)
        val pctStr = "$pct%"
        val pctFont = Font("Segoe UI", Font.PLAIN, 11)
        g2d.font = pctFont
        val fmPct = g2d.fontMetrics
        val pctW = fmPct.stringWidth(pctStr)
        val pctX = (w - pctW) / 2
        val pctY = barY + 22

        g2d.color = Color(0, 0, 0, 60)
        g2d.drawString(pctStr, pctX, pctY + 1)

        g2d.color = Color(220, 230, 245, 190)
        g2d.drawString(pctStr, pctX, pctY)

        // 9. Protection status row + current operation
        val stFont = Font("Segoe UI", Font.PLAIN, 11)
        g2d.font = stFont
        val fmSt = g2d.fontMetrics
        val stStr = currentOperation
        val stW = fmSt.stringWidth(stStr)
        val stX = (w - stW) / 2
        val stY = barY + 40
        g2d.color = Color(0, 0, 0, 60)
        g2d.drawString(stStr, stX, stY + 1)
        g2d.color = Color(190, 220, 255, 210)
        g2d.drawString(stStr, stX, stY)

        val opFont = Font("Segoe UI", Font.PLAIN, 10)
        g2d.font = opFont
        val fmOp = g2d.fontMetrics
        val opStr = protectionStatus
        val opW = fmOp.stringWidth(opStr)
        val opX = (w - opW) / 2
        val opY = stY + 14
        g2d.color = Color(0, 0, 0, 50)
        g2d.drawString(opStr, opX, opY + 1)
        g2d.color = Color(150, 220, 170, 190)
        g2d.drawString(opStr, opX, opY)
    }
}

package cn.reverie.render.hud

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.impl.*
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGTransform
internal fun HudRenderer.renderEditOverlay(partialTicks: Float) {
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val accent = HUDModule.sampleColor(0f)
        val font = RenderEngine.fontRenderer

        // 1. 自动对齐引导线（吸附时显示）
        val snapColor = NanoVGColor(1f, 1f, 1f, 0.50f)
        val centerSnapColor = accent.withAlpha(0.9f)

        for (line in activeSnapLines) {
            val color = if (line.isCenter) centerSnapColor else snapColor
            val width = if (line.isCenter) 1.5f else 1.0f
            if (line.isVertical) {
                Draw2D.line(line.position, line.start, line.position, line.end, color, width)
            } else {
                Draw2D.line(line.start, line.position, line.end, line.position, color, width)
            }

            if (line.label != null) {
                val textWidth = font.getStringWidth(line.label, 8.5f)
                val lx = if (line.isVertical) {
                    val preferred = if (line.position > screenW * 0.5f) line.position - textWidth - 5f else line.position + 5f
                    preferred.coerceIn(2f, (screenW - textWidth - 2f).coerceAtLeast(2f))
                } else {
                    (screenW * 0.5f - textWidth * 0.5f).coerceAtLeast(2f)
                }
                val ly = if (line.isVertical) {
                    screenH * 0.5f
                } else if (line.isCenter || line.position > screenH * 0.5f) {
                    line.position - 6f
                } else {
                    line.position + 4f
                }
                font.drawString(line.label, lx, ly, if (line.isCenter) centerSnapColor else snapColor, 8.5f)
            }
        }

        // 2. 控件边框
        for (element in elements) {
            val shouldDrawBox = element.enabled || draggingElement == element || element.isHovered || element.isSelected
            if (shouldDrawBox) {
                val s = element.scale
                if (s != 1f) {
                    val transform = NanoVGTransform(Draw2D.context)
                    transform.withTransform {
                        transform.translate(element.x, element.y)
                        transform.scale(s)
                        transform.translate(-element.x, -element.y)
                        element.renderEditBoundingBox(partialTicks)
                    }
                } else {
                    element.renderEditBoundingBox(partialTicks)
                }
            }
        }
    }

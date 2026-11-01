package cn.reverie.render.hud.impl

import cn.reverie.module.BooleanSetting
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudElement
import cn.reverie.render.hud.HudVerticalAnchor
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.Hud
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffects
import net.minecraft.core.Holder

class PotionHUDWidget : HudElement(
    name = "PotionHUD",
    defaultX = 12f,
    defaultY = 320f,
    width = 145f,
    height = 80f,
    defaultEnabled = true,
    verticalAnchor = HudVerticalAnchor.BOTTOM
) {
    val showDurationBar = BooleanSetting("Duration Bar", "Progress bar underneath potion card", true)
    val interceptVanillaPotion = BooleanSetting(
        "Intercept Vanilla Potion",
        "Hide the vanilla potion effect list while this HUD widget is enabled",
        true
    )

    init {
        addSetting(showDurationBar)
        addSetting(interceptVanillaPotion)
    }

    override fun resetPosition() {
        val screenH = ResolutionHelper.scaledHeight
        x = 12f
        y = (screenH - 140f).coerceAtLeast(10f)
    }

    private fun toRoman(amplifier: Int): String {
        return when (amplifier) {
            0 -> "I"
            1 -> "II"
            2 -> "III"
            3 -> "IV"
            4 -> "V"
            else -> "${amplifier + 1}"
        }
    }

    private data class PotionEntry(
        val name: String,
        val durationText: String,
        val progress: Float,
        val spriteId: Identifier?
    )

    private fun getEntries(isEditing: Boolean): List<PotionEntry> {
        val mc = Minecraft.getInstance()
        val player = mc.player
        val list = mutableListOf<PotionEntry>()
        val effects = player?.activeEffects ?: emptyList()

        for (effect in effects) {
            val effName = try {
                Component.translatable(effect.effect.value().descriptionId).string
            } catch (e: Throwable) { "Effect" }

            val ampStr = toRoman(effect.amplifier)
            val fullTitle = "$effName $ampStr"

            val durationTicks = effect.duration
            val isInfinite = effect.isInfiniteDuration
            val durText = if (isInfinite) {
                "**:**"
            } else {
                val sec = (durationTicks / 20).coerceAtLeast(0)
                String.format("%02d:%02d", sec / 60, sec % 60)
            }

            val prog = if (isInfinite) 1f else ((durationTicks % 2400) / 2400f).coerceIn(0.05f, 1f)
            val sprite = try { Hud.getMobEffectSprite(effect.effect) } catch (e: Throwable) { null }
            list.add(PotionEntry(fullTitle, durText, prog, sprite))
        }

        if (list.isEmpty() && isEditing) {
            list.add(PotionEntry("Speed II", "01:45", 0.75f, try { Hud.getMobEffectSprite(MobEffects.SPEED) } catch (e: Throwable) { null }))
            list.add(PotionEntry("Strength I", "00:32", 0.35f, try { Hud.getMobEffectSprite(MobEffects.STRENGTH) } catch (e: Throwable) { null }))
            list.add(PotionEntry("Fire Resistance I", "02:10", 0.90f, try { Hud.getMobEffectSprite(MobEffects.FIRE_RESISTANCE) } catch (e: Throwable) { null }))
        }

        return list
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return

        val entries = getEntries(isEditing)
        if (entries.isEmpty()) return

        val cardH = 26f
        val gap = 3.5f
        var curY = y

        for (entry in entries) {
            val sprite = entry.spriteId
            if (sprite != null) {
                val iconX = (x + 5f).toInt()
                val iconY = (curY + (cardH - 18f) * 0.5f - 0.5f).toInt()
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, iconX, iconY, 18, 18)
            }
            curY += cardH + gap
        }
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return

        val font: FontRenderer = RenderEngine.fontRenderer
        val activeList = getEntries(isEditing)

        if (activeList.isEmpty()) {
            width = 135f
            height = 20f
            if (isEditing) renderEditBoundingBox(partialTicks)
            return
        }

        val cardW = 145f
        val cardH = 26f
        val gap = 3.5f
        width = cardW
        height = activeList.size * cardH + (activeList.size - 1) * gap
        val radius = 4.0f

        val bgAlpha = HUDModule.bgOpacity.getFloat()
        val textShadow = HUDModule.textShadow.value

        var curY = y
        for (entry in activeList) {
            val centerY = curY + cardH * 0.5f - (if (showDurationBar.value) 1f else 0f)

            drawWidgetShadow(x, curY, cardW, cardH, radius, 0.45f, blur = 6f)

            val bgCol = NanoVGColor(0f, 0f, 0f, bgAlpha)
            val borderCol = NanoVGColor(1f, 1f, 1f, 0.08f)
            drawBackdrop(x, curY, cardW, cardH, radius, bgCol)
            NanoVGRenderer.drawRoundedOutline(x, curY, cardW, cardH, radius, borderCol, 0.75f)

            val nameX = x + 27f
            val maxNameW = cardW - 68f
            val trimmedName = font.trimWithEllipsis(entry.name, maxNameW, 10.5f)
            font.drawString(
                trimmedName,
                nameX,
                centerY - 5f,
                NanoVGColor.WHITE,
                10.5f,
                shadow = textShadow
            )

            font.drawString(
                entry.durationText,
                x + cardW - 8f,
                centerY - 4.5f,
                NanoVGColor(0.80f, 0.82f, 0.88f, 0.95f),
                9.5f,
                cn.reverie.render.font.TextAlignment.TOP_RIGHT,
                shadow = textShadow
            )

            if (showDurationBar.value) {
                val barH = 1.4f
                val barW = (cardW - 6f) * entry.progress

                NanoVGRenderer.drawRoundedRect(x + 3f, curY + cardH - 2.2f, cardW - 6f, barH, 0.7f, NanoVGColor(1f, 1f, 1f, 0.06f))

                if (barW > 0.5f) {
                    NanoVGRenderer.drawRoundedRect(x + 3f, curY + cardH - 2.2f, barW, barH, 0.7f, NanoVGColor(1f, 1f, 1f, 0.55f))
                }
            }

            curY += cardH + gap
        }

        if (isEditing) {
            renderEditBoundingBox(partialTicks)
        }
    }
}

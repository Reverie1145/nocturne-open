package cn.reverie.render.hud.impl

import cn.reverie.module.BooleanSetting
import cn.reverie.module.ModeSetting
import cn.reverie.module.combat.KillAura
import cn.reverie.module.misc.YuriGallery
import cn.reverie.render.art.AnimeArt
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudElement
import cn.reverie.render.hud.HudHorizontalAnchor
import cn.reverie.render.hud.HudVerticalAnchor
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.nanovg.NanoVGTransform
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.EntityHitResult
import java.util.Locale

class TargetHUDWidget : HudElement(
    name = "TargetHUD",
    defaultX = 400f,
    defaultY = 300f,
    width = 175f,
    height = 58f,
    defaultEnabled = true,
    horizontalAnchor = HudHorizontalAnchor.CENTER,
    verticalAnchor = HudVerticalAnchor.CENTER
) {
    val ghostBar = BooleanSetting("Ghost Bar", "Delayed trailing damage health bar", true)
    val showStatus = BooleanSetting("Show Status", "Display win/loss health comparison tag", true)
    val showArmor = BooleanSetting("Show Armor", "Display target equipment indicators", true)
    val healthStyle = ModeSetting("Health Text", "Health value format", "HP & %", listOf("HP & %", "HP Only", "Percent Only"))
    val animePortrait = cn.reverie.module.BooleanSetting("Anime Portrait", "Yuri Gallery character portrait instead of skin head", true)

    private val alphaAnim = FloatAnimation(0f, 0f, 320L, CubicBezier.EASE_OUT)
    private val healthAnim = FloatAnimation(20f, 20f, 320L, CubicBezier.EASE_OUT)
    private val trailAnim = FloatAnimation(20f, 20f, 520L, CubicBezier.EASE_OUT)

    private var currentTarget: LivingEntity? = null

    init {
        addSetting(ghostBar)
        addSetting(showStatus)
        addSetting(showArmor)
        addSetting(healthStyle)
        addSetting(animePortrait)
    }

    private fun portraitIdFor(target: LivingEntity?): String {
        val seed = target?.name?.string ?: "Nocturne"
        return AnimeArt.pickFor(AnimeArt.TARGET_PORTRAIT_POOL, seed)
    }

    private fun useAnimePortrait(target: LivingEntity?): Boolean {
        return YuriGallery.enabled && animePortrait.value &&
            AnimeArt.image(portraitIdFor(target)) != null
    }

    override fun resetPosition() {
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        x = ((screenW - width) * 0.5f).coerceAtLeast(10f)
        y = (screenH * 0.5f + 50f).coerceAtLeast(10f)
    }

    override fun onTick() {
        super.onTick()

        val mc = Minecraft.getInstance()
        val player = mc.player
        val screen = mc.gui.screen()
        val isEditing = screen is ChatScreen

        var foundTarget: LivingEntity? = null

        if (KillAura.enabled && KillAura.target != null) {
            foundTarget = KillAura.target
        }

        if (foundTarget == null && mc.hitResult is EntityHitResult) {
            val hitEntity = (mc.hitResult as EntityHitResult).entity
            if (hitEntity is LivingEntity && hitEntity != player) {
                foundTarget = hitEntity
            }
        }

        if (foundTarget == null && isEditing && player != null) {
            foundTarget = player
        }

        currentTarget = foundTarget

        if (foundTarget != null) {
            alphaAnim.animateTo(1f)
            val hp = foundTarget.health + foundTarget.absorptionAmount
            healthAnim.animateTo(hp)

            if (hp < trailAnim.value) {
                trailAnim.animateTo(hp)
            } else {
                trailAnim.setImmediate(hp)
            }
        } else {
            alphaAnim.animateTo(0f)
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return
        val mc = Minecraft.getInstance()
        val target = currentTarget ?: (if (isEditing) mc.player else null) ?: return
        if (alphaAnim.value <= 0.01f && !isEditing) return

        val headSize = 34
        val headX = (x + 6f).toInt()
        val headY = (y + 6f).toInt()
        val skin = (target as? AbstractClientPlayer)?.skin ?: mc.player?.skin
        if (skin != null && !useAnimePortrait(target)) {
            try {
                PlayerFaceExtractor.extractRenderState(graphics, skin, headX, headY, headSize)
            } catch (ignored: Throwable) {}
        }

        if (showArmor.value) {
            val slots = listOf(
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
            )
            val itemSize = 16
            val gap = 3
            var ax = (x + 47f).toInt()
            val ay = (y + 38f).toInt()

            for (slot in slots) {
                val item = target.getItemBySlot(slot)
                val stack = if (!item.isEmpty) item else if (isEditing) {
                    when (slot) {
                        EquipmentSlot.HEAD -> ItemStack(Items.DIAMOND_HELMET)
                        EquipmentSlot.CHEST -> ItemStack(Items.DIAMOND_CHESTPLATE)
                        EquipmentSlot.LEGS -> ItemStack(Items.DIAMOND_LEGGINGS)
                        EquipmentSlot.FEET -> ItemStack(Items.DIAMOND_BOOTS)
                        else -> ItemStack(Items.DIAMOND_SWORD)
                    }
                } else ItemStack.EMPTY

                if (!stack.isEmpty) {
                    graphics.item(stack, ax, ay)
                    graphics.itemDecorations(mc.font, stack, ax, ay)
                }
                ax += itemSize + gap
            }
        }
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        val mc = Minecraft.getInstance()
        val target = currentTarget ?: if (isEditing) mc.player else null
        val alpha = if (isEditing && currentTarget == null) 1f else alphaAnim.value

        if (!enabled && !isEditing) return
        if (alpha <= 0.01f && !isEditing) return
        if (target == null) return

        width = 180f
        height = if (showArmor.value) 60f else 46f
        val radius = 5.5f

        val font: FontRenderer = RenderEngine.fontRenderer
        val bgAlpha = (HUDModule.bgOpacity.getFloat() * alpha).coerceIn(0f, 1f)
        val textShadow = HUDModule.textShadow.value

        val scale = 0.95f + 0.05f * alpha
        val cx = x + width * 0.5f
        val cy = y + height * 0.5f

        val transform = NanoVGTransform(Draw2D.context)
        transform.withTransform {
            transform.translate(cx, cy)
            transform.scale(scale)
            transform.translate(-cx, -cy)

            drawWidgetShadow(x, y, width, height, radius, 0.45f * alpha)

            val bgCol = NanoVGColor(0f, 0f, 0f, bgAlpha)
            val borderCol = NanoVGColor(1f, 1f, 1f, 0.08f * alpha)
            drawBackdrop(x, y, width, height, radius, bgCol)
            NanoVGRenderer.drawRoundedOutline(x, y, width, height, radius, borderCol, 0.75f)

            val headSize = 34f
            val headX = x + 6f
            val headY = y + 6f
            if (useAnimePortrait(target)) {
                AnimeArt.drawCover(
                    portraitIdFor(target),
                    headX, headY, headSize, headSize,
                    alpha = alpha,
                    radius = 3.5f,
                    zoom = 1.1f,
                    biasY = -0.55f
                )
            }
            NanoVGRenderer.drawRoundedOutline(
                headX, headY, headSize, headSize, 3.5f,
                NanoVGColor(1f, 1f, 1f, 0.12f * alpha),
                0.75f
            )

            val targetTextBlur = cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(alpha, 5.5f)
            val nameX = headX + headSize + 7f
            val nameY = if (showArmor.value) y + 6.5f else y + 7f
            val targetName = target.name.string
            val dist = mc.player?.distanceTo(target) ?: 0f
            val distStr = String.format(Locale.US, "%.1fm", dist)

            val maxNameW = width - (nameX - x) - font.getStringWidth(distStr, 8.5f) - 10f
            val trimmedName = font.trimWithEllipsis(targetName, maxNameW, 10.5f)

            font.drawString(
                trimmedName,
                nameX, nameY,
                NanoVGColor.WHITE.multiplyAlpha(alpha),
                10.5f,
                TextAlignment.TOP_LEFT,
                shadow = textShadow,
                blur = targetTextBlur
            )
            font.drawString(
                distStr,
                x + width - 7f, nameY + 0.5f,
                NanoVGColor(0.80f, 0.82f, 0.90f, 0.80f * alpha),
                8.5f,
                TextAlignment.TOP_RIGHT,
                shadow = textShadow,
                blur = targetTextBlur
            )

            val midRowY = if (showArmor.value) y + 18.5f else y + 20.5f
            val midRowH = 9.5f

            if (showStatus.value) {
                val selfHp = (mc.player?.health ?: 20f) + (mc.player?.absorptionAmount ?: 0f)
                val targetHp = target.health + target.absorptionAmount
                val delta = selfHp - targetHp

                val statusText = when {
                    delta > 0.8f -> String.format(Locale.US, "+%.1f HP", delta)
                    delta < -0.8f -> String.format(Locale.US, "%.1f HP", delta)
                    else -> "EQUAL"
                }

                val statusW = font.getStringWidth(statusText, 7.5f) + 8f

                NanoVGRenderer.drawRoundedRect(
                    nameX, midRowY, statusW, midRowH, 2.0f,
                    NanoVGColor(1f, 1f, 1f, 0.06f * alpha)
                )
                NanoVGRenderer.drawRoundedOutline(
                    nameX, midRowY, statusW, midRowH, 2.0f,
                    NanoVGColor(1f, 1f, 1f, 0.10f * alpha),
                    0.75f
                )
                NanoVGRenderer.drawVerticallyCenteredString(
                    statusText,
                    nameX + statusW * 0.5f,
                    midRowY + midRowH * 0.5f,
                    NanoVGColor(0.90f, 0.92f, 0.98f, 0.95f * alpha),
                    7.5f,
                    shadow = textShadow,
                    blur = targetTextBlur
                )
            }

            val maxHp = target.maxHealth + target.absorptionAmount
            val hpRatio = (healthAnim.value / maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val trailRatio = (trailAnim.value / maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)

            val hpCur = String.format(Locale.US, "%.1f", target.health + target.absorptionAmount)
            val hpPercent = "${(hpRatio * 100).toInt()}%"
            val hpDisplay = when (healthStyle.value) {
                "HP Only" -> "$hpCur HP"
                "Percent Only" -> hpPercent
                else -> "$hpCur HP ($hpPercent)"
            }
            font.drawString(
                hpDisplay,
                x + width - 7f, midRowY + (midRowH - 8.0f) * 0.5f,
                NanoVGColor(0.85f, 0.88f, 0.95f, 0.90f * alpha),
                8.0f,
                TextAlignment.TOP_RIGHT,
                shadow = textShadow,
                blur = targetTextBlur
            )

            val barX = nameX
            val barY = if (showArmor.value) y + 30.0f else y + 33.5f
            val barW = width - (nameX - x) - 7f
            val barH = 4.0f
            val barRadius = 2.0f

            NanoVGRenderer.drawRoundedRect(
                barX, barY, barW, barH, barRadius,
                NanoVGColor(1f, 1f, 1f, 0.08f * alpha)
            )

            if (ghostBar.value && trailRatio > hpRatio) {
                val ghostW = (barW * trailRatio).coerceIn(0f, barW)
                val diffW = (trailRatio - hpRatio) * barW
                if (diffW > 1.2f) {
                    cn.reverie.render.animation.MotionBlurHelper.renderHorizontalMotionBlur(
                        barX + barW * hpRatio, barY, diffW, barH, barRadius,
                        -diffW * 0.45f,
                        NanoVGColor(1f, 1f, 1f, 0.35f * alpha),
                        maxBlur = 12f
                    )
                }
                NanoVGRenderer.drawRoundedRect(
                    barX, barY, ghostW, barH, barRadius,
                    NanoVGColor(1f, 1f, 1f, 0.25f * alpha)
                )
            }

            if (hpRatio > 0.005f) {
                val activeW = (barW * hpRatio).coerceAtLeast(2.5f)
                NanoVGRenderer.drawRoundedRect(
                    barX, barY, activeW, barH, barRadius,
                    NanoVGColor.WHITE.multiplyAlpha(alpha)
                )
            }

            if (showArmor.value) {
                val itemSize = 16f
                val gap = 3f
                var ax = x + 47f
                val ay = y + 38f
                for (slotIndex in 0 until 5) {
                    NanoVGRenderer.drawRoundedRect(ax, ay, itemSize, itemSize, 2.5f, NanoVGColor(1f, 1f, 1f, 0.035f * alpha))
                    NanoVGRenderer.drawRoundedOutline(ax, ay, itemSize, itemSize, 2.5f, NanoVGColor(1f, 1f, 1f, 0.06f * alpha), 0.75f)
                    ax += itemSize + gap
                }
            }
        }

        if (isEditing) {
            renderEditBoundingBox(partialTicks)
        }
    }
}

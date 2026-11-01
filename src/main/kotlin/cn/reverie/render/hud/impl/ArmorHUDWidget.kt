package cn.reverie.render.hud.impl

import cn.reverie.module.BooleanSetting
import cn.reverie.module.ModeSetting
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudElement
import cn.reverie.render.hud.HudHorizontalAnchor
import cn.reverie.render.hud.HudVerticalAnchor
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class ArmorHUDWidget : HudElement(
    name = "ArmorHUD",
    defaultX = 420f,
    defaultY = 430f,
    width = 160f,
    height = 28f,
    defaultEnabled = true,
    horizontalAnchor = HudHorizontalAnchor.CENTER,
    verticalAnchor = HudVerticalAnchor.BOTTOM
) {
    val orientation = ModeSetting("Orientation", "Layout arrangement", "Horizontal", listOf("Horizontal", "Vertical"))
    val showDurability = BooleanSetting("Durability Bar", "Display durability indicator track", true)
    val showPercentage = BooleanSetting("Percentage", "Display durability percentage text", false)
    val showOffhand = BooleanSetting("Show Offhand", "Display offhand item slot", true)

    init {
        addSetting(orientation)
        addSetting(showDurability)
        addSetting(showPercentage)
        addSetting(showOffhand)
    }

    override fun resetPosition() {
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        x = ((screenW - width) * 0.5f + 120f).coerceAtLeast(10f)
        y = (screenH - 55f).coerceAtLeast(10f)
    }

    private data class ItemSlotInfo(
        val slot: EquipmentSlot,
        val item: ItemStack,
        val previewItem: ItemStack,
        val hasItem: Boolean
    )

    private fun getSlots(): List<ItemSlotInfo> {
        val mc = Minecraft.getInstance()
        val player = mc.player
        val list = mutableListOf<ItemSlotInfo>()

        val eqSlots = listOf(
            EquipmentSlot.HEAD to ItemStack(Items.DIAMOND_HELMET),
            EquipmentSlot.CHEST to ItemStack(Items.DIAMOND_CHESTPLATE),
            EquipmentSlot.LEGS to ItemStack(Items.DIAMOND_LEGGINGS),
            EquipmentSlot.FEET to ItemStack(Items.DIAMOND_BOOTS),
            EquipmentSlot.MAINHAND to ItemStack(Items.DIAMOND_SWORD)
        )

        for ((slot, preview) in eqSlots) {
            val stack = player?.getItemBySlot(slot) ?: ItemStack.EMPTY
            list.add(ItemSlotInfo(slot, stack, preview, !stack.isEmpty))
        }

        if (showOffhand.value) {
            val offStack = player?.getItemBySlot(EquipmentSlot.OFFHAND) ?: ItemStack.EMPTY
            list.add(ItemSlotInfo(EquipmentSlot.OFFHAND, offStack, ItemStack(Items.SHIELD), !offStack.isEmpty))
        }

        return list
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return

        val mc = Minecraft.getInstance()
        val slots = getSlots()
        val isHoriz = orientation.value == "Horizontal"
        val cellSize = 24f
        val gap = 3.5f

        for (i in slots.indices) {
            val slot = slots[i]
            val cellX = if (isHoriz) x + i * (cellSize + gap) else x
            val cellY = if (isHoriz) y else y + i * (cellSize + gap)

            val renderStack = if (slot.hasItem) slot.item else if (isEditing) slot.previewItem else ItemStack.EMPTY
            if (!renderStack.isEmpty) {
                val itemX = (cellX + (cellSize - 16f) * 0.5f).toInt()
                val itemY = (cellY + 2f).toInt()
                graphics.item(renderStack, itemX, itemY)
                graphics.itemDecorations(mc.font, renderStack, itemX, itemY)
            }
        }
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return

        val slots = getSlots()
        val isHoriz = orientation.value == "Horizontal"
        val cellSize = 24f
        val gap = 3.5f
        val count = slots.size

        if (isHoriz) {
            width = count * cellSize + (count - 1) * gap
            height = cellSize + (if (showPercentage.value) 9f else 0f)
        } else {
            width = cellSize + (if (showPercentage.value) 28f else 0f)
            height = count * cellSize + (count - 1) * gap
        }

        val radius = 4.0f
        val bgAlpha = HUDModule.bgOpacity.getFloat()
        val textShadow = HUDModule.textShadow.value

        for (i in 0 until count) {
            val slot = slots[i]
            val cellX = if (isHoriz) x + i * (cellSize + gap) else x
            val cellY = if (isHoriz) y else y + i * (cellSize + gap)
            val hasItem = slot.hasItem || isEditing

            drawWidgetShadow(cellX, cellY, cellSize, cellSize, radius, 0.45f, blur = 6f)

            val bgCol = if (hasItem) NanoVGColor(0f, 0f, 0f, bgAlpha) else NanoVGColor(0f, 0f, 0f, bgAlpha * 0.4f)
            val borderCol = if (hasItem) NanoVGColor(1f, 1f, 1f, 0.08f) else NanoVGColor(1f, 1f, 1f, 0.04f)
            drawBackdrop(cellX, cellY, cellSize, cellSize, radius, bgCol)
            NanoVGRenderer.drawRoundedOutline(cellX, cellY, cellSize, cellSize, radius, borderCol, 0.75f)

            val renderStack = if (slot.hasItem) slot.item else if (isEditing) slot.previewItem else ItemStack.EMPTY
            if (!renderStack.isEmpty) {
                val isDamageable = renderStack.isDamageableItem || isEditing
                val durRatio = if (renderStack.isDamageableItem && renderStack.maxDamage > 0) {
                    1f - renderStack.damageValue.toFloat() / renderStack.maxDamage.toFloat()
                } else if (isEditing) 0.85f else 1.0f

                val durColor = when {
                    durRatio > 0.25f -> NanoVGColor(0.95f, 0.96f, 0.98f, 0.95f)
                    durRatio > 0.10f -> NanoVGColor(0.95f, 0.75f, 0.20f, 0.95f)
                    else -> NanoVGColor(0.95f, 0.35f, 0.35f, 0.95f)
                }

                if (showDurability.value && isDamageable) {
                    val barH = 1.4f
                    val barW = (cellSize - 6f) * durRatio.coerceIn(0f, 1f)

                    NanoVGRenderer.drawRoundedRect(cellX + 3f, cellY + cellSize - 2.4f, cellSize - 6f, barH, 0.7f, NanoVGColor(1f, 1f, 1f, 0.08f))

                    if (barW > 0.5f) {
                        NanoVGRenderer.drawRoundedRect(cellX + 3f, cellY + cellSize - 2.4f, barW, barH, 0.7f, durColor)
                    }
                }

                if (showPercentage.value) {
                    val pText = "${(durRatio * 100).toInt()}%"
                    val font: FontRenderer = RenderEngine.fontRenderer
                    if (isHoriz) {
                        font.drawCenteredString(pText, cellX + cellSize * 0.5f, cellY + cellSize + 4.5f, NanoVGColor(0.85f, 0.88f, 0.95f, 0.90f), 8.0f, shadow = textShadow)
                    } else {
                        font.drawString(pText, cellX + cellSize + 4f, cellY + cellSize * 0.5f, NanoVGColor(0.85f, 0.88f, 0.95f, 0.90f), 8.5f, cn.reverie.render.font.TextAlignment.MIDDLE_LEFT, shadow = textShadow)
                    }
                }
            }
        }

        if (isEditing) {
            renderEditBoundingBox(partialTicks)
        }
    }
}

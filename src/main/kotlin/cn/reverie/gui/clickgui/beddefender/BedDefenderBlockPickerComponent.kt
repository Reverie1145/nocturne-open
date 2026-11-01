package cn.reverie.gui.clickgui.beddefender
import cn.reverie.module.player.BedDefender
import cn.reverie.module.player.beddefender.BedDefenseBlocks
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.client.Minecraft
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.Block
import cn.reverie.gui.clickgui.setting.SettingComponent
import cn.reverie.gui.clickgui.setting.StringSettingComponent

/** Custom material input plus mouse selection from common blocks, the held item, and all nine hotbar slots. */
class BedDefenderBlockPickerComponent : SettingComponent(BedDefender.customBlock) {
    private data class Box(val x: Float, val y: Float, val width: Float, val height: Float) {
        fun contains(mx: Float, my: Float) = mx in x..(x + width) && my in y..(y + height)
    }

    private val input = StringSettingComponent(BedDefender.customBlock, 128, "minecraft:block_id")
    private val innerWidth get() = width - 12f
    private val presetColumns get() = if (width >= 220f) 3 else 2
    private val presetRows get() = (PRESETS.size + presetColumns - 1) / presetColumns
    private val hotbarColumns get() = if (width >= 240f) 9 else 3
    private val hotbarRows get() = 9 / hotbarColumns
    private val hotbarLabelY get() = 64f + presetRows * 24f
    private val hotbarY get() = hotbarLabelY + 13f
    override var height: Float
        get() = hotbarY + hotbarRows * 27f + 17f
        set(@Suppress("UNUSED_PARAMETER") value) {}

    private fun presetBox(index: Int): Box {
        val cellWidth = (innerWidth - (presetColumns - 1) * 4f) / presetColumns
        return Box(6f + index % presetColumns * (cellWidth + 4f), 62f + index / presetColumns * 24f, cellWidth, 20f)
    }

    private fun slotBox(index: Int): Box {
        val cellWidth = (innerWidth - (hotbarColumns - 1) * 3f) / hotbarColumns
        return Box(6f + index % hotbarColumns * (cellWidth + 3f), hotbarY + index / hotbarColumns * 27f, cellWidth, 23f)
    }

    private fun heldBox() = Box(width - 48f, 39f, 42f, 17f)

    private fun positionInput() {
        input.x = x
        input.y = y
        input.width = width
    }

    private fun heldBlock(): Block? {
        val player = Minecraft.getInstance().player ?: return null
        return listOf(player.mainHandItem, player.offhandItem).asSequence()
            .filterNot { it.isEmpty }.mapNotNull { (it.item as? BlockItem)?.block }
            .firstOrNull(BedDefenseBlocks::canUse)
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        positionInput()
        input.render(mouseX, mouseY, partialTicks)
        val selected = BedDefender.resolveCustomBlock()
        val valid = selected != null && BedDefenseBlocks.canUse(selected)
        val status = when {
            selected == null -> "Unknown block ID"
            !valid -> "Full, non-falling block only"
            else -> BedDefenseBlocks.displayName(selected)
        }
        val statusColor = if (valid) NanoVGColor(0.56f, 0.83f, 0.69f) else NanoVGColor(0.95f, 0.56f, 0.47f)
        text(status, x + 6f, y + 43f, width - 58f, statusColor, 6.8f)
        val mx = mouseX - x
        val my = mouseY - y
        val held = heldBlock()
        val heldBox = heldBox()
        button(heldBox, "Use held", false, heldBox.contains(mx, my), held != null)

        for ((index, preset) in PRESETS.withIndex()) {
            val box = presetBox(index)
            val block = BedDefenseBlocks.find(preset.second)
            button(box, preset.first, block != null && block === selected, box.contains(mx, my), block != null)
        }

        NanoVGRenderer.drawString("Pick from hotbar", x + 6f, y + hotbarLabelY, NanoVGColor(0.63f, 0.68f, 0.76f), 7.1f)
        val player = Minecraft.getInstance().player
        var hoveredName: String? = null
        var available = 0
        for (index in 0..8) {
            val box = slotBox(index)
            val stack = player?.inventory?.getItem(index)
            val block = stack?.takeUnless { it.isEmpty }?.let { (it.item as? BlockItem)?.block }
            val enabled = block != null && BedDefenseBlocks.canUse(block)
            val active = enabled && block === selected
            if (active) available += stack.count
            val hovered = box.contains(mx, my)
            if (hovered && block != null) hoveredName = "${index + 1}: ${BedDefenseBlocks.displayName(block)}${if (enabled) "" else " (unsupported)"}"
            button(box, "", active, hovered, enabled)
            NanoVGRenderer.drawString("${index + 1}", x + box.x + 3f, y + box.y + 3f, NanoVGColor(0.72f, 0.76f, 0.83f, if (enabled) 1f else 0.40f), 6f)
            if (enabled) {
                val rgb = BedDefenseBlocks.previewRgb(block)
                val tint = NanoVGColor(((rgb shr 16) and 255) / 255f, ((rgb shr 8) and 255) / 255f, (rgb and 255) / 255f)
                Draw2D.roundedRect(x + box.x + box.width * 0.5f - 4f, y + box.y + 5f, 8f, 8f, 1f, tint)
                NanoVGRenderer.drawRightString("${stack.count}", x + box.x + box.width - 3f, y + box.y + 14f, NanoVGColor(0.75f, 0.79f, 0.86f), 6.5f)
            } else {
                NanoVGRenderer.drawCenteredString("--", x + box.x + box.width * 0.5f, y + box.y + 12f, NanoVGColor(0.38f, 0.41f, 0.47f), 7f)
            }
        }
        val offhand = player?.offhandItem
        if (offhand != null && !offhand.isEmpty && (offhand.item as? BlockItem)?.block === selected) available += offhand.count
        val hint = hoveredName ?: when {
            player == null -> "Join a world to pick hotbar blocks"
            !valid -> "Enter an ID or choose a block above"
            available == 0 -> "Selected block missing from hotbar"
            else -> "$available available in hotbar/offhand"
        }
        text(hint, x + 6f, y + height - 11f, innerWidth, NanoVGColor(0.60f, 0.66f, 0.75f), 6.3f)
    }

    private fun text(value: String, x: Float, y: Float, maxWidth: Float, color: NanoVGColor, size: Float) {
        NanoVGRenderer.drawString(RenderEngine.fontRenderer.trimWithEllipsis(value, maxWidth.coerceAtLeast(1f), size), x, y, color, size)
    }

    private fun button(box: Box, label: String, selected: Boolean, hovered: Boolean, enabled: Boolean) {
        val accent = HUDModule.sampleColor(0f)
        val alpha = if (enabled) 1f else 0.35f
        Draw2D.roundedRect(x + box.x, y + box.y, box.width, box.height, 3f, when {
            selected -> accent.withAlpha(0.22f)
            hovered && enabled -> NanoVGColor(1f, 1f, 1f, 0.10f)
            else -> NanoVGColor(0f, 0f, 0f, 0.20f)
        })
        Draw2D.roundedOutline(x + box.x, y + box.y, box.width, box.height, 3f,
            if (selected) accent.withAlpha(0.70f) else NanoVGColor(1f, 1f, 1f, 0.09f * alpha), 0.7f)
        val trimmed = RenderEngine.fontRenderer.trimWithEllipsis(label, box.width - 6f, 7f)
        NanoVGRenderer.drawCenteredString(trimmed, x + box.x + box.width * 0.5f, y + box.y + box.height * 0.5f,
            NanoVGColor(0.82f, 0.86f, 0.92f, alpha), 7f)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false
        positionInput()
        if (input.mouseClicked(mouseX, mouseY, button)) return true
        val mx = mouseX - x
        val my = mouseY - y
        if (heldBox().contains(mx, my)) {
            heldBlock()?.let(BedDefender::selectCustomBlock)
            return true
        }
        for ((index, preset) in PRESETS.withIndex()) if (presetBox(index).contains(mx, my)) {
            BedDefenseBlocks.find(preset.second)?.let(BedDefender::selectCustomBlock)
            return true
        }
        for (index in 0..8) if (slotBox(index).contains(mx, my)) {
            val stack = Minecraft.getInstance().player?.inventory?.getItem(index)?.takeUnless { it.isEmpty }
            (stack?.item as? BlockItem)?.block?.let(BedDefender::selectCustomBlock)
            return true
        }
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int) = input.keyPressed(keyCode, scanCode, modifiers)
    override fun charTyped(codePoint: Char, modifiers: Int) = input.charTyped(codePoint, modifiers)

    companion object {
        private val PRESETS = listOf(
            "Wool" to "minecraft:white_wool", "End Stone" to "minecraft:end_stone",
            "Obsidian" to "minecraft:obsidian", "Planks" to "minecraft:oak_planks",
            "Glass" to "minecraft:glass", "Clay" to "minecraft:terracotta"
        )
    }
}

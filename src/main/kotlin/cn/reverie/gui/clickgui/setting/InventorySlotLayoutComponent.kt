package cn.reverie.gui.clickgui.setting
import cn.reverie.module.player.InvManager
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.RoundedRect
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGImage
import cn.reverie.render.nanovg.NanoVGPaint
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.texture.TextureLoader
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack

/** A compact, clickable hotbar layout editor for InvManager's nine target slots. */
class InventorySlotLayoutComponent : SettingComponent(InvManager.slot1) {

    private data class SlotBox(val index: Int, val x: Float, val y: Float, val width: Float, val height: Float)
    private data class OptionBox(val mode: String, val x: Float, val y: Float, val width: Float, val height: Float)

    private val slotBoxes = mutableListOf<SlotBox>()
    private val optionBoxes = mutableListOf<OptionBox>()
    private var openSlot: Int? = null
    private var activeSlot = 0

    init {
        height = CLOSED_HEIGHT
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        updateSlotLayout()
        updateOptionLayout()

        NanoVGRenderer.drawString(
            "Hotbar layout",
            x + 7f,
            y + 1f,
            NanoVGColor(0.72f, 0.74f, 0.80f, 0.92f),
            8.5f
        )

        for (box in slotBoxes) {
            val selected = box.index == activeSlot
            val hovered = mouseX in box.x..(box.x + box.width) && mouseY in box.y..(box.y + box.height)
            val mode = InvManager.slotSettings[box.index].value
            val background = when {
                openSlot == box.index -> NanoVGColor(1f, 1f, 1f, 0.15f)
                selected -> NanoVGColor(1f, 1f, 1f, 0.10f)
                hovered -> NanoVGColor(1f, 1f, 1f, 0.07f)
                else -> NanoVGColor(0f, 0f, 0f, 0.15f)
            }

            NanoVGRenderer.drawRoundedRect(box.x, box.y, box.width, box.height, 3f, background)
            NanoVGRenderer.drawRoundedOutline(
                box.x,
                box.y,
                box.width,
                box.height,
                3f,
                NanoVGColor(1f, 1f, 1f, if (selected || openSlot == box.index) 0.16f else 0.07f),
                0.7f
            )
            NanoVGRenderer.drawString(
                (box.index + 1).toString(),
                box.x + 3f,
                box.y + 2f,
                NanoVGColor(0.62f, 0.65f, 0.72f, 0.90f),
                5.8f
            )
            val iconSize = 13f
            val iconX = box.x + (box.width - iconSize) * 0.5f
            val iconY = box.y + 6.5f
            if (mode == "None") {
                NanoVGRenderer.drawRoundedRect(
                    box.x + box.width * 0.5f - 3f,
                    iconY + 5.5f,
                    6f,
                    1f,
                    0.5f,
                    NanoVGColor(0.48f, 0.51f, 0.58f, 0.90f)
                )
            } else if (!InvManagerSlotIcons.draw(mode, iconX, iconY, iconSize)) {
                NanoVGRenderer.drawCenteredString(
                    abbreviation(mode),
                    box.x + box.width * 0.5f,
                    box.y + box.height * 0.66f,
                    NanoVGColor(0.91f, 0.92f, 0.95f, 0.96f),
                    7f
                )
            }
        }

        val currentMode = InvManager.slotSettings[activeSlot].value
        NanoVGRenderer.drawString(
            "Slot ${activeSlot + 1}: $currentMode  ·  click to edit",
            x + 7f,
            y + GRID_BOTTOM + 4f,
            NanoVGColor(0.58f, 0.61f, 0.69f, 0.88f),
            6.8f
        )

        val selectedSlot = openSlot ?: return
        val menuX = x + 4f
        val menuY = y + POPUP_TOP
        val menuW = width - 8f
        val menuH = popupHeight()
        NanoVGRenderer.drawRoundedRect(menuX, menuY, menuW, menuH, 4f, NanoVGColor(0f, 0f, 0f, 0.22f))
        NanoVGRenderer.drawRoundedOutline(
            menuX,
            menuY,
            menuW,
            menuH,
            4f,
            NanoVGColor(1f, 1f, 1f, 0.09f),
            0.75f
        )
        NanoVGRenderer.drawString(
            "Slot ${selectedSlot + 1}  ·  ${InvManager.slotSettings[selectedSlot].value}",
            menuX + 6f,
            menuY + 4f,
            NanoVGColor(0.78f, 0.81f, 0.87f, 0.94f),
            7.3f
        )

        for (box in optionBoxes) {
            val hovered = mouseX in box.x..(box.x + box.width) && mouseY in box.y..(box.y + box.height)
            val selected = InvManager.slotSettings[selectedSlot].value == box.mode
            val color = when {
                selected -> NanoVGColor(1f, 1f, 1f, 0.13f)
                hovered -> NanoVGColor(1f, 1f, 1f, 0.07f)
                else -> NanoVGColor(0f, 0f, 0f, 0.12f)
            }
            NanoVGRenderer.drawRoundedRect(box.x, box.y, box.width, box.height, 2.5f, color)
            if (selected) {
                NanoVGRenderer.drawRoundedOutline(
                    box.x,
                    box.y,
                    box.width,
                    box.height,
                    2.5f,
                    NanoVGColor(1f, 1f, 1f, 0.12f),
                    0.65f
                )
            }
            val optionIconSize = 9f
            val iconDrawn = InvManagerSlotIcons.draw(box.mode, box.x + 2f, box.y + 1.5f, optionIconSize)
            val textX = box.x + if (iconDrawn) 13f else 4f
            val availableTextWidth = (box.width - (textX - box.x) - 3f).coerceAtLeast(4f)
            val label = RenderEngine.fontRenderer.trimWithEllipsis(box.mode, availableTextWidth, 6.7f)
            NanoVGRenderer.drawVerticallyCenteredString(
                label,
                textX,
                box.y + box.height * 0.5f,
                if (selected) NanoVGColor.WHITE else NanoVGColor(0.77f, 0.80f, 0.86f, 0.92f),
                6.7f
            )
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false

        val selectedSlot = openSlot
        if (selectedSlot != null) {
            val option = optionBoxes.firstOrNull {
                mouseX in it.x..(it.x + it.width) && mouseY in it.y..(it.y + it.height)
            }
            if (option != null) {
                InvManager.slotSettings[selectedSlot].value = option.mode
                activeSlot = selectedSlot
                setOpenSlot(null)
                return true
            }
        }

        val slot = slotBoxes.firstOrNull {
            mouseX in it.x..(it.x + it.width) && mouseY in it.y..(it.y + it.height)
        }
        if (slot != null) {
            activeSlot = slot.index
            setOpenSlot(if (openSlot == slot.index) null else slot.index)
            return true
        }

        if (openSlot != null && mouseX in x..(x + width) && mouseY in y..(y + height)) {
            setOpenSlot(null)
            return true
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (openSlot != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setOpenSlot(null)
            return true
        }
        return false
    }

    private fun updateSlotLayout() {
        slotBoxes.clear()
        val gap = 3f
        val cellHeight = 21f
        val gridX = x + 8f
        val gridY = y + GRID_TOP
        val totalWidth = width - 16f
        val cellWidth = (totalWidth - 2f * gap) / 3f

        for (index in 0 until 9) {
            val column = index % 3
            val row = index / 3
            slotBoxes.add(
                SlotBox(
                    index,
                    gridX + column * (cellWidth + gap),
                    gridY + row * (cellHeight + gap),
                    cellWidth,
                    cellHeight
                )
            )
        }
    }

    private fun updateOptionLayout() {
        optionBoxes.clear()
        if (openSlot == null) return

        val menuX = x + 4f
        val menuY = y + POPUP_TOP
        val columns = optionColumns()
        val innerGap = 3f
        val optionX = menuX + 5f
        val optionY = menuY + POPUP_HEADER_HEIGHT
        val optionW = (width - 8f - 10f - (columns - 1) * innerGap) / columns
        val optionH = 12f

        InvManager.slotOptions.forEachIndexed { index, mode ->
            val column = index % columns
            val row = index / columns
            optionBoxes.add(
                OptionBox(
                    mode,
                    optionX + column * (optionW + innerGap),
                    optionY + row * (optionH + OPTION_ROW_GAP),
                    optionW,
                    optionH
                )
            )
        }
    }

    private fun setOpenSlot(slot: Int?) {
        openSlot = slot
        height = if (slot == null) CLOSED_HEIGHT else POPUP_TOP + popupHeight()
    }

    private fun popupHeight(): Float {
        val rows = (InvManager.slotOptions.size + optionColumns() - 1) / optionColumns()
        val optionsHeight = rows * OPTION_HEIGHT + (rows - 1).coerceAtLeast(0) * OPTION_ROW_GAP
        return POPUP_HEADER_HEIGHT + optionsHeight + POPUP_BOTTOM_PAD
    }

    private fun optionColumns(): Int = if (width < SINGLE_COLUMN_MENU_WIDTH) 1 else 2

    private fun abbreviation(mode: String): String = when (mode) {
        "None" -> "--"
        "Pickaxe" -> "PK"
        "Shovel" -> "SH"
        "Block" -> "BL"
        "Water" -> "WA"
        "Lava" -> "LA"
        "Gapple" -> "GA"
        "Pearl" -> "PE"
        "Potion" -> "PO"
        "Projectile" -> "PR"
        "FishingRod" -> "FR"
        else -> mode.take(2).uppercase()
    }

    private companion object {
        const val GRID_TOP = 14f
        const val GRID_BOTTOM = GRID_TOP + 3f * 21f + 2f * 3f
        const val CLOSED_HEIGHT = GRID_BOTTOM + 19f
        const val POPUP_TOP = CLOSED_HEIGHT + 4f
        const val POPUP_HEADER_HEIGHT = 17f
        const val OPTION_HEIGHT = 12f
        const val OPTION_ROW_GAP = 2f
        const val POPUP_BOTTOM_PAD = 5f
        const val SINGLE_COLUMN_MENU_WIDTH = 114f
    }
}

private object InvManagerSlotIcons {
    private val textureByMode = mapOf(
        "Sword" to "diamond_sword",
        "Bow" to "bow",
        "Pickaxe" to "diamond_pickaxe",
        "Axe" to "diamond_axe",
        "Shovel" to "diamond_shovel",
        "Food" to "cooked_beef",
        "Block" to "cobblestone",
        "Water" to "water_bucket",
        "Lava" to "lava_bucket",
        "Gapple" to "golden_apple",
        "Pearl" to "ender_pearl",
        "Potion" to "potion",
        "Projectile" to "snowball",
        "FishingRod" to "fishing_rod",
        "TNT" to "tnt"
    )

    private val imageCache = HashMap<String, NanoVGImage?>()

    fun draw(mode: String, x: Float, y: Float, size: Float): Boolean {
        val texture = textureByMode[mode] ?: return false
        val image = load(texture) ?: return false
        if (!image.isValid) return false

        val context = RenderEngine.nanoVG.handle
        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.imagePattern(context, x, y, size, size, 0f, image.imageHandle, 1f, paint)
            RoundedRect.draw(context, x, y, size, size, CornerRadii(0f), paint)
        }
        return true
    }

    private fun load(texture: String): NanoVGImage? {
        if (imageCache.containsKey(texture)) return imageCache[texture]

        val image = try {
            val identifier = Identifier.withDefaultNamespace("textures/item/$texture.png")
            val resource = Minecraft.getInstance().resourceManager.getResource(identifier).orElse(null)
            val loaded = resource?.open()?.use { TextureLoader.loadFromStream(it, generateMipmaps = true, forceWhite = false) }
            if (loaded == null) {
                null
            } else {
                NanoVGImage.createFromGLTexture(RenderEngine.nanoVG.handle, loaded.textureId, loaded.width, loaded.height)
            }
        } catch (_: Throwable) {
            null
        }
        imageCache[texture] = image
        return image
    }
}

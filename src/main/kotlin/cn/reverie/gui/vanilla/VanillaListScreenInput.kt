package cn.reverie.gui.vanilla

import cn.reverie.gui.mainmenu.MainMenuScreen
import cn.reverie.gui.mainmenu.MenuChrome
import cn.reverie.render.ui.screens.AccountManagerScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

// Pointer coordinates reaching this file are already in the design canvas space (see MixinMouseHandler).

internal fun VanillaListScreenRenderer.joinEntry(entry: Any?) {
    if (isSingleplayer) {
        (entry as? WorldSelectionList.WorldListEntry)?.joinWorld()
    } else if (isMultiplayer) {
        (entry as? ServerSelectionList.Entry)?.join()
    }
}

private fun VanillaListScreenRenderer.press(btn: VanillaListScreenRenderer.InteractivePillButton) {
    btn.isPressed = true
    btn.onClick()
}

internal fun VanillaListScreenRenderer.handleMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
    val mx = event.x().toFloat()
    val my = event.y().toFloat()
    val btn = event.button()

    // While the departure motion plays the screen swallows input so nothing fires twice.
    if (closeStartMs > 0L) return true
    if (btn != 0) return false

    // Persistent chrome shared with the main menu: top navigation, page rail, account chip.
    val chip = MenuChrome.hitTest(mx, my)
    if (chip == MenuChrome.BRAND) {
        requestClose { screen.onClose() }
        return true
    }
    if (chip == MenuChrome.ACCOUNT) {
        requestClose { Minecraft.getInstance().gui.setScreen(AccountManagerScreen(screen)) }
        return true
    }

    if (backButton.contains(mx, my)) {
        press(backButton)
        return true
    }

    for (tb in toolbarButtons) {
        if (tb.contains(mx, my) && tb.isEnabled) {
            press(tb)
            return true
        }
    }

    for (db in dockButtons) {
        if (db.contains(mx, my) && db.isEnabled) {
            press(db)
            return true
        }
    }

    if (searchClearRect.width > 0f && searchClearRect.contains(mx, my) && searchQuery.isNotEmpty()) {
        searchQuery = ""
        updateFilterQuery("")
        return true
    }

    if (searchBoxRect.contains(mx, my)) {
        searchFocused = true
        cursorTimer = System.currentTimeMillis()
        return true
    } else {
        searchFocused = false
    }

    val thumb = scrollbarThumb
    if (thumb != null && thumb.copy(x = thumb.x - 5f, width = thumb.width + 10f).contains(mx, my)) {
        draggingScrollbar = true
        dragGrabOffset = my - thumb.y
        return true
    }

    val list = findList() ?: return false
    if (listRect.contains(mx, my)) {
        for (row in visibleRows) {
            if (!row.rect.contains(mx, my)) continue
            setListSelected(list, row.entry)

            val now = System.currentTimeMillis()
            val isQuick = row.quick.contains(mx, my)
            val isDouble = (now - lastClickTime < 320L && lastClickedEntry === row.entry) || isQuick
            if (isDouble) {
                joinEntry(row.entry)
                return true
            }
            lastClickTime = now
            lastClickedEntry = row.entry
            return true
        }
    }

    return false
}

internal fun VanillaListScreenRenderer.handleMouseReleased(event: MouseButtonEvent): Boolean {
    draggingScrollbar = false
    backButton.isPressed = false
    for (b in toolbarButtons) b.isPressed = false
    for (b in dockButtons) b.isPressed = false
    return false
}

internal fun VanillaListScreenRenderer.handleMouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
    if (!draggingScrollbar) return false
    if (scrollMax <= 0f) return false

    val trackTop = listRect.y + 14f
    val trackH = listRect.height - 28f
    val thumbH = (trackH * trackH / (trackH + scrollMax)).coerceIn(28f, trackH)
    val scrollable = trackH - thumbH
    if (scrollable <= 0f) return false

    val targetY = event.y().toFloat() - dragGrabOffset
    val frac = ((targetY - trackTop) / scrollable).coerceIn(0f, 1f)
    scrollTarget = frac * scrollMax
    return true
}

internal fun VanillaListScreenRenderer.handleMouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
    if (closeStartMs > 0L) return true
    if (scrollMax > 0f) {
        scrollTarget = (scrollTarget - scrollY.toFloat() * 78f).coerceIn(0f, scrollMax)
        return true
    }
    return false
}

internal fun VanillaListScreenRenderer.handleKeyPressed(event: KeyEvent): Boolean {
    val key = event.key
    if (closeStartMs > 0L) return true

    if (key == GLFW.GLFW_KEY_ESCAPE) {
        if (searchFocused) {
            searchFocused = false
            return true
        }
        backButton.onClick()
        return true
    }

    if (searchFocused) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            searchFocused = false
            return true
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && searchQuery.isNotEmpty()) {
            searchQuery = searchQuery.dropLast(1)
            updateFilterQuery(searchQuery)
            return true
        }
        if (key == GLFW.GLFW_KEY_V && event.modifiers and GLFW.GLFW_MOD_CONTROL != 0) {
            val clip = Minecraft.getInstance().keyboardHandler.clipboard
            if (clip.isNotEmpty() && searchQuery.length < 40) {
                searchQuery = (searchQuery + clip.replace("\n", "").replace("\r", "")).take(40)
                updateFilterQuery(searchQuery)
            }
            return true
        }
    } else if (key == GLFW.GLFW_KEY_F && event.modifiers and GLFW.GLFW_MOD_CONTROL != 0) {
        searchFocused = true
        cursorTimer = System.currentTimeMillis()
        return true
    }

    if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
        val selected = findList()?.selected
        if (selected != null) {
            joinEntry(selected)
            return true
        }
    }

    if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
        val list = findList() ?: return false
        val children = filtered
        if (children.isEmpty()) return false
        val selectedEntry: Any? = list.selected
        val curIndex = children.indexOf(selectedEntry)
        val delta = if (key == GLFW.GLFW_KEY_UP) -1 else 1
        val nextIndex = if (curIndex < 0) 0 else (curIndex + delta).coerceIn(0, children.size - 1)
        setListSelected(list, children[nextIndex])
        ensureSelectedVisible()
        return true
    }

    return false
}

internal fun VanillaListScreenRenderer.handleCharTyped(event: CharacterEvent): Boolean {
    if (!searchFocused) return false
    val codepoint = event.codepoint
    if (codepoint <= 0) return false
    val char = codepoint.toChar()
    if (char.isISOControl()) return false

    if (searchQuery.length < 40) {
        searchQuery += char
        updateFilterQuery(searchQuery)
        return true
    }
    return false
}

internal fun VanillaListScreenRenderer.updateFilterQuery(query: String) {
    if (isSingleplayer) {
        val list = findList() as? WorldSelectionList
        list?.updateFilter(query)
    }
    scrollTarget = 0f
}

internal fun VanillaListScreenRenderer.findList(): ObjectSelectionList<*>? {
    for (child in screen.children()) {
        if (child is ObjectSelectionList<*>) return child
    }
    return null
}

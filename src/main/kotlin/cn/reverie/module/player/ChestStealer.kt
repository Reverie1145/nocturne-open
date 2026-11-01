package cn.reverie.module.player

import cn.reverie.mixin.gui.AbstractContainerScreenAccessor
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.util.player.HumanClicker
import cn.reverie.util.player.InvItemHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.hypot

object ChestStealer : Module(
    name = "ChestStealer",
    description = "Rapidly and realistically loots chests with simulated human clicks",
    category = Category.PLAYER
) {
    val speedPreset = ModeSetting("Speed Preset", "Speed profile", "Blatant (0ms)", listOf("Blatant (0ms)", "Fast Legit", "Custom"))
    val mode = ModeSetting("Mode", "Loot filter mode", "Smart", listOf("Smart", "All"))
    val order = ModeSetting("Order", "Slot visit sequence", "Nearest", listOf("Nearest", "Value", "Top-Down", "Random"))

    val startDelay = NumberSetting("Start Delay", "Delay after opening chest in ms", 0.0, 0.0, 400.0, 5.0, { speedPreset.isMode("Custom") })
    val clickDelay = NumberSetting("Click Delay", "Base ms between clicks", 0.0, 0.0, 200.0, 5.0, { speedPreset.isMode("Custom") })
    val randomDelay = NumberSetting("Random Delay", "Random extra delay in ms", 0.0, 0.0, 60.0, 5.0, { speedPreset.isMode("Custom") })
    val holdTime = NumberSetting("Hold Time", "Click hold duration in ms", 0.0, 0.0, 60.0, 1.0, { speedPreset.isMode("Custom") })
    val moveCursor = BooleanSetting("Move Cursor", "Simulate smooth cursor trajectory", false)
    val moveSpeed = NumberSetting("Move Speed", "Cursor gliding speed multiplier", 3.0, 0.5, 5.0, 0.1, { moveCursor.value })
    val syncRealMouse = BooleanSetting("Physical Mouse", "Move OS mouse cursor", false, { moveCursor.value })
    val titleCheck = BooleanSetting("Title Check", "Ignore menu and shop chests", true)
    val autoClose = BooleanSetting("Auto Close", "Close chest when finished", true)
    val closeDelay = NumberSetting("Close Delay", "Delay before closing chest in ms", 0.0, 0.0, 400.0, 5.0, { autoClose.value })

    private val mc = Minecraft.getInstance()

    private var lastMenu: ChestMenu? = null
    private var openTime: Long = 0L
    private var nextClickTime: Long = 0L
    private var allDoneTime: Long = 0L

    private val blacklistKeywords = listOf(
        "select", "menu", "shop", "teleport", "vote", "reward",
        "kit", "game", "lobby", "profile", "play", "compass",
        "setting", "cosmetic", "selector", "duel", "bedwars",
        "skywars", "quest", "bank", "collect", "upgrade", "profile",
        "workbench", "crafting"
    )

    override fun onDisable() {
        HumanClicker.reset()
        reset()
    }

    private fun reset() {
        lastMenu = null
        openTime = 0L
        nextClickTime = 0L
        allDoneTime = 0L
    }

    private fun getEffectiveStartDelay(): Long {
        return when (speedPreset.value) {
            "Blatant (0ms)" -> 0L
            "Fast Legit" -> 40L
            else -> startDelay.value.toLong()
        }
    }

    private fun getEffectiveClickDelay(): Long {
        return when (speedPreset.value) {
            "Blatant (0ms)" -> 0L
            "Fast Legit" -> 25L
            else -> clickDelay.value.toLong()
        }
    }

    private fun getEffectiveRandomDelay(): Long {
        return when (speedPreset.value) {
            "Blatant (0ms)" -> 0L
            "Fast Legit" -> 8L
            else -> randomDelay.value.toLong()
        }
    }

    private fun getEffectiveHoldTime(): Long {
        return when (speedPreset.value) {
            "Blatant (0ms)" -> 0L
            "Fast Legit" -> 10L
            else -> holdTime.value.toLong()
        }
    }

    private fun getEffectiveMoveCursor(): Boolean {
        return when (speedPreset.value) {
            "Blatant (0ms)" -> false
            "Fast Legit" -> true
            else -> moveCursor.value
        }
    }

    private fun getEffectiveMoveSpeed(): Double {
        return when (speedPreset.value) {
            "Blatant (0ms)" -> 5.0
            "Fast Legit" -> 2.5
            else -> moveSpeed.value
        }
    }

    private fun getEffectiveCloseDelay(): Long {
        return when (speedPreset.value) {
            "Blatant (0ms)" -> 0L
            "Fast Legit" -> 50L
            else -> closeDelay.value.toLong()
        }
    }

    fun onUpdate() {
        if (!enabled) return

        val screen = mc.gui.screen() as? AbstractContainerScreen<*>
        if (screen == null) {
            reset()
            return
        }

        val menu = screen.menu as? ChestMenu
        if (menu == null) {
            reset()
            return
        }

        val now = System.currentTimeMillis()

        if (menu != lastMenu) {
            lastMenu = menu
            openTime = now
            val rng = ThreadLocalRandom.current()
            val rnd = getEffectiveRandomDelay()
            val extra = if (rnd > 0) rng.nextLong(0, rnd + 1) else 0L
            nextClickTime = now + getEffectiveStartDelay() + extra
            allDoneTime = 0L
            HumanClicker.reset()
            HumanClicker.syncCursorFromMc()
        }

        if (titleCheck.value) {
            val title = screen.title.string.lowercase()
            for (keyword in blacklistKeywords) {
                if (title.contains(keyword)) return
            }
        }

        if (now < nextClickTime) return

        if (HumanClicker.isBusy) return

        val chestSlotsCount = menu.rowCount * 9
        val targetSlots = mutableListOf<Slot>()

        for (i in 0 until chestSlotsCount) {
            val slot = menu.getSlot(i)
            val stack = slot.item
            if (stack.isEmpty) continue

            if (mode.isMode("Smart")) {
                if (InvItemHelper.isTrashItem(stack)) continue
            }
            targetSlots.add(slot)
        }

        val playerSlotsStart = chestSlotsCount
        val playerSlotsEnd = menu.slots.size
        var hasFreePlayerSlot = false
        for (i in playerSlotsStart until playerSlotsEnd) {
            val pSlot = menu.getSlot(i)
            if (pSlot.item.isEmpty) {
                hasFreePlayerSlot = true
                break
            }
        }

        if (targetSlots.isEmpty() || !hasFreePlayerSlot) {

            if (autoClose.value) {
                val closeDel = getEffectiveCloseDelay()
                if (closeDel <= 0L) {
                    mc.player?.closeContainer()
                    reset()
                } else {
                    if (allDoneTime == 0L) {
                        allDoneTime = now
                    } else if (now - allDoneTime >= closeDel) {
                        mc.player?.closeContainer()
                        reset()
                    }
                }
            }
            return
        }

        allDoneTime = 0L

        val clickDel = getEffectiveClickDelay()
        val moveCur = getEffectiveMoveCursor()

        if (clickDel <= 0L && !moveCur) {
            val screenAccessor = screen as? AbstractContainerScreenAccessor
            for (slot in targetSlots) {
                var free = false
                for (i in playerSlotsStart until playerSlotsEnd) {
                    if (menu.getSlot(i).item.isEmpty) {
                        free = true
                        break
                    }
                }
                if (!free) break
                screenAccessor?.nocturneInvokeSlotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE)
            }
            if (autoClose.value) {
                val closeDel = getEffectiveCloseDelay()
                if (closeDel <= 0L) {
                    mc.player?.closeContainer()
                    reset()
                } else {
                    allDoneTime = now
                }
            }
            return
        }

        val screenAccessor = screen as? AbstractContainerScreenAccessor
        val left = screenAccessor?.nocturneGetLeftPos() ?: 0
        val top = screenAccessor?.nocturneGetTopPos() ?: 0

        when (order.value) {
            "Nearest" -> {
                targetSlots.sortBy { slot ->
                    val sx = left + slot.x + 8.0
                    val sy = top + slot.y + 8.0
                    hypot(sx - HumanClicker.curX, sy - HumanClicker.curY)
                }
            }
            "Value" -> {
                targetSlots.sortByDescending { slot ->
                    val stack = slot.item
                    val armor = InvItemHelper.getArmorScore(stack)
                    val sword = InvItemHelper.getSwordScore(stack)
                    val food = InvItemHelper.getFoodScore(stack)
                    maxOf(armor, sword, food, 0f)
                }
            }
            "Top-Down" -> {
                targetSlots.sortBy { it.index }
            }
            "Random" -> {
                targetSlots.shuffle()
            }
        }

        val targetSlot = targetSlots.firstOrNull() ?: return

        HumanClicker.schedule(
            task = HumanClicker.ClickTask(
                slot = targetSlot,
                actionType = HumanClicker.ActionType.SHIFT_CLICK,
                onCompleted = {
                    val rng = ThreadLocalRandom.current()
                    val rnd = getEffectiveRandomDelay()
                    val extra = if (rnd > 0) rng.nextLong(0, rnd + 1) else 0L
                    nextClickTime = System.currentTimeMillis() + clickDel + extra
                }
            ),
            enableCursorMove = moveCur,
            syncReal = syncRealMouse.value,
            speed = getEffectiveMoveSpeed(),
            holdMs = getEffectiveHoldTime()
        )
    }
}

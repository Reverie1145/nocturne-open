package cn.reverie.module.player

import cn.reverie.module.player.invmanager.*

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.combat.KillAura
import cn.reverie.module.misc.Disabler
import cn.reverie.module.world.Scaffold
import cn.reverie.util.misc.TimerUtil
import cn.reverie.util.player.InvItemHelper
import cn.reverie.util.player.InvItemHelper.ArmorType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.AbstractFurnaceMenu
import net.minecraft.world.inventory.BrewingStandMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.*
object InvManager : Module(
    name = "InvManager",
    description = "Automatically manages and sorts inventory",
    category = Category.PLAYER
) {

    val sort = BooleanSetting("SortInventory", "Sort inventory into hotbar", true)

    internal val slotOptions = listOf(
        "None", "Sword", "Bow", "Pickaxe", "Axe", "Shovel", "Food", "Block", "Water", "Lava",
        "Gapple", "Pearl", "Potion", "Projectile", "FishingRod", "TNT"
    )

    val slot1 = ModeSetting("Slot1", "Item type for hotbar slot 1", "None", slotOptions, { sort.value })
    val slot2 = ModeSetting("Slot2", "Item type for hotbar slot 2", "None", slotOptions, { sort.value })
    val slot3 = ModeSetting("Slot3", "Item type for hotbar slot 3", "None", slotOptions, { sort.value })
    val slot4 = ModeSetting("Slot4", "Item type for hotbar slot 4", "None", slotOptions, { sort.value })
    val slot5 = ModeSetting("Slot5", "Item type for hotbar slot 5", "None", slotOptions, { sort.value })
    val slot6 = ModeSetting("Slot6", "Item type for hotbar slot 6", "None", slotOptions, { sort.value })
    val slot7 = ModeSetting("Slot7", "Item type for hotbar slot 7", "None", slotOptions, { sort.value })
    val slot8 = ModeSetting("Slot8", "Item type for hotbar slot 8", "None", slotOptions, { sort.value })
    val slot9 = ModeSetting("Slot9", "Item type for hotbar slot 9", "None", slotOptions, { sort.value })

    val sortDelay = NumberSetting("SortDelay", "Delay between sorting actions in ms", 0.0, 0.0, 500.0, 50.0, { sort.value })
    val throwUseless = BooleanSetting("ThrowUselessItems", "Throw useless items", true)
    val throwDelay = NumberSetting("ThrowDelay", "Delay between throw actions in ms", 0.0, 0.0, 500.0, 50.0, { throwUseless.value })
    val autoArmor = BooleanSetting("AutoArmor", "Equip best armor automatically", true)
    val autoArmorDelay = NumberSetting("AutoArmorDelay", "Delay between armor actions in ms", 0.0, 0.0, 500.0, 50.0, { autoArmor.value })
    val randomiseManage = BooleanSetting("Randomise", "Randomise slot order", false)
    val openInv = BooleanSetting("OpenInventory", "Only sort when inventory screen is open", false)
    val lobbyCheck = BooleanSetting("LobbyCheck", "Do not manage inventory in lobby or spectator", true)

    internal val slotSettings = arrayOf(slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9)

    internal val slots = mutableListOf<Int>()
    val sortTimer = TimerUtil()
    val throwTimer = TimerUtil()
    val autoArmorTimer = TimerUtil()
    var randomised: Boolean = false
    var action: Boolean = false
    /** True while automatic inventory actions are pending or cooling down. */
    var cleaning: Boolean = false
        private set
    var coolDown: Int = 0

    internal val mc: Minecraft get() = Minecraft.getInstance()

    override fun onEnable() {
        action = false
        cleaning = false
        coolDown = 0
        randomised = false
        slots.clear()
        sortTimer.reset()
        throwTimer.reset()
        autoArmorTimer.reset()
    }

    override fun onDisable() {
        action = false
        cleaning = false
        coolDown = 0
        randomised = false
        slots.clear()
    }

    fun onUpdate() {
        if (!enabled) {
            cleaning = false
            return
        }

        val player = mc.player ?: run { cleaning = false; return }
        if (mc.level == null || mc.gameMode == null) { cleaning = false; return }

        val screen = mc.gui.screen()
        if (screen != null && screen !is InventoryScreen) {
            cleaning = false
            return
        }

        if (player.isUsingItem || Scaffold.enabled || KillAura.target != null ||
            inScreenHandler(player.containerMenu) || Disabler.isInventoryMovePausedForChest()
        ) { cleaning = false; return }

        if (lobbyCheck.value && InvItemHelper.isInLobbyOrSpectator()) {
            cleaning = false
            return
        }

        if (coolDown > 0) {
            coolDown--
            return
        }

        val inInvScreen = screen is InventoryScreen
        val canRun = if (openInv.value) inInvScreen else (screen == null || inInvScreen)
        if (!canRun) {
            randomised = false
            cleaning = false
            return
        }

        if (!randomised || !randomiseManage.value || slots.size != 36) {
            slots.clear()
            for (slot in 0..35) {
                slots.add(slot)
            }
            if (randomiseManage.value) {
                slots.shuffle()
            }
            randomised = true
        }

        val needInventoryAction = (autoArmor.value && undressed()) ||
                (throwUseless.value && this.hasUselessItemImpl()) ||
                (sort.value && this.unsortedImpl())

        if (!needInventoryAction) {
            cleaning = false
            return
        }
        cleaning = true

        if (autoArmor.value) {
            if (autoWear()) {
                if (undressed()) return
            }
        }

        if (throwUseless.value) {
            if (this.autoThrowUselessImpl()) {
                if (this.hasUselessItemImpl()) return
            }
        }

        if (sort.value) {
            if (this.autoSortImpl()) {
                if (this.unsortedImpl()) return
            }
        }

        coolDown = if (autoArmorDelay.value <= 0.0 && throwDelay.value <= 0.0 && sortDelay.value <= 0.0) 0 else 2
    }

    fun autoWear(): Boolean {
        val player = mc.player ?: return false
        val delay = autoArmorDelay.value.toLong()
        var actionTaken = false

        for (slotID in 0..35) {
            val currentSlot = if (slotID < slots.size) slots[slotID] else slotID
            if (currentSlot !in 0..35) continue

            val itemStack = player.inventory.getItem(currentSlot)
            if (itemStack.isEmpty) continue

            val eqType = InvItemHelper.getEquipmentType(itemStack) ?: continue

            val (invSlot, menuSlot) = when (eqType) {
                ArmorType.HELMET -> Pair(InvItemHelper.HELMET_SLOT_INVENTORY, InvItemHelper.HELMET_SLOT_INTERACTION_MANAGER)
                ArmorType.CHESTPLATE -> Pair(InvItemHelper.CHEST_PLATE_SLOT_INVENTORY, InvItemHelper.CHEST_PLATE_SLOT_INTERACTION_MANAGER)
                ArmorType.LEGGINGS -> Pair(InvItemHelper.LEGGINGS_SLOT_INVENTORY, InvItemHelper.LEGGINGS_SLOT_INTERACTION_MANAGER)
                ArmorType.BOOTS -> Pair(InvItemHelper.BOOTS_SLOT_INVENTORY, InvItemHelper.BOOTS_SLOT_INTERACTION_MANAGER)
            }

            if (wearArmorPiece(currentSlot, itemStack, eqType, invSlot, menuSlot, delay)) {
                actionTaken = true
                if (delay > 0L) return true
            }
        }
        return actionTaken
    }

    private fun wearArmorPiece(
        currentSlot: Int,
        itemStack: ItemStack,
        armorType: ArmorType,
        invSlot: Int,
        menuSlot: Int,
        delay: Long
    ): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        val syncId = player.containerMenu.containerId

        val bestArmorScore = InvItemHelper.getBestArmorScore(armorType)
        val armorScore = InvItemHelper.getProtection(itemStack)
        val isBestArmor = armorScore > 0f && armorScore >= bestArmorScore - 0.001f

        if (isBestArmor) {
            val equipped = player.inventory.getItem(invSlot)
            if (equipped.isEmpty) {
                if (autoArmorTimer.hasTimeElapsed(delay) || delay == 0L) {
                    val trueID = if (currentSlot <= 8) currentSlot + 36 else currentSlot
                    gameMode.handleContainerInput(syncId, trueID, 0, ContainerInput.QUICK_MOVE, player)
                    autoArmorTimer.reset()
                    return true
                }
            } else {
                val equippedScore = InvItemHelper.getProtection(equipped)

                if (equippedScore < bestArmorScore - 0.001f) {
                    if (autoArmorTimer.hasTimeElapsed(delay) || delay == 0L) {
                        gameMode.handleContainerInput(syncId, menuSlot, 0, ContainerInput.THROW, player)
                        autoArmorTimer.reset()
                        return true
                    }
                }
            }
        }
        return false
    }

    fun undressed(): Boolean {
        val player = mc.player ?: return false
        val inv = player.inventory

        for (slot in 0..35) {
            val itemStack = inv.getItem(slot)
            if (itemStack.isEmpty) continue

            val type = InvItemHelper.getEquipmentType(itemStack) ?: continue
            when (type) {
                ArmorType.HELMET -> if (inv.getItem(InvItemHelper.HELMET_SLOT_INVENTORY).isEmpty) return true
                ArmorType.CHESTPLATE -> if (inv.getItem(InvItemHelper.CHEST_PLATE_SLOT_INVENTORY).isEmpty) return true
                ArmorType.LEGGINGS -> if (inv.getItem(InvItemHelper.LEGGINGS_SLOT_INVENTORY).isEmpty) return true
                ArmorType.BOOTS -> if (inv.getItem(InvItemHelper.BOOTS_SLOT_INVENTORY).isEmpty) return true
            }
        }

        val helmet = inv.getItem(InvItemHelper.HELMET_SLOT_INVENTORY)
        val bestHelmet = InvItemHelper.getBestArmorScore(ArmorType.HELMET)
        if (bestHelmet > 0f && InvItemHelper.getProtection(helmet) < bestHelmet - 0.001f) return true

        val chest = inv.getItem(InvItemHelper.CHEST_PLATE_SLOT_INVENTORY)
        val bestChest = InvItemHelper.getBestArmorScore(ArmorType.CHESTPLATE)
        if (bestChest > 0f && InvItemHelper.getProtection(chest) < bestChest - 0.001f) return true

        val leg = inv.getItem(InvItemHelper.LEGGINGS_SLOT_INVENTORY)
        val bestLeg = InvItemHelper.getBestArmorScore(ArmorType.LEGGINGS)
        if (bestLeg > 0f && InvItemHelper.getProtection(leg) < bestLeg - 0.001f) return true

        val boots = inv.getItem(InvItemHelper.BOOTS_SLOT_INVENTORY)
        val bestBoots = InvItemHelper.getBestArmorScore(ArmorType.BOOTS)
        if (bestBoots > 0f && InvItemHelper.getProtection(boots) < bestBoots - 0.001f) return true

        return false
    }

    fun getSlotWithMostBlock(selectSlot: Int): Int {
        val player = mc.player ?: return -1
        return InventorySlotSelector.findMost(player, selectSlot, slotSettings.map { it.value }, "Block") {
            it.item is BlockItem && InvItemHelper.isValidBlock(it)
        }
    }

    fun getSlotWithMostFood(selectSlot: Int): Int = findMost(selectSlot, "Food", InvItemHelper::isFood)
    fun getSlotWithMostGapple(selectSlot: Int): Int = findMost(selectSlot, "Gapple") { it.item == Items.GOLDEN_APPLE }
    fun getSlotWithMostProjectile(selectSlot: Int): Int = findMost(selectSlot, "Projectile") {
        it.item == Items.EGG || it.item == Items.SNOWBALL
    }
    fun getSlotWithMostPearl(selectSlot: Int): Int = findMost(selectSlot, "Pearl") { it.item == Items.ENDER_PEARL }
    fun getSlotWithMostTNT(selectSlot: Int): Int = findMost(selectSlot, "TNT") { it.item == Items.TNT }

    private fun findMost(selectSlot: Int, mode: String, matches: (ItemStack) -> Boolean): Int {
        val player = mc.player ?: return -1
        return InventorySlotSelector.findMost(player, selectSlot, slotSettings.map { it.value }, mode, matches)
    }
    fun inScreenHandler(screenHandler: AbstractContainerMenu?): Boolean {
        if (screenHandler == null) return false
        return screenHandler is ChestMenu ||
                screenHandler is AbstractFurnaceMenu ||
                screenHandler is BrewingStandMenu
    }

}

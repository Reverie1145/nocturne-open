package cn.reverie.module.player.invmanager

import cn.reverie.module.player.InvManager

import cn.reverie.util.player.InvItemHelper
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.*
    fun InvManager.autoThrowUselessImpl(): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        val delay = throwDelay.value.toLong()
        val syncId = player.containerMenu.containerId
        val allItems = InvItemHelper.getAllItems()
        var thrownAny = false

        val bestSword = InvItemHelper.getBestSword(allItems)
        val bestBow = InvItemHelper.getBestPowerBow(allItems)
        val bestPick = InvItemHelper.getBestPickaxe(allItems)
        val bestAxe = InvItemHelper.getBestAxe(allItems)
        val bestShovel = InvItemHelper.getBestShovel(allItems)

        for (slotID in 0..35) {
            val currentSlot = if (slotID < slots.size) slots[slotID] else slotID
            if (currentSlot !in 0..35) continue

            val itemStack = player.inventory.getItem(currentSlot)
            if (itemStack.isEmpty) continue

            var shouldThrow = false

            if (!InvItemHelper.isUsefulItem(itemStack)) {
                shouldThrow = true
            } else if (InvItemHelper.isBow(itemStack)) {
                if (itemStack !== bestBow || InvItemHelper.isDuplicateBestBow(itemStack, allItems)) shouldThrow = true
            } else if (InvItemHelper.isSword(itemStack)) {
                if (itemStack !== bestSword || InvItemHelper.isDuplicateBestSword(itemStack, allItems)) shouldThrow = true
            } else if (InvItemHelper.isPickaxe(itemStack)) {
                if (itemStack !== bestPick || InvItemHelper.isDuplicateBestPickaxe(itemStack, allItems)) shouldThrow = true
            } else if (InvItemHelper.isAxe(itemStack)) {
                if (itemStack !== bestAxe || InvItemHelper.isDuplicateBestAxe(itemStack, allItems)) shouldThrow = true
            } else if (InvItemHelper.isShovel(itemStack)) {
                if (itemStack !== bestShovel || InvItemHelper.isDuplicateBestShovel(itemStack, allItems)) shouldThrow = true
            } else {
                val eqType = InvItemHelper.getEquipmentType(itemStack)
                if (eqType != null) {
                    val bestArmorScore = InvItemHelper.getBestArmorScore(eqType)
                    val armorScore = InvItemHelper.getProtection(itemStack)
                    if (armorScore < bestArmorScore - 0.001f || InvItemHelper.isDuplicateBestArmor(itemStack)) {
                        shouldThrow = true
                    }
                }
            }

            if (shouldThrow) {
                if (throwTimer.hasTimeElapsed(delay) || delay == 0L) {
                    val trueID = if (currentSlot <= 8) currentSlot + 36 else currentSlot
                    gameMode.handleContainerInput(syncId, trueID, 1, ContainerInput.THROW, player)
                    throwTimer.reset()
                    thrownAny = true
                    if (delay > 0L) return true
                }
            }
        }
        return thrownAny
    }

    fun InvManager.hasUselessItemImpl(): Boolean {
        val player = mc.player ?: return false
        val allItems = InvItemHelper.getAllItems()
        val bestSword = InvItemHelper.getBestSword(allItems)
        val bestBow = InvItemHelper.getBestPowerBow(allItems)
        val bestPick = InvItemHelper.getBestPickaxe(allItems)
        val bestAxe = InvItemHelper.getBestAxe(allItems)
        val bestShovel = InvItemHelper.getBestShovel(allItems)

        for (slotID in 0..35) {
            val currentSlot = if (slotID < slots.size) slots[slotID] else slotID
            if (currentSlot !in 0..35) continue

            val itemStack = player.inventory.getItem(currentSlot)
            if (itemStack.isEmpty) continue

            if (!InvItemHelper.isUsefulItem(itemStack)) return true

            if (InvItemHelper.isBow(itemStack)) {
                if (itemStack !== bestBow || InvItemHelper.isDuplicateBestBow(itemStack, allItems)) return true
            } else if (InvItemHelper.isSword(itemStack)) {
                if (itemStack !== bestSword || InvItemHelper.isDuplicateBestSword(itemStack, allItems)) return true
            } else if (InvItemHelper.isPickaxe(itemStack)) {
                if (itemStack !== bestPick || InvItemHelper.isDuplicateBestPickaxe(itemStack, allItems)) return true
            } else if (InvItemHelper.isAxe(itemStack)) {
                if (itemStack !== bestAxe || InvItemHelper.isDuplicateBestAxe(itemStack, allItems)) return true
            } else if (InvItemHelper.isShovel(itemStack)) {
                if (itemStack !== bestShovel || InvItemHelper.isDuplicateBestShovel(itemStack, allItems)) return true
            } else {
                val eqType = InvItemHelper.getEquipmentType(itemStack)
                if (eqType != null) {
                    val bestArmorScore = InvItemHelper.getBestArmorScore(eqType)
                    val armorScore = InvItemHelper.getProtection(itemStack)
                    if (armorScore < bestArmorScore - 0.001f || InvItemHelper.isDuplicateBestArmor(itemStack)) return true
                }
            }
        }
        return false
    }

    fun InvManager.autoSortImpl(): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        val delay = sortDelay.value.toLong()
        val syncId = player.containerMenu.containerId

        val bestSword = InvItemHelper.getBestSword()
        val bestBow = InvItemHelper.getBestPowerBow()
        val bestPick = InvItemHelper.getBestPickaxe()
        val bestAxe = InvItemHelper.getBestAxe()
        val bestShovel = InvItemHelper.getBestShovel()
        var sortedAny = false

        for (slotID in 0..35) {
            val currentSlot = if (slotID < slots.size) slots[slotID] else slotID
            if (currentSlot !in 0..35) continue

            val itemStack = player.inventory.getItem(currentSlot)
            if (itemStack.isEmpty) continue

            for (targetSlot in 0..8) {
                val targetSetting = slotSettings[targetSlot].value
                var shouldSwap = false

                when (targetSetting) {
                    "None" -> {}
                    "Sword" -> {
                        if (InvItemHelper.isSword(itemStack) && itemStack === bestSword) {
                            if (!this.isSlotOccupiedByOtherImpl(currentSlot, targetSlot, "Sword")) {
                                if (player.inventory.getItem(targetSlot) !== bestSword) shouldSwap = true
                            }
                        }
                    }
                    "Bow" -> {
                        if (InvItemHelper.isBow(itemStack) && itemStack === bestBow) {
                            if (!this.isSlotOccupiedByOtherImpl(currentSlot, targetSlot, "Bow")) {
                                if (player.inventory.getItem(targetSlot) !== bestBow) shouldSwap = true
                            }
                        }
                    }
                    "Pickaxe" -> {
                        if (InvItemHelper.isPickaxe(itemStack) && itemStack === bestPick) {
                            if (!this.isSlotOccupiedByOtherImpl(currentSlot, targetSlot, "Pickaxe")) {
                                if (player.inventory.getItem(targetSlot) !== bestPick) shouldSwap = true
                            }
                        }
                    }
                    "Axe" -> {
                        if (InvItemHelper.isAxe(itemStack) && itemStack === bestAxe) {
                            if (!this.isSlotOccupiedByOtherImpl(currentSlot, targetSlot, "Axe")) {
                                if (player.inventory.getItem(targetSlot) !== bestAxe) shouldSwap = true
                            }
                        }
                    }
                    "Shovel" -> {
                        if (InvItemHelper.isShovel(itemStack) && itemStack === bestShovel) {
                            if (!this.isSlotOccupiedByOtherImpl(currentSlot, targetSlot, "Shovel")) {
                                if (player.inventory.getItem(targetSlot) !== bestShovel) shouldSwap = true
                            }
                        }
                    }
                    "Food" -> {
                        val mostFoodSlot = getSlotWithMostFood(targetSlot)
                        if (currentSlot == mostFoodSlot && mostFoodSlot != targetSlot) {
                            val inTarget = player.inventory.getItem(targetSlot)
                            val inMost = player.inventory.getItem(mostFoodSlot)
                            if (!(InvItemHelper.isFood(inTarget) && inTarget.count == inMost.count)) shouldSwap = true
                        }
                    }
                    "Block" -> {
                        val mostBlockSlot = getSlotWithMostBlock(targetSlot)
                        if (currentSlot == mostBlockSlot && mostBlockSlot != targetSlot) {
                            val inTarget = player.inventory.getItem(targetSlot)
                            val inMost = player.inventory.getItem(mostBlockSlot)
                            if (!(inTarget.item is BlockItem && inTarget.count == inMost.count)) shouldSwap = true
                        }
                    }
                    "Water" -> {
                        if (itemStack.item == Items.WATER_BUCKET) {
                            if (!this.isSlotOccupiedByOtherImpl(currentSlot, targetSlot, "Water")) {
                                if (player.inventory.getItem(targetSlot).item != Items.WATER_BUCKET) shouldSwap = true
                            }
                        }
                    }
                    "Lava" -> {
                        if (itemStack.item == Items.LAVA_BUCKET) {
                            if (!this.isSlotOccupiedByOtherImpl(currentSlot, targetSlot, "Lava")) {
                                if (player.inventory.getItem(targetSlot).item != Items.LAVA_BUCKET) shouldSwap = true
                            }
                        }
                    }
                    "Gapple" -> {
                        val mostGappleSlot = getSlotWithMostGapple(targetSlot)
                        if (currentSlot == mostGappleSlot && mostGappleSlot != targetSlot) {
                            val inTarget = player.inventory.getItem(targetSlot)
                            val inMost = player.inventory.getItem(mostGappleSlot)
                            if (!(inTarget.item == Items.GOLDEN_APPLE && inTarget.count == inMost.count)) shouldSwap = true
                        }
                    }
                    "Pearl" -> {
                        val mostPearlSlot = getSlotWithMostPearl(targetSlot)
                        if (currentSlot == mostPearlSlot && mostPearlSlot != targetSlot) {
                            val inTarget = player.inventory.getItem(targetSlot)
                            val inMost = player.inventory.getItem(mostPearlSlot)
                            if (!(inTarget.item == Items.ENDER_PEARL && inTarget.count == inMost.count)) shouldSwap = true
                        }
                    }
                    "Potion" -> {
                        if (itemStack.item is PotionItem) {
                            if (!this.isSlotOccupiedByOtherImpl(currentSlot, targetSlot, "Potion")) {
                                if (player.inventory.getItem(targetSlot).item !is PotionItem) shouldSwap = true
                            }
                        }
                    }
                    "Projectile" -> {
                        val mostProjSlot = getSlotWithMostProjectile(targetSlot)
                        if (currentSlot == mostProjSlot && mostProjSlot != targetSlot) {
                            val inTarget = player.inventory.getItem(targetSlot)
                            val inMost = player.inventory.getItem(mostProjSlot)
                            val isTargetProj = inTarget.item == Items.EGG || inTarget.item == Items.SNOWBALL
                            if (!(isTargetProj && inTarget.count == inMost.count)) shouldSwap = true
                        }
                    }
                    "FishingRod" -> {
                        if (itemStack.item == Items.FISHING_ROD) {
                            if (!this.isSlotOccupiedByOtherImpl(currentSlot, targetSlot, "FishingRod")) {
                                if (player.inventory.getItem(targetSlot).item != Items.FISHING_ROD) shouldSwap = true
                            }
                        }
                    }
                    "TNT" -> {
                        val mostTNTSlot = getSlotWithMostTNT(targetSlot)
                        if (currentSlot == mostTNTSlot && mostTNTSlot != targetSlot) {
                            val inTarget = player.inventory.getItem(targetSlot)
                            val inMost = player.inventory.getItem(mostTNTSlot)
                            if (!(inTarget.item == Items.TNT && inTarget.count == inMost.count)) shouldSwap = true
                        }
                    }
                }

                if (shouldSwap) {
                    if (sortTimer.hasTimeElapsed(delay) || delay == 0L) {
                        val trueID = if (currentSlot <= 8) currentSlot + 36 else currentSlot
                        gameMode.handleContainerInput(syncId, trueID, targetSlot, ContainerInput.SWAP, player)
                        sortTimer.reset()
                        sortedAny = true
                        if (delay > 0L) return true
                    }
                }
            }
        }
        return sortedAny
    }

    fun InvManager.unsortedImpl(): Boolean {
        val player = mc.player ?: return false

        for (targetSlot in 0..8) {
            when (slotSettings[targetSlot].value) {
                "Sword" -> {
                    val bestSword = InvItemHelper.getBestSword()
                    if (bestSword != null && !bestSword.isEmpty && player.inventory.getItem(targetSlot) !== bestSword && !this.isOtherSlotConfiguredImpl(targetSlot, "Sword")) return true
                }
                "Bow" -> {
                    val bestBow = InvItemHelper.getBestPowerBow()
                    if (bestBow != null && !bestBow.isEmpty && player.inventory.getItem(targetSlot) !== bestBow && !this.isOtherSlotConfiguredImpl(targetSlot, "Bow")) return true
                }
                "Pickaxe" -> {
                    val bestPick = InvItemHelper.getBestPickaxe()
                    if (bestPick != null && !bestPick.isEmpty && player.inventory.getItem(targetSlot) !== bestPick && !this.isOtherSlotConfiguredImpl(targetSlot, "Pickaxe")) return true
                }
                "Axe" -> {
                    val bestAxe = InvItemHelper.getBestAxe()
                    if (bestAxe != null && !bestAxe.isEmpty && player.inventory.getItem(targetSlot) !== bestAxe && !this.isOtherSlotConfiguredImpl(targetSlot, "Axe")) return true
                }
                "Shovel" -> {
                    val bestShovel = InvItemHelper.getBestShovel()
                    if (bestShovel != null && !bestShovel.isEmpty && player.inventory.getItem(targetSlot) !== bestShovel && !this.isOtherSlotConfiguredImpl(targetSlot, "Shovel")) return true
                }
                "Food" -> {
                    val foodSlot = getSlotWithMostFood(targetSlot)
                    if (foodSlot != targetSlot && foodSlot != -1) {
                        val inTarget = player.inventory.getItem(targetSlot)
                        val inFood = player.inventory.getItem(foodSlot)
                        if (!(InvItemHelper.isFood(inTarget) && inTarget.count == inFood.count)) return true
                    }
                }
                "Block" -> {
                    val blockSlot = getSlotWithMostBlock(targetSlot)
                    if (blockSlot != targetSlot && blockSlot != -1) {
                        val inTarget = player.inventory.getItem(targetSlot)
                        val inBlock = player.inventory.getItem(blockSlot)
                        if (!(inTarget.item is BlockItem && inTarget.count == inBlock.count)) return true
                    }
                }
                "Water" -> {
                    val hotbarWater = player.inventory.getItem(targetSlot)
                    val isWater = !hotbarWater.isEmpty && hotbarWater.item == Items.WATER_BUCKET
                    val waterSlot = InvItemHelper.findSlot(Items.WATER_BUCKET)
                    if (!isWater && waterSlot != -1) return true
                }
                "Lava" -> {
                    val hotbarLava = player.inventory.getItem(targetSlot)
                    val isLava = !hotbarLava.isEmpty && hotbarLava.item == Items.LAVA_BUCKET
                    val lavaSlot = InvItemHelper.findSlot(Items.LAVA_BUCKET)
                    if (!isLava && lavaSlot != -1) return true
                }
                "Gapple" -> {
                    val gappleSlot = getSlotWithMostGapple(targetSlot)
                    if (gappleSlot != targetSlot && gappleSlot != -1) {
                        val inTarget = player.inventory.getItem(targetSlot)
                        val inGapple = player.inventory.getItem(gappleSlot)
                        if (!(inTarget.item == Items.GOLDEN_APPLE && inTarget.count == inGapple.count)) return true
                    }
                }
                "Pearl" -> {
                    val pearlSlot = getSlotWithMostPearl(targetSlot)
                    if (pearlSlot != targetSlot && pearlSlot != -1) {
                        val inTarget = player.inventory.getItem(targetSlot)
                        val inPearl = player.inventory.getItem(pearlSlot)
                        if (!(inTarget.item == Items.ENDER_PEARL && inTarget.count == inPearl.count)) return true
                    }
                }
                "Potion" -> {
                    val hotbarPotion = player.inventory.getItem(targetSlot)
                    val isPotion = !hotbarPotion.isEmpty && hotbarPotion.item is PotionItem
                    val potSlot = InvItemHelper.findPotionSlot()
                    if (!isPotion && potSlot != -1) return true
                }
                "Projectile" -> {
                    val projSlot = getSlotWithMostProjectile(targetSlot)
                    if (projSlot != targetSlot && projSlot != -1) {
                        val inTarget = player.inventory.getItem(targetSlot)
                        val inProj = player.inventory.getItem(projSlot)
                        val isTargetProj = inTarget.item == Items.EGG || inTarget.item == Items.SNOWBALL
                        if (!(isTargetProj && inTarget.count == inProj.count)) return true
                    }
                }
                "FishingRod" -> {
                    val hotbarRod = player.inventory.getItem(targetSlot)
                    val isRod = !hotbarRod.isEmpty && hotbarRod.item == Items.FISHING_ROD
                    val rodSlot = InvItemHelper.findSlot(Items.FISHING_ROD)
                    if (!isRod && rodSlot != -1) return true
                }
                "TNT" -> {
                    val tntSlot = getSlotWithMostTNT(targetSlot)
                    if (tntSlot != targetSlot && tntSlot != -1) {
                        val inTarget = player.inventory.getItem(targetSlot)
                        val inTNT = player.inventory.getItem(tntSlot)
                        if (!(inTarget.item == Items.TNT && inTarget.count == inTNT.count)) return true
                    }
                }
            }
        }
        return false
    }

    private fun InvManager.isSlotOccupiedByOtherImpl(currentSlot: Int, targetSlot: Int, mode: String): Boolean {
        for (i in 0..8) {
            if (i != targetSlot && currentSlot == i && slotSettings[i].value == mode) {
                return true
            }
        }
        return false
    }

    private fun InvManager.isOtherSlotConfiguredImpl(targetSlot: Int, mode: String): Boolean {
        for (i in 0..8) {
            if (i != targetSlot && slotSettings[i].value == mode) {
                return true
            }
        }
        return false
    }

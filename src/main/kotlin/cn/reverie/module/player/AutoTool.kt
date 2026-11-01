package cn.reverie.module.player

import cn.reverie.mixin.client.MultiPlayerGameModeAccessor
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.combat.KillAura
import cn.reverie.module.world.Scaffold
import cn.reverie.util.misc.ItemSpoofUtil
import cn.reverie.util.player.InvItemHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.state.BlockState
import org.lwjgl.glfw.GLFW
import kotlin.math.max

object AutoTool : Module(
    name = "AutoTool",
    description = "Automatically selects the best tool or weapon",
    category = Category.PLAYER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val mode = ModeSetting("Mode", "Tool switch mode", "Silent", listOf("Silent", "Switch"))
    val switchBack = BooleanSetting("Switch Back", "Restore original slot after action", true)
    val swapDelay = NumberSetting("Swap Delay", "Delay in ticks before switching back", 1.0, 0.0, 10.0, 1.0, { switchBack.value })
    val itemSpoof = BooleanSetting("Item Spoof", "Spoof hand model during switch in Switch mode", true, { mode.isMode("Switch") })
    val silenceHotbar = BooleanSetting("Silence Hotbar", "Keep hotbar highlight on original slot in Silent mode", true, { mode.isMode("Silent") })

    val weapons = BooleanSetting("Weapons", "Auto switch to best weapon when attacking", true)
    val preferSword = BooleanSetting("Prefer Sword", "Prefer sword over axe when attacking", true, { weapons.value })

    val saveDurability = BooleanSetting("Save Durability", "Prevent breaking tools with low durability", true)
    val durabilityThreshold = NumberSetting("Durability Threshold", "Minimum durability to preserve", 5.0, 1.0, 50.0, 1.0, { saveDurability.value })

    enum class ToolState {
        IDLE,
        MINING,
        ATTACKING
    }

    var oldSlot: Int = -1
        private set

    var currentToolSlot: Int = -1
        private set

    var lastMinePos: BlockPos? = null
        private set

    var swapBackTicks: Int = -1
        private set

    var toolState: ToolState = ToolState.IDLE
        private set

    private var spoofingFromAutoTool: Boolean = false

    private val mc: Minecraft get() = Minecraft.getInstance()

    override val suffix: String
        get() = mode.value

    override fun onEnable() {
        cleanReset()
    }

    override fun onDisable() {
        val player = mc.player
        if (player != null) {
            restoreSlot(player)
        } else {
            cleanReset()
        }
    }

    /**
     * Calculates the effective digging speed of an ItemStack against a specific BlockState.
     */
    fun getDigSpeed(stack: ItemStack, state: BlockState): Float {
        if (stack.isEmpty) {
            return 1.0f / if (state.requiresCorrectToolForDrops()) 100f else 30f
        }

        if (saveDurability.value && stack.isDamageableItem) {
            val remaining = stack.maxDamage - stack.damageValue
            if (remaining <= durabilityThreshold.getInt()) {
                return 0f
            }
        }

        var speed = stack.getDestroySpeed(state)

        // Shears have block-specific speed bonuses that are not exposed by ItemStack#getDestroySpeed.
        // Match vanilla's shears block categories so wool, leaves, and other shearable blocks win selection.
        if (stack.`is`(Items.SHEARS)) {
            val path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.block).path
            speed = when {
                state.`is`(net.minecraft.tags.BlockTags.LEAVES) || state.`is`(net.minecraft.tags.BlockTags.WOOL) ||
                    state.`is`(net.minecraft.tags.BlockTags.WOOL_CARPETS) || path == "vine" || path == "glow_lichen" -> 5.0f
                path == "cobweb" || path.endsWith("_wool") -> 15.0f
                path == "grass" || path == "fern" || path == "dead_bush" || path == "hanging_roots" || path == "glow_lichen" ||
                    path.endsWith("_fern") || path.endsWith("_seagrass") -> 5.0f
                else -> speed
            }
        }

        // Prevent damaging swords on blocks they are not designed to mine (takes 2 durability)
        if (InvItemHelper.isSword(stack) && speed <= 1.0f) {
            speed = 0.05f
        }

        if (speed > 1.0f) {
            val eff = InvItemHelper.getEnchantLevel(stack, Enchantments.EFFICIENCY)
            if (eff > 0) {
                speed += (eff * eff + 1).toFloat()
            }
        }

        val canHarvest = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state)
        val modifier = if (canHarvest) 30f else 100f

        return speed / modifier
    }

    /**
     * Finds the best tool slot in the hotbar (0..8) for a given BlockState.
     * Returns -1 if no hotbar item digs faster than the current item or an empty hand.
     */
    fun findBestToolSlot(state: BlockState): Int {
        val player = mc.player ?: return -1
        val currentSlot = player.inventory.selectedSlot
        val currentStack = player.inventory.getItem(currentSlot)

        val currentSpeed = getDigSpeed(currentStack, state)
        var bestSlot = -1
        val emptyHandSpeed = getDigSpeed(ItemStack.EMPTY, state)
        var bestSpeed = max(currentSpeed, emptyHandSpeed)

        for (slot in 0..8) {
            if (slot == currentSlot) continue
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue

            if (saveDurability.value && stack.isDamageableItem) {
                val remaining = stack.maxDamage - stack.damageValue
                if (remaining <= durabilityThreshold.getInt()) continue
            }

            val speed = getDigSpeed(stack, state)
            if (speed > bestSpeed) {
                bestSpeed = speed
                bestSlot = slot
            }
        }

        return bestSlot
    }

    /**
     * Finds the best weapon slot in the hotbar (0..8).
     */
    fun findBestWeaponSlot(): Int {
        val player = mc.player ?: return -1
        var bestSlot = -1
        var bestScore = -1f

        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue

            if (saveDurability.value && stack.isDamageableItem) {
                val remaining = stack.maxDamage - stack.damageValue
                if (remaining <= durabilityThreshold.getInt()) continue
            }

            var score = 0f
            val isSword = InvItemHelper.isSword(stack)
            val isAxe = InvItemHelper.isAxe(stack)

            if (isSword) {
                score = InvItemHelper.getSwordDamage(stack)
                if (preferSword.value) {
                    score += 20.0f
                }
            } else if (isAxe) {
                score = InvItemHelper.getAxeScore(stack)
            } else {
                val attrDamage = InvItemHelper.getSwordDamage(stack)
                if (attrDamage > 1.0f) {
                    score = attrDamage
                }
            }

            if (score > 0f && score > bestScore) {
                bestScore = score
                bestSlot = slot
            }
        }

        return bestSlot
    }

    /**
     * Called when the client initiates block destruction.
     */
    fun onStartDestroyBlock(pos: BlockPos, direction: Direction) {
        if (!enabled) return
        val player = mc.player ?: return
        val level = mc.level ?: return

        if (player.isSpectator || player.abilities.instabuild || player.isUsingItem) return
        if (Scaffold.enabled) return
        if (KillAura.enabled && KillAura.target != null) return

        val state = level.getBlockState(pos)
        if (state.isAir) return

        // Skip unbreakable blocks (hardness < 0) and instant break blocks (hardness == 0)
        val hardness = state.getDestroySpeed(level, pos)
        if (hardness <= 0.0f) return

        val bestSlot = findBestToolSlot(state)
        if (bestSlot != -1 && bestSlot != player.inventory.selectedSlot) {
            if (toolState == ToolState.IDLE || oldSlot == -1) {
                oldSlot = player.inventory.selectedSlot
            }
            toolState = ToolState.MINING
            currentToolSlot = bestSlot
            lastMinePos = pos
            swapBackTicks = -1

            startSpoofingIfNeeded(oldSlot)

            player.inventory.selectedSlot = bestSlot
            syncCarriedItem()
        } else if (bestSlot == -1) {
            lastMinePos = pos
            swapBackTicks = -1
        }
    }

    /**
     * Called while the client continues destroying a block.
     */
    fun onContinueDestroyBlock(pos: BlockPos, direction: Direction) {
        if (!enabled) return
        val player = mc.player ?: return
        val level = mc.level ?: return

        if (player.isSpectator || player.abilities.instabuild || player.isUsingItem) return
        if (Scaffold.enabled) return

        swapBackTicks = -1

        // If target block changed while holding attack, re-evaluate best tool
        if (pos != lastMinePos) {
            val state = level.getBlockState(pos)
            if (!state.isAir && state.getDestroySpeed(level, pos) > 0.0f) {
                val bestSlot = findBestToolSlot(state)
                if (bestSlot != -1 && bestSlot != player.inventory.selectedSlot) {
                    if (toolState == ToolState.IDLE || oldSlot == -1) {
                        oldSlot = player.inventory.selectedSlot
                    }
                    toolState = ToolState.MINING
                    currentToolSlot = bestSlot
                    startSpoofingIfNeeded(oldSlot)

                    player.inventory.selectedSlot = bestSlot
                    syncCarriedItem()
                }
            }
            lastMinePos = pos
        }
    }

    /**
     * Called when block destruction is stopped (e.g. released click, turned away).
     */
    fun onStopDestroyBlock() {
        if (!enabled) return
        if (toolState == ToolState.MINING) {
            handleActionEnd()
        }
    }

    /**
     * Called when a block is successfully destroyed.
     */
    fun onDestroyBlock(pos: BlockPos) {
        if (!enabled) return
        if (toolState == ToolState.MINING) {
            val player = mc.player
            if (player != null && mc.options.keyAttack.isDown) {
                // If player is holding left click, allow 1-2 ticks grace for consecutive block mining
                swapBackTicks = max(swapDelay.getInt(), 1)
            } else {
                handleActionEnd()
            }
        }
    }

    /**
     * Called before attacking an entity.
     */
    fun onPreAttack(player: LocalPlayer, entity: Entity) {
        if (!enabled || !weapons.value) return
        if (Scaffold.enabled || player.isSpectator) return
        if (entity !is LivingEntity || entity is ArmorStand) return
        // HypixelA needs the selected sword to remain in hand throughout
        // the attack/block cycle. An automatic weapon swap invalidates its use.
        if (isAuraSwordSession(player)) {
            cleanReset()
            return
        }

        val weaponSlot = findBestWeaponSlot()
        if (weaponSlot != -1 && weaponSlot != player.inventory.selectedSlot) {
            if (toolState == ToolState.IDLE || oldSlot == -1) {
                oldSlot = player.inventory.selectedSlot
            }
            toolState = ToolState.ATTACKING
            currentToolSlot = weaponSlot
            swapBackTicks = -1

            startSpoofingIfNeeded(oldSlot)

            player.inventory.selectedSlot = weaponSlot
            syncCarriedItem()
        }
    }

    /**
     * Called after attacking an entity.
     */
    fun onPostAttack(player: LocalPlayer, entity: Entity) {
        if (!enabled || !weapons.value) return
        if (isAuraSwordSession(player)) return
        if (toolState == ToolState.ATTACKING) {
            handleActionEnd()
        }
    }

    /**
     * Main tick loop for status checks and delayed slot switching back.
     */
    fun onTick() {
        val player = mc.player ?: run {
            cleanReset()
            return
        }

        if (!enabled) {
            if (oldSlot != -1) {
                restoreSlot(player)
            }
            return
        }

        // A pending delayed restore from an earlier hit can otherwise swap the
        // sword away one tick after HypixelA starts blocking.
        if (isAuraSwordSession(player)) {
            if (toolState != ToolState.IDLE || swapBackTicks >= 0) cleanReset()
            return
        }

        // Handle delayed switch back
        if (swapBackTicks > 0) {
            swapBackTicks--
            if (swapBackTicks == 0) {
                restoreSlot(player)
                return
            }
        }

        // Check if player manually changed slots while active
        if (toolState != ToolState.IDLE && currentToolSlot != -1) {
            if (player.inventory.selectedSlot != currentToolSlot) {
                // User pressed a hotbar key or scrolled to another slot
                oldSlot = player.inventory.selectedSlot
                if (spoofingFromAutoTool) {
                    ItemSpoofUtil.startSpoof(oldSlot, shouldSpoofHotbar())
                }
            }
        }

        // Mining safety check: if Minecraft is no longer destroying and attack key is up
        if (toolState == ToolState.MINING) {
            val gm = mc.gameMode
            val isStillMining = gm != null && gm.isDestroying && mc.options.keyAttack.isDown
            if (!isStillMining && swapBackTicks < 0) {
                handleActionEnd()
            }
        }
    }

    private fun handleActionEnd() {
        val player = mc.player ?: run {
            cleanReset()
            return
        }

        if (switchBack.value) {
            if (swapDelay.value <= 0.0) {
                restoreSlot(player)
            } else if (swapBackTicks < 0) {
                swapBackTicks = swapDelay.getInt()
            }
        } else {
            cleanReset()
        }
    }

    private fun isAuraSwordSession(player: LocalPlayer): Boolean =
        KillAura.enabled && KillAura.target != null && InvItemHelper.isSword(player.mainHandItem)

    private fun startSpoofingIfNeeded(slot: Int) {
        val isSilent = mode.isMode("Silent")
        if (isSilent) {
            ItemSpoofUtil.startSpoof(slot, silenceHotbar.value)
            spoofingFromAutoTool = true
        } else if (itemSpoof.value) {
            ItemSpoofUtil.startSpoof(slot, false)
            spoofingFromAutoTool = true
        }
    }

    private fun shouldSpoofHotbar(): Boolean {
        return mode.isMode("Silent") && silenceHotbar.value
    }

    private fun syncCarriedItem() {
        val gm = mc.gameMode as? MultiPlayerGameModeAccessor
        gm?.nocturneEnsureHasSentCarriedItem()
    }

    fun restoreSlot(player: LocalPlayer) {
        if (spoofingFromAutoTool) {
            ItemSpoofUtil.stopSpoof()
            spoofingFromAutoTool = false
        }

        if (oldSlot in 0..8 && player.inventory.selectedSlot != oldSlot) {
            player.inventory.selectedSlot = oldSlot
            syncCarriedItem()
        }

        cleanReset()
    }

    fun cleanReset() {
        if (spoofingFromAutoTool) {
            ItemSpoofUtil.stopSpoof()
            spoofingFromAutoTool = false
        }
        oldSlot = -1
        currentToolSlot = -1
        lastMinePos = null
        swapBackTicks = -1
        toolState = ToolState.IDLE
    }
}

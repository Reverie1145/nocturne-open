package cn.reverie.module.movement.noslow

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.module.combat.KillAura
import cn.reverie.module.movement.NoSlow
import cn.reverie.util.network.Alink
import cn.reverie.util.network.PacketUtil
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.PotionItem

object GrimNoSlow : NoSlowMode {
    override val modeName: String = "Grim"

    private val alink = Alink()

    private var isUse = false
    private var onceUse = true
    private var lastTimerTime = System.currentTimeMillis()
    private var useTicks = -1
    private var waitingTick = false
    private var pendingSwapItem = false
    private var pendingUseItem = false
    private var sendUseItem = false
    private var pendingReleaseItem = false
    private var pendingRestoreItem = false
    private var stopRequested = false
    private var swapped = false
    private var serverUsing = false
    private var useHand = InteractionHand.MAIN_HAND
    private var sequence = -1
    private var bowDelaying = false

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        alink.release()
        resetState()
    }

    override fun onWorldChange() {
        onDisable()
    }

    override fun onTick() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        alink.setMaxDelayMs(maxOf(1, NoSlow.releaseTicks()) * 50L)
        if (isRangedNoSlow(player)) {
            if (!player.isUsingItem) stopBowDelay()
            return
        }
        val auraHasTarget = KillAura.enabled && KillAura.target != null
        if ((auraHasTarget || !player.isUsingItem) && isUse) {
            requestFoodStop()
        }
    }

    override fun onClick() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (isRangedNoSlow(player)) return
        if (useTicks > NoSlow.releaseTicks()) {
            mc.options.keyUse.setDown(false)
            if (!alink.isEmpty()) requestFoodStop()
        }
    }

    override fun onPreUpdate() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (isRangedNoSlow(player)) return

        if (pendingRestoreItem) {
            if (sendSwapOffhand()) {
                swapped = false
                pendingRestoreItem = false
                finishFoodUse()
            }
        } else if (pendingReleaseItem) {
            flushInBound()
            connection()?.let { PacketUtil.sendSilent(releasePacket()) }
            serverUsing = false
            pendingReleaseItem = false
            if (swapped) {
                pendingRestoreItem = true
            } else {
                finishFoodUse()
            }
        } else if (pendingUseItem) {
            sendUseItem = true
            pendingUseItem = false
        } else if (pendingSwapItem) {
            if (sendSwapOffhand()) {
                swapped = true
                pendingUseItem = true
                pendingSwapItem = false
            }
        } else if (waitingTick) {
            pendingSwapItem = true
            waitingTick = false
        }

        if (useTicks >= 0) useTicks++
    }

    override fun onSendPosition(yaw: Float, pitch: Float) {
        if (!sendUseItem) return
        val connection = connection() ?: return

        if (sequence == -1) {
            sendUseItem = false
            if (swapped) {
                pendingRestoreItem = true
            } else {
                finishFoodUse()
            }
            return
        }

        PacketUtil.sendSilent(
            ServerboundUseItemPacket(
                if (swapped) other(useHand) else useHand,
                sequence,
                yaw,
                pitch
            )
        )
        sequence = -1
        sendUseItem = false
        serverUsing = true
        if (stopRequested) pendingReleaseItem = true
    }

    override fun onItemUseSlowdown(original: Float): Float {
        val player = Minecraft.getInstance().player ?: return original
        if (!player.isUsingItem) return original
        val item = player.getUseItem().item
        if (item is BowItem && !NoSlow.bowEnabled()) return original
        if (item is CrossbowItem && !NoSlow.crossbowEnabled()) return original
        player.setSprinting(canSprintWhileUsing(player))
        return 1.0f
    }

    override fun onPacket(event: PacketEvent) {
        val player = Minecraft.getInstance().player ?: return
        if (event.isReceive) onReceive(player, event) else onSend(player, event)
    }

    private fun onReceive(player: Player, event: PacketEvent) {
        val packet = event.packet
        if (packet is ClientboundPlayerPositionPacket) {
            flushInBound()
            if (isUse) requestFoodStop()
            stopBowDelay()
            return
        }

        if (isRangedNoSlow(player)
            && player.isUsingItem
            && (packet is ClientboundKeepAlivePacket || packet is ClientboundPingPacket)
            && !event.isCancelled
        ) {
            event.cancel()
            alink.queue(packet)
            bowDelaying = true
            return
        }

        if (isRangedNoSlow(player)) return

        if (packet is ClientboundContainerSetSlotPacket || packet is ClientboundContainerSetContentPacket) {
            if (isUse || System.currentTimeMillis() - lastTimerTime < 100L) {
                event.cancel()
                return
            }
        }

        if (isHoldingFood(player)
            && (packet is ClientboundKeepAlivePacket
                || packet is ClientboundPingPacket
                || packet is ClientboundSetEntityMotionPacket)
            && isUse
            && !stopRequested
            && !event.isCancelled
        ) {
            if (useTicks == -1) useTicks = 0
            event.cancel()
            alink.queue(packet)
        }
    }

    private fun onSend(player: Player, event: PacketEvent) {
        val packet = event.packet
        if (isRangedNoSlow(player)
            && (packet is ServerboundUseItemPacket || packet is ServerboundPlayerActionPacket)
        ) {
            stopBowDelay()
        }

        if (isRangedNoSlow(player)) return

        if (packet is ServerboundPlayerActionPacket) {
            if (packet.action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                && (isUse || !alink.isEmpty())
            ) {
                Minecraft.getInstance().options.keyUse.setDown(false)
                event.cancel()
                if (isUse) requestFoodStop()
                return
            }
        }

        if (isHoldingFood(player) && packet is ServerboundUseItemPacket) {
            if (!isUse && !(isFood(player.mainHandItem) && isFood(player.offhandItem))) {
                if (!isFood(player.offhandItem) || packet.hand == InteractionHand.OFF_HAND) {
                    isUse = true
                    if (onceUse) {
                        onceUse = false
                        useHand = packet.hand
                        sequence = packet.sequence
                        waitingTick = true
                    }
                    event.cancel()
                }
            }
        }
    }

    private fun resetState() {
        alink.clear()
        isUse = false
        onceUse = true
        lastTimerTime = System.currentTimeMillis()
        useTicks = -1
        waitingTick = false
        pendingSwapItem = false
        pendingUseItem = false
        sendUseItem = false
        pendingReleaseItem = false
        pendingRestoreItem = false
        stopRequested = false
        swapped = false
        serverUsing = false
        useHand = InteractionHand.MAIN_HAND
        sequence = -1
        bowDelaying = false
    }

    private fun isFood(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item
        val food = stack.has(DataComponents.FOOD)
        val potion = item is PotionItem || item == Items.MILK_BUCKET
        val shield = item == Items.SHIELD
        return food || shield || potion
    }

    private fun isRangedWeapon(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item: Item = stack.item
        return item is BowItem && NoSlow.bowEnabled() || item is CrossbowItem && NoSlow.crossbowEnabled()
    }

    private fun isRangedNoSlow(player: Player): Boolean {
        return isRangedWeapon(player.mainHandItem)
            || isRangedWeapon(player.offhandItem)
            || isRangedWeapon(player.getUseItem())
    }

    private fun isHoldingFood(player: Player): Boolean {
        return isFood(player.mainHandItem) || isFood(player.offhandItem)
    }

    private fun canSprintWhileUsing(player: Player): Boolean {
        return player.getFoodData().foodLevel > 6
            && !player.horizontalCollision
            && Minecraft.getInstance().options.keyUp.isDown
    }

    private fun stopBowDelay() {
        if (!bowDelaying && alink.isEmpty()) return
        bowDelaying = false
        flushInBound()
    }

    private fun flushInBound() {
        alink.release()
    }

    private fun connection(): Connection? = Minecraft.getInstance().connection?.connection

    private fun releasePacket(): Packet<*> = ServerboundPlayerActionPacket(
        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
        BlockPos.ZERO,
        Direction.DOWN
    )

    private fun sendSwapOffhand(): Boolean {
        val connection = connection() ?: return false
        connection.send(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN
            )
        )
        return true
    }

    private fun requestFoodStop() {
        if (!isUse) return
        stopRequested = true
        Minecraft.getInstance().options.keyUse.setDown(false)
        flushInBound()
        if (serverUsing && !pendingReleaseItem && !pendingRestoreItem) {
            pendingReleaseItem = true
        }
    }

    private fun finishFoodUse() {
        flushInBound()
        isUse = false
        onceUse = true
        useTicks = -1
        waitingTick = false
        pendingSwapItem = false
        pendingUseItem = false
        sendUseItem = false
        pendingReleaseItem = false
        pendingRestoreItem = false
        stopRequested = false
        serverUsing = false
        useHand = InteractionHand.MAIN_HAND
        sequence = -1
        lastTimerTime = System.currentTimeMillis()
    }

    private fun other(hand: InteractionHand): InteractionHand =
        if (hand == InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND

    private fun isActive(): Boolean {
        return isUse
            || bowDelaying
            || waitingTick
            || pendingSwapItem
            || pendingUseItem
            || sendUseItem
            || pendingReleaseItem
            || pendingRestoreItem
    }

    @JvmStatic
    fun isEating(): Boolean = isActive()

    @JvmStatic
    fun isRecentlyFinishedEating(): Boolean = System.currentTimeMillis() - lastTimerTime < 100L

    @JvmStatic
    fun isAlinkActive(): Boolean = alink.isActive()

    @JvmStatic
    fun getAlinkProgress(): Float = alink.getProgress()
}

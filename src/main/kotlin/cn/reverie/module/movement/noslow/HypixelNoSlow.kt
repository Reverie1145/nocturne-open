package cn.reverie.module.movement.noslow

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.mixin.client.MinecraftAccessor
import cn.reverie.module.movement.NoSlow
import cn.reverie.util.misc.ClientTimer
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.phys.EntityHitResult
import kotlin.math.sqrt

object HypixelNoSlow : NoSlowMode {
    override val modeName: String = "HypixelA"

    private enum class ResyncPhase { NONE, AWAY, BACK }

    private var tick = 0
    private var fullTick = true
    private var activeAtPreUpdate = false
    private var kbBoostLockout = 0
    private var resyncPhase = ResyncPhase.NONE
    private var grimSlotGuess = 0
    private var resyncRunning = false
    private var windowDirty = false
    private var resyncSending = false
    private var cyclePos = 0
    private var gapTick = 0
    private var selfC07 = false
    private var blinkActive = false
    private var blinkDoneThisCycle = false
    private var blinkStartNano = 0L
    private var blinkHeldFlying = 0
    private var blinkAbort = false
    private var blinkKb = false
    private var blinkReleased = false
    private val blinkQueue = ArrayList<Packet<*>>()
    private var swFinishing = false
    private var creditMs = 0.0
    private var lastRealNano = 0L
    private var bursting = false
    private var burstLeft = 0
    private var timerUp = 1.0f
    private var timerDown = 1.0f

    override fun onEnable() {
        resetAll()
    }

    override fun onDisable() {
        flushBlink()
        resetAll()
    }

    override fun onWorldChange() {
        onDisable()
    }

    override fun onTick() {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null || mc.connection == null) {
            resetAll()
            return
        }
        if (kbBoostLockout > 0) kbBoostLockout--
        activeAtPreUpdate = active()
        if (!activeAtPreUpdate) {
            if (swFinishing || blinkActive) flushBlink()
            stopResync()
            resetTimer()
            tick = 0
            fullTick = true
            return
        }

        if (NoSlow.slotResync.value) resyncTick()
        else {
            stopResync()
            fullTick = NoSlow.slowInterval.getInt() <= 0
                || tick++ % (NoSlow.slowInterval.getInt() + 1) == 0
        }
        if (NoSlow.blink.value && !blinkActive && resyncRunning
            && resyncPhase == (if (NoSlow.blinkBlock.value) ResyncPhase.BACK else ResyncPhase.AWAY)
            && !blinkDoneThisCycle && !windowDirty
        ) {
            blinkActive = true
            blinkStartNano = System.nanoTime()
            blinkHeldFlying = 0
            blinkAbort = false
            blinkKb = false
            blinkReleased = false
            blinkDoneThisCycle = true
        }
        if (blinkActive) {
            val wanted = maxOf(1, NoSlow.blinkTicks.getInt())
            val due = blinkAbort || blinkKb || blinkReleased
                || blinkHeldFlying >= wanted
                && (System.nanoTime() - blinkStartNano) / 1.0E6 >= blinkHeldFlying * 50.0
            if (due) flushBlink()
        }
        if (mc.options.keyUp.isDown) mc.player?.setSprinting(true)
        timerTick()
    }

    override fun onPreMotion(player: net.minecraft.client.player.LocalPlayer) {
        if (!NoSlow.boost.value || kbBoostLockout > 0 || !active() || !fullTick
            || player.horizontalCollision
        ) return
        val dx = player.deltaMovement.x
        val dz = player.deltaMovement.z
        val distance = sqrt(dx * dx + dz * dz)
        if (distance <= 1.0E-4) return
        val scale = 9.0E-4 / distance
        player.setPos(player.x + dx * scale, player.y, player.z + dz * scale)
    }

    override fun onItemUseSlowdown(original: Float): Float {
        return if (fullTick && (activeAtPreUpdate || swFinishing)) 1.0f else original
    }

    override fun onPacket(event: PacketEvent) {
        val mc = Minecraft.getInstance()
        if (event.isCancelled || mc.player == null) return
        val packet = event.packet
        if (event.isReceive) {
            if (packet is ClientboundSetEntityMotionPacket && packet.id == mc.player?.id) {
                if (active() || blinkActive) kbBoostLockout = 10
                if (blinkActive) blinkKb = true
            }
            return
        }
        if (blinkActive && packet is ServerboundMovePlayerPacket) {
            blinkQueue.add(packet)
            blinkHeldFlying++
            event.cancel()
            return
        }
        if (packet is ServerboundMovePlayerPacket) {
            windowDirty = false
            creditMs -= 50.0
            return
        }
        if (packet is ServerboundPlayerActionPacket
            && packet.action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
        ) {
            if (selfC07) return
            blinkAbort = true
            blinkReleased = true
            stopResync()
        }
        if (packet is ServerboundSetCarriedItemPacket && !resyncSending) {
            windowDirty = true
        }
    }

    private fun active(): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        return mc.level != null && player.isUsingItem
            && player.getUseItem().useAnimation == ItemUseAnimation.BLOCK
    }

    private fun backFullActive(): Boolean {
        return NoSlow.slotResync.value && !NoSlow.slotRelease.value && NoSlow.backFullTick.value
    }

    private fun resyncTick() {
        val player = Minecraft.getInstance().player ?: return
        val local = player.inventory.selectedSlot
        if (resyncPhase == ResyncPhase.NONE) {
            grimSlotGuess = local
            resyncRunning = true
            fullTick = false
            resyncPhase = ResyncPhase.BACK
            return
        }
        if (windowDirty) {
            fullTick = if (cyclePos <= 1) resyncPhase == ResyncPhase.AWAY else !backFullActive()
            return
        }
        if (cyclePos == 0) {
            if (NoSlow.slotRelease.value) sendRelease()
            else sendC09(nextSlot(local))
            resyncPhase = ResyncPhase.AWAY
            cyclePos = 1
            fullTick = true
            return
        }
        if (cyclePos == 1) {
            if (!NoSlow.slotRelease.value && grimSlotGuess != local) sendRelease()
            doItemUseWithoutBlock()
            resyncPhase = ResyncPhase.BACK
            fullTick = backFullActive()
            blinkDoneThisCycle = false
            gapTick = 0
            cyclePos = if (!NoSlow.blink.value || NoSlow.c09Interval.getInt() <= 2) 0 else 2
            return
        }
        fullTick = if (backFullActive()) gapTick % 2 == 1
        else NoSlow.slowInterval.getInt() <= 0
            || gapTick++ % (NoSlow.slowInterval.getInt() + 1) == 0
        cyclePos++
        if (cyclePos >= maxOf(2, NoSlow.c09Interval.getInt())) {
            cyclePos = 0
            gapTick = 0
        }
    }

    private fun nextSlot(local: Int): Int {
        val target = (local + 1) % 9
        return if (target == grimSlotGuess) (local + 2) % 9 else target
    }

    private fun sendC09(slot: Int) {
        resyncSending = true
        try {
            connectionSend(ServerboundSetCarriedItemPacket(slot))
        } finally {
            resyncSending = false
        }
        grimSlotGuess = slot
    }

    private fun sendRelease() {
        selfC07 = true
        try {
            connectionSend(
                ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                    BlockPos.ZERO,
                    Direction.DOWN
                )
            )
        } finally {
            selfC07 = false
        }
    }

    private fun connectionSend(packet: Packet<*>) {
        Minecraft.getInstance().connection?.connection?.send(packet)
    }

    private fun stopResync() {
        val player = Minecraft.getInstance().player ?: return
        if (resyncRunning && resyncPhase == ResyncPhase.AWAY
            && grimSlotGuess != player.inventory.selectedSlot && !windowDirty
        ) {
            sendC09(player.inventory.selectedSlot)
        }
        resyncPhase = ResyncPhase.NONE
        resyncRunning = false
        cyclePos = 0
        gapTick = 0
        windowDirty = false
    }

    private fun flushBlink() {
        if (!blinkActive) return
        blinkActive = false
        for (packet in ArrayList(blinkQueue)) connectionSend(packet)
        blinkQueue.clear()
        blinkHeldFlying = 0
        blinkAbort = false
        blinkKb = false
        blinkReleased = false
    }

    private fun timerTick() {
        if (!NoSlow.timerBurst.value || blinkActive || NoSlow.blink.value) {
            bursting = false
            burstLeft = 0
            ClientTimer.reset()
            return
        }
        val now = System.nanoTime()
        if (lastRealNano == 0L) lastRealNano = now
        creditMs += (now - lastRealNano) / 1.0E6
        lastRealNano = now
        if (bursting && burstLeft-- > 0 && creditMs >= 58.0) {
            ClientTimer.setMultiplier(timerUp)
            return
        }
        bursting = false
        if (creditMs >= maxOf(0.0, 120.0 - NoSlow.safetyMs.value)) {
            snapshotTimer()
            bursting = true
            burstLeft = maxOf(1, NoSlow.burstTicks.getInt())
            ClientTimer.setMultiplier(timerUp)
        } else {
            ClientTimer.setMultiplier(timerDown)
        }
    }

    private fun snapshotTimer() {
        val n = maxOf(1, NoSlow.burstTicks.getInt())
        val peak = 122.0
        val up = 1.0 / (1.0 - minOf(peak, 50.0 * n - 1.0) / (50.0 * n))
        timerUp = minOf(NoSlow.maxTimer.value, up).toFloat()
        timerDown = maxOf(0.25, minOf(1.0, timerUp / (2.0 * timerUp - 1.0))).toFloat()
    }

    private fun resetTimer() {
        creditMs = 0.0
        lastRealNano = 0L
        bursting = false
        burstLeft = 0
        timerUp = 1.0f
        timerDown = 1.0f
        ClientTimer.reset()
    }

    private fun resetAll() {
        tick = 0
        fullTick = true
        activeAtPreUpdate = false
        kbBoostLockout = 0
        stopResyncState()
        blinkActive = false
        blinkQueue.clear()
        blinkHeldFlying = 0
        blinkAbort = false
        blinkKb = false
        blinkReleased = false
        swFinishing = false
        resetTimer()
    }

    private fun stopResyncState() {
        resyncPhase = ResyncPhase.NONE
        resyncRunning = false
        resyncSending = false
        windowDirty = false
        cyclePos = 0
        gapTick = 0
        grimSlotGuess = 0
        selfC07 = false
    }

    private fun doItemUseWithoutBlock() {
        val mc = Minecraft.getInstance()
        val player: Player = mc.player ?: return
        if (mc.level == null || mc.gameMode == null || mc.gameMode!!.isDestroying) return

        (mc as MinecraftAccessor).nocturneSetRightClickDelay(4)
        if (player.isPassenger) return

        for (hand in InteractionHand.entries) {
            val stack: ItemStack = player.getItemInHand(hand)
            if (!stack.isItemEnabled(mc.level!!.enabledFeatures())) return

            val result = mc.hitResult
            if (result is EntityHitResult) {
                val entity = result.entity
                if (!mc.level!!.worldBorder.isWithinBounds(entity.blockPosition())) return
                var interaction = mc.gameMode!!.interact(player, entity, result, hand)
                if (!interaction.consumesAction()) {
                    interaction = mc.gameMode!!.interact(player, entity, result, hand)
                }
                if (interaction is InteractionResult.Success) {
                    if (interaction.swingSource() == InteractionResult.SwingSource.CLIENT) {
                        player.swing(hand)
                    }
                    return
                }
            }

            if (!stack.isEmpty) {
                val interaction = mc.gameMode!!.useItem(player, hand)
                if (interaction is InteractionResult.Success) {
                    if (interaction.swingSource() == InteractionResult.SwingSource.CLIENT) {
                        player.swing(hand)
                    }
                    return
                }
            }
        }
    }
}

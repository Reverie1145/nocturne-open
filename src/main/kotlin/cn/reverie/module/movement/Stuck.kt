package cn.reverie.module.movement

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.util.network.PacketUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.phys.Vec3

object Stuck : Module("Stuck", "Freeze movement using a selectable packet or travel mode", Category.MOVEMENT) {
    val mode = ModeSetting("Mode", "Movement freeze method", "NoPacket", listOf("NoPacket", "CancelMove", "Ticks", "Expo Normal", "Expo Pulse"))
    val pulseDelay = NumberSetting("Pulse-delay", "Ticks between Expo Pulse movement releases", 20.0, 0.0, 200.0, 1.0, { mode.isMode("Expo Pulse") })
    val tickCycle = BooleanSetting("Tick Cycle", "Release the freeze for one tick every 21 ticks", false, { mode.isMode("NoPacket") || mode.isMode("CancelMove") })
    val packetMove = BooleanSetting("PacketMove", "Send an offset movement packet during the release tick", false, { tickCycle.value && (mode.isMode("NoPacket") || mode.isMode("CancelMove")) })
    val autoDisable = BooleanSetting("Auto Disable", "Disable Ticks mode when blocks are nearby below", false, { mode.isMode("Ticks") })
    val bypass = BooleanSetting("Bypass", "Alternate sneak input while Ticks mode is active", false, { mode.isMode("Ticks") })

    private var cycleTicks = 0
    private var pausedThisTick = false
    private var clutching = false
    private var bypassTicks = 0
    private var bypassSneaking = false
    private var travelTicks = 0
    private var expoPulseRemainingMs = 0
    private var expoReleased = false

    init {
        mode.onChanged = { reset() }
        listen(PacketEvent::class.java) { event ->
            if (event.isReceive) {
                val player = Minecraft.getInstance().player ?: return@listen
                if (event.packet is ClientboundPlayerPositionPacket) {
                    enabled = false
                } else if (mode.isMode("NoPacket") && active() &&
                    (event.packet as? ClientboundSetEntityMotionPacket)?.id == player.id) {
                    event.isCancelled = true
                }
            } else if (mode.isMode("NoPacket") && active() && event.packet is ServerboundMovePlayerPacket) {
                event.isCancelled = true
            }
        }
    }

    override fun onEnable() = reset()

    override fun onDisable() {
        val player = Minecraft.getInstance().player
        if (player != null) {
            if (mode.isMode("Ticks") && bypass.value) {
                if (bypassSneaking) sendSneak(player, false)
                bypassInteract(player)
            } else if (mode.isMode("NoPacket") && !player.onGround()) {
                sendOffsetPosition(player)
            }
        }
        reset()
    }

    private fun reset() {
        cycleTicks = 0
        pausedThisTick = false
        clutching = false
        bypassTicks = 0
        bypassSneaking = false
        travelTicks = 0
        expoPulseRemainingMs = 0
        expoReleased = false
    }

    @JvmStatic
    fun onTick(player: LocalPlayer) {
        if (!enabled) return
        if (isExpoMode()) {
            player.setSprinting(false)
            expoReleased = false
            if (expoPulseRemainingMs > 0) expoPulseRemainingMs -= 50
            if (player.hurtTime != 0) {
                expoReleased = true
            } else if (mode.isMode("Expo Pulse") && expoPulseRemainingMs <= 0) {
                expoPulseRemainingMs += pulseDelay.getInt() * 50
                expoReleased = true
            }
            return
        }
        travelTicks = (travelTicks + 1) % 20
        if (mode.isMode("Ticks")) {
            clutching = !player.onGround()
            if (autoDisable.value) {
                val level = Minecraft.getInstance().level
                if (level != null && (1..2).any { !level.getBlockState(player.blockPosition().below(it)).isAir }) {
                    enabled = false
                    return
                }
            }
            if (bypass.value && clutching && ++bypassTicks >= 10) {
                bypassTicks = 0
                bypassSneaking = !bypassSneaking
                sendSneak(player, bypassSneaking)
            }
            return
        }
        pausedThisTick = tickCycle.value && cycleTicks == 20
        cycleTicks = if (tickCycle.value) (cycleTicks + 1) % 21 else 0
        if (pausedThisTick && packetMove.value && !player.onGround()) {
            sendOffsetPosition(player)
        }
    }

    private fun sendOffsetPosition(player: LocalPlayer) {
        PacketUtil.sendPacketNoEvent(ServerboundMovePlayerPacket.PosRot(
            Vec3(player.x + 1337.0, player.y, player.z + 1337.0),
            player.yRot + 0.01f, player.xRot, player.onGround(), player.horizontalCollision
        ))
    }

    private fun sendSneak(player: LocalPlayer, sneak: Boolean) {
        val input = player.input.keyPresses
        PacketUtil.sendPacketNoEvent(ServerboundPlayerInputPacket(Input(
            input.forward(), input.backward(), input.left(), input.right(), input.jump(), sneak, input.sprint()
        )))
    }

    private fun bypassInteract(player: LocalPlayer) {
        val mc = Minecraft.getInstance()
        val gameMode = mc.gameMode ?: return
        var hand = InteractionHand.OFF_HAND
        val previousSlot = player.inventory.selectedSlot
        var switched = false
        if (isUsable(player.offhandItem)) {
            for (slot in 0..8) {
                if (!isUsable(player.inventory.getItem(slot))) {
                    hand = InteractionHand.MAIN_HAND
                    if (slot != previousSlot) {
                        player.inventory.selectedSlot = slot
                        PacketUtil.sendPacketNoEvent(ServerboundSetCarriedItemPacket(slot))
                        switched = true
                    }
                    break
                }
            }
        }
        gameMode.useItem(player, hand)
        if (switched) {
            player.inventory.selectedSlot = previousSlot
            PacketUtil.sendPacketNoEvent(ServerboundSetCarriedItemPacket(previousSlot))
        }
    }

    private fun isUsable(stack: ItemStack): Boolean = !stack.isEmpty && stack.item.getUseAnimation(stack) != ItemUseAnimation.NONE

    private fun isExpoMode(): Boolean = mode.isMode("Expo Normal") || mode.isMode("Expo Pulse")

    private fun active(): Boolean = enabled && !isExpoMode() && (if (mode.isMode("Ticks")) clutching else !tickCycle.value || !pausedThisTick)

    @JvmStatic
    fun onMoveInput(event: MoveInputEvent) {
        if (!active() && !(enabled && isExpoMode())) return
        event.forward = 0f
        event.strafe = 0f
    }

    @JvmStatic
    fun shouldCancelTravel(): Boolean = if (enabled && isExpoMode()) !expoReleased else active() && travelTicks != 0

    @JvmStatic
    fun shouldCancelEntityMove(): Boolean = enabled && isExpoMode() && !expoReleased

    @JvmStatic
    fun shouldClearVelocityOnTravelCancel(): Boolean = !isExpoMode()

    @JvmStatic
    fun shouldHoldPositionUpdate(): Boolean = enabled && mode.isMode("NoPacket") && active()
}

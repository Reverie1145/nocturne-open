package cn.reverie.module.combat.killaura

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.mixin.client.MultiPlayerGameModeAccessor
import cn.reverie.mixin.entity.LocalPlayerAccessor
import cn.reverie.mixin.world.ClientLevelAccessor
import cn.reverie.module.combat.KillAura
import cn.reverie.module.world.Scaffold
import cn.reverie.util.network.PacketUtil
import cn.reverie.util.network.ProtocolUtil
import cn.reverie.util.rotation.RayCastUtil
import cn.reverie.util.rotation.Rotation
import com.viaversion.viafabricplus.ViaFabricPlus
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusHandItemProvider
import com.viaversion.viaversion.api.protocol.Protocol
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.protocols.v1_8to1_9.Protocol1_8To1_9
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_8
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.EntityHitResult
import org.lwjgl.glfw.GLFW

/** Minecraft API adapter; the source's BlinkUtility and BadPackets definitions were not supplied. */
internal object KillAuraWatchdogBlock {
    private var owner: LocalPlayer? = null
    private var started = false
    @Volatile private var blink: KillAuraBlinkBuffer<() -> Unit>? = null
    private var tickRotation: Rotation? = null
    private val attackBoundary = KillAuraAttackBoundary()
    private val cycle = KillAuraWatchdogCycle(object : KillAuraWatchdogCycle.Actions {
        override fun disableSprintKey() {
            Minecraft.getInstance().options.keySprint.isDown = false
        }

        override fun stopSprinting() {
            owner?.isSprinting = false
        }

        override fun raycast(): Boolean {
            val player = owner ?: return false
            val mc = Minecraft.getInstance()
            // Nocturne keeps packet rotations separate from the visible camera.
            // Use the same resolved tick rotation for this ray and the movement packet.
            val rotation = tickRotation ?: Rotation(player.yRot, player.xRot)
            mc.hitResult = RayCastUtil.rayCast(rotation, 3.0, 0f, player)
            return (mc.hitResult as? EntityHitResult)?.entity === KillAura.target && KillAura.target != null
        }

        override fun attack(raycastHit: Boolean) {
            val player = owner ?: return
            val target = KillAura.target ?: return
            if (!attackBoundary.canAttackOrUse) return
            // processWatchdogBWPre forces Packet attack without canAttack/CPS checks.
            // Aura.java's BadPackets.bad(...) guard requires the missing BadPackets source.
            if (raycastHit) KillAura.doAttack(player, target, swingBeforeAttack = ProtocolUtil.is1_8OrOlder())
            else player.swing(InteractionHand.MAIN_HAND)
            KillAura.attackTimer.reset()
        }

        override fun playerIsBlocking(): Boolean {
            return owner?.isBlocking == true
        }

        override fun realBlock() {
            if (!attackBoundary.canAttackOrUse) return
            val player = owner ?: return
            val mc = Minecraft.getInstance()
            if (mc.gameMode == null || mc.level == null) return
            if (!KillAura.isHoldingSword(player)) return
            sendSwordBlock(player, tickRotation ?: Rotation(player.yRot, player.xRot), mc.hitResult as? EntityHitResult)
            setBlocking(true)
        }

        override fun stopUsingItem() {
            owner?.stopUsingItem()
        }

        override fun releaseUsingItem() {
            val player = owner ?: return
            val mc = Minecraft.getInstance()
            if (mc.player === player && mc.connection === player.connection) {
                attackBoundary.onRelease()
                mc.gameMode?.releaseUsingItem(player)
            }
        }

        override fun setBlocking(blocking: Boolean) {
            KillAura.blocking = blocking
            KillAura.blockingState = blocking
        }

        override fun setRenderBlock(blocking: Boolean) {
            KillAura.renderBlock = blocking
        }

        override fun acquireBlink(): KillAuraWatchdogCycle.BlinkSession {
            return KillAuraBlinkBuffer<() -> Unit> { it() }.also { blink = it }
        }
    })

    /**
     * Shared sword-use sequence: optional entity interact, then the use-item action
     * (C08 on 1.8 connections), then the local use state. Callers must already have
     * marked the legacy sword stack as owned so the 72000 tick use duration applies.
     */
    fun sendSwordBlock(player: LocalPlayer, rotation: Rotation, hit: EntityHitResult?) {
        val mc = Minecraft.getInstance()
        val gameMode = mc.gameMode ?: return
        val level = mc.level ?: return
        (gameMode as MultiPlayerGameModeAccessor).nocturneEnsureHasSentCarriedItem()
        if (hit != null && !interactLegacy(player, gameMode, hit)) {
            gameMode.interact(player, hit.entity, hit, InteractionHand.MAIN_HAND)
        }
        if (!sendLegacyBlock(player)) {
            ViaFabricPlusHandItemProvider.lastUsedItem = player.mainHandItem.copy()
            (level as ClientLevelAccessor).nocturneGetBlockStatePredictionHandler().startPredicting().use { prediction ->
                PacketUtil.sendPacket(ServerboundUseItemPacket(
                    InteractionHand.MAIN_HAND, prediction.currentSequence(), rotation.yaw, rotation.pitch
                ))
            }
        }
        player.startUsingItem(InteractionHand.MAIN_HAND)
    }

    /** C08(stack) has its own held-item snapshot and does not contain a rotation. */
    private fun sendLegacyBlock(player: LocalPlayer): Boolean {
        if (!ProtocolUtil.is1_8OrOlder()) return false
        val connection = ViaFabricPlus.api().userConnection() ?: return false
        val item = ViaFabricPlus.api().conversions()
            .translateItem(player.mainHandItem.copy(), ProtocolVersion.v1_8) ?: return false
        val packet = PacketWrapper.create(ServerboundPackets1_8.USE_ITEM_ON, connection)
        KillAuraLegacyPackets.writeSwordBlock(packet, item)
        queueLegacyPacket(packet)
        return true
    }

    private fun interactLegacy(player: LocalPlayer, gameMode: MultiPlayerGameMode, hit: EntityHitResult): Boolean {
        if (!ProtocolUtil.is1_8OrOlder()) return false
        val connection = ViaFabricPlus.api().userConnection() ?: return false
        val entity = hit.entity
        val entityId = PacketUtil.legacyEntityId(connection, entity.id)
        val location = hit.location.subtract(entity.position())
        val interactAt = PacketWrapper.create(ServerboundPackets1_8.INTERACT, connection)
        KillAuraLegacyPackets.writeInteractAt(interactAt, entityId, location.x, location.y, location.z)
        queueLegacyPacket(interactAt)

        // 1.8 Entity.interactAt returns false; only ArmorStand overrides it.
        // The modern API merges both actions, and Via sends both automatically.
        val spectator = gameMode.playerMode == GameType.SPECTATOR
        val acceptedAt = !spectator && entity is ArmorStand &&
            entity.interact(player, InteractionHand.MAIN_HAND, location).consumesAction()
        if (!acceptedAt) {
            (gameMode as MultiPlayerGameModeAccessor).nocturneEnsureHasSentCarriedItem()
            val interact = PacketWrapper.create(ServerboundPackets1_8.INTERACT, connection)
            KillAuraLegacyPackets.writeInteract(interact, entityId)
            queueLegacyPacket(interact)
            if (!spectator) player.interactOn(entity, InteractionHand.MAIN_HAND, location)
        }
        return true
    }

    fun captureProtocolPacket(packet: PacketWrapper, protocol: Class<out Protocol<*, *, *, *>>): Boolean {
        if (!selected()) return false
        return blink?.capture { packet.sendToServer(protocol) } == true
    }

    private fun queueLegacyPacket(packet: PacketWrapper) {
        val action: () -> Unit = { packet.sendToServer(Protocol1_8To1_9::class.java) }
        if (blink?.capture(action) != true) action()
    }

    val blockTicks: Int get() = cycle.blockTicks
    val rightMouseABActive: Boolean get() = cycle.rightMouseABActive

    private fun selected(): Boolean = KillAura.enabled && KillAura.autoBlockMode.isMode("WatchdogBW")

    fun onEnable() {
        if (!selected()) return
        cycle.onEnable(KillAura.autoBlockOnlyPressed.value)
        started = true
    }

    fun controlsAttack(): Boolean = selected() && cycle.rightMouseABActive

    fun allowsRegularAttack(): Boolean = selected() && cycle.allowAttack && attackBoundary.canAttackOrUse

    // The input pass runs before Tick PRE; use this tick's physical button here
    // so pressing Only Pressed cannot also queue an ordinary aura click.
    fun blocksInputAttack(player: LocalPlayer): Boolean = selected() &&
        (!KillAura.autoBlockOnlyPressed.value || KillAura.isHoldingSword(player) && rightMouseDown())

    fun shouldPauseSprint(player: LocalPlayer): Boolean = !Scaffold.enabled &&
        KillAura.target != null && KillAura.isHoldingSword(player) && blocksInputAttack(player)

    private fun rightMouseDown(): Boolean = Minecraft.getInstance().let { mc ->
        GLFW.glfwGetMouseButton(mc.window.handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
    }

    fun wantsBlock(player: LocalPlayer): Boolean =
        controlsAttack() && KillAura.target != null && KillAura.isHoldingSword(player)

    fun onPreTick(player: LocalPlayer, rotation: Rotation): Boolean {
        if (!selected()) return false
        if (owner != null && owner !== player) reset(discardPackets = true)
        owner = player
        tickRotation = rotation
        if (!started) onEnable()
        if (Scaffold.enabled) {
            KillAura.target = null
            return false
        }
        cycle.onPreTick(
            KillAura.autoBlockOnlyPressed.value,
            rightMouseDown(),
            KillAura.isHoldingSword(player),
            KillAura.target != null,
            player.isAlive,
            player.isSprinting,
            (player as LocalPlayerAccessor).nocturneWasSprinting()
        )
        return true
    }

    fun onPostTick() {
        if (selected() && !Scaffold.enabled) cycle.onPostTick()
    }

    fun onPreMotion(player: LocalPlayer) {
        if (!selected()) return
        owner = player
        if (!started) onEnable()
        cycle.onPreMotion(KillAura.target != null && !Scaffold.enabled)
    }

    fun ownsUseItem(): Boolean {
        val player = owner ?: return false
        return legacyBlockingItem(player) != null
    }

    fun isLegacySwordStack(stack: ItemStack): Boolean {
        val player = owner ?: return false
        return selected() && cycle.blocking && player === Minecraft.getInstance().player &&
            stack === player.mainHandItem && stack.`is`(ItemTags.SWORDS)
    }

    fun legacyBlockingItem(player: LocalPlayer): ItemStack? {
        return if (player === owner && isLegacySwordStack(player.mainHandItem) &&
            player.isUsingItem && player.usedItemHand == InteractionHand.MAIN_HAND &&
            player.useItem.`is`(ItemTags.SWORDS)) player.useItem else null
    }

    fun onPacket(event: PacketEvent) {
        if (!event.isSend || event.isCancelled) return
        if (event.packet is ServerboundMovePlayerPacket) attackBoundary.onMovement()
        if (selected() && (event.packet as? ServerboundPlayerActionPacket)?.action ==
            ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) attackBoundary.onRelease()
        val buffer = blink ?: return
        if (!buffer.active) return
        val packet = event.packet
        if (!KillAuraBlinkPackets.includes(packet)) return
        // C08 includes its held item in 1.8. Save it with the queued action instead
        // of using whichever item the hand-item provider happens to hold at flush.
        val handItem = if (packet is ServerboundUseItemPacket && packet.hand == InteractionHand.MAIN_HAND) {
            ViaFabricPlusHandItemProvider.lastUsedItem?.copy()
        } else null
        val action: () -> Unit = {
            if (handItem != null) ViaFabricPlusHandItemProvider.lastUsedItem = handItem
            PacketUtil.sendPacketNoEvent(packet)
        }
        if (buffer.capture(action)) event.cancel()
    }

    fun reset(discardPackets: Boolean = false) {
        val player = owner
        val sameConnection = player == null || Minecraft.getInstance().let { mc ->
            mc.player === player && mc.level === player.level() && mc.connection === player.connection
        }
        if (discardPackets || !sameConnection) {
            cycle.discard()
            attackBoundary.onMovement()
        } else cycle.onDisable()
        blink = null
        owner = null
        started = false
        tickRotation = null
    }
}

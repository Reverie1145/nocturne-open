package cn.reverie.module.combat.killaura

import cn.reverie.module.combat.KillAura
import cn.reverie.util.rotation.RayCastUtil
import cn.reverie.util.rotation.Rotation
import cn.reverie.util.rotation.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.EntityHitResult
import kotlin.math.max
import kotlin.math.min

/**
 * HypixelA AutoBlock, ported from Grim.java's "Hypixel" AutoBlock.
 *
 * The fake block pose stays up while the target is in range. A real sword block
 * is only sent for a short window (at most three ticks) around the moment the
 * target's own swing reaches its contact phase, then released with a proper
 * release-use-item action. All entry points run from the Tick PRE update, after
 * this tick's attack and before the movement packet.
 */
internal object KillAuraHypixelABlock {
    /** Fake pose and real blocks are only maintained inside this distance. */
    private const val ENGAGE_RANGE = 3.5
    private const val MAX_BLOCK_TICKS = 3
    private const val SWING_DURATION = 6

    private var owner: LocalPlayer? = null
    private var realBlocking = false
    private var started = false
    private var blockTicks = 0
    private var swingInterval = MAX_BLOCK_TICKS
    private var swingWasActive = false
    private var swingStartTick = -1
    private var predictedSwingTick = -1
    private var predictionTarget: LivingEntity? = null

    private fun selected(): Boolean = KillAura.enabled && KillAura.autoBlockMode.isMode("HypixelA")

    /** One block update per tick. A null target drops every block state. */
    fun update(player: LocalPlayer, target: LivingEntity?) {
        if (!selected()) return
        owner = player
        if (target == null || !target.isAlive || target.level() !== player.level() ||
            !KillAura.isHoldingSword(player)) {
            stop()
            return
        }

        if (predictionTarget !== target) {
            predictionTarget = target
            resetSwingTracking()
        }

        val distance = KillAura.combatDistance(player, target)
        if (distance >= ENGAGE_RANGE || distance > KillAura.blockRange.value) {
            stop()
            return
        }

        KillAura.renderBlock = true
        KillAura.fakeBlockRenderTarget = target
        KillAura.fakeBlockOutOfRangeTicks = 0

        if (!started) {
            started = true
            blockTicks = 1
            return
        }
        if (player.hurtTime > 2) {
            blockTicks = 0
            release()
            return
        }

        val swinging = target.swinging
        val tick = target.tickCount
        if (swinging && !swingWasActive) {
            if (swingStartTick >= 0) {
                val interval = max(1, tick - swingStartTick)
                // 1.8's swingProgressInt advances once per tick. A faster observed
                // swing cycle needs a shorter prediction window.
                swingInterval = if (interval <= 2) 1 else if (interval <= 4) 2 else 3
            }
            swingStartTick = tick
            predictedSwingTick = -1
        }
        swingWasActive = swinging

        // 26.2 has no per-swing description; 6 ticks is the vanilla/1.8 swing length.
        val duration = SWING_DURATION
        val phase = target.getAttackAnim(0.0f)
        // The 1.8 attack contact is around the middle of the swing. Accept a small
        // phase band instead of one exact frame to survive packet/tick jitter.
        val contactWindow = swinging && phase >= 0.35f && phase <= min(0.85f, 4.0f / duration + 0.35f)
        if (contactWindow && predictedSwingTick != swingStartTick) {
            predictedSwingTick = swingStartTick
            // The current tick is already part of the prediction window, so the
            // remainder keeps the hard three-tick maximum.
            blockTicks = max(0, min(MAX_BLOCK_TICKS, swingInterval) - 1)
            beginRealBlock(player, target)
        } else if (blockTicks > 0) {
            blockTicks--
        } else {
            release()
        }
    }

    /** Release any real block and drop the visual pose. */
    fun stop() {
        release()
        started = false
        blockTicks = 0
        KillAura.renderBlock = false
        KillAura.fakeBlockRenderTarget = null
        KillAura.fakeBlockOutOfRangeTicks = 0
    }

    fun reset(discardPackets: Boolean = false) {
        val player = owner
        val sameConnection = player == null || Minecraft.getInstance().let { mc ->
            mc.player === player && mc.level === player.level() && mc.connection === player.connection
        }
        if (realBlocking && (discardPackets || !sameConnection)) {
            player?.stopUsingItem()
            setBlocking(false)
        } else {
            release()
        }
        started = false
        blockTicks = 0
        predictionTarget = null
        resetSwingTracking()
        owner = null
    }

    private fun resetSwingTracking() {
        swingInterval = MAX_BLOCK_TICKS
        swingWasActive = false
        swingStartTick = -1
        predictedSwingTick = -1
    }

    private fun beginRealBlock(player: LocalPlayer, target: LivingEntity) {
        if (realBlocking) return
        val mc = Minecraft.getInstance()
        if (mc.gameMode == null || mc.level == null) return
        // The legacy sword hooks key off this flag, so it must be set before the
        // use state starts (it decides the use duration and block animation).
        setBlocking(true)
        val rotation = RotationManager.currentRotation ?: Rotation(player.yRot, player.xRot)
        val reach = min(KillAura.blockRange.value, ENGAGE_RANGE)
        val hit = (RayCastUtil.rayCast(rotation, reach, 0f, player) as? EntityHitResult)
            ?.takeIf { it.entity === target }
        KillAuraWatchdogBlock.sendSwordBlock(player, rotation, hit)
    }

    private fun release() {
        if (!realBlocking) return
        val player = owner
        val mc = Minecraft.getInstance()
        if (player != null && mc.player === player && mc.connection === player.connection) {
            player.stopUsingItem()
            mc.gameMode?.releaseUsingItem(player)
        }
        setBlocking(false)
    }

    private fun setBlocking(value: Boolean) {
        realBlocking = value
        KillAura.blocking = value
        KillAura.blockingState = value
    }

    fun ownsUseItem(): Boolean {
        val player = owner ?: return false
        return legacyBlockingItem(player) != null
    }

    fun isLegacySwordStack(stack: ItemStack): Boolean {
        val player = owner ?: return false
        return selected() && realBlocking && player === Minecraft.getInstance().player &&
            stack === player.mainHandItem && stack.`is`(ItemTags.SWORDS)
    }

    fun legacyBlockingItem(player: LocalPlayer): ItemStack? {
        return if (player === owner && isLegacySwordStack(player.mainHandItem) &&
            player.isUsingItem && player.usedItemHand == InteractionHand.MAIN_HAND &&
            player.useItem.`is`(ItemTags.SWORDS)) player.useItem else null
    }
}

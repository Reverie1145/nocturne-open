package cn.reverie.module.combat

import cn.reverie.app.event.player.AttackEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.ModeSetting
import cn.reverie.module.NumberSetting
import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.module.combat.criticals.CriticalLowTimer
import cn.reverie.module.combat.criticals.CriticalStasis
import cn.reverie.module.movement.Fly
import cn.reverie.module.movement.NoSlow
import cn.reverie.module.movement.Sprint
import cn.reverie.module.movement.Stuck
import cn.reverie.module.world.Scaffold
import cn.reverie.util.network.ProtocolUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

/** Coordinates falling stasis, critical-hit timer cycles, and melee sprint state. */
object Criticals : Module(
    name = "Criticals",
    description = "Prepare critical hits with jumps, falling stasis, or low timer between hits",
    category = Category.COMBAT
) {
    val mode = ModeSetting("Mode", "Critical-hit preparation", "Packet Stasis", listOf("Packet Stasis", "Low Timer", "Jump"))
    val autoJump = BooleanSetting("Auto Jump", "Jump normally when a target is in range", true)
    val onlyCriticalHits = BooleanSetting("Only Critical Hits", "Wait for a valid falling critical window before attacking", true, { mode.isMode("Jump") })
    val pauseSprint = BooleanSetting("Pause Sprint", "Temporarily pause sprint during critical setup and restore it afterwards", true, { mode.isMode("Jump") && onlyCriticalHits.value })
    val targetRange = NumberSetting(
        "Target Range",
        "Maximum distance for critical setup and sprint coordination",
        3.5,
        3.0,
        6.0,
        0.1,
        { autoJump.value || !mode.isMode("Jump") || onlyCriticalHits.value && pauseSprint.value }
    )

    private var owner: LocalPlayer? = null
    private var sprintSuppressed = false
    private var sprintIntentBeforeSuppression = false
    private var attackedDuringJump = false
    private var jumpPulsePending = false
    private var pendingCriticalTarget: Entity? = null

    init {
        mode.onChanged = {
            resetLowTimer()
            Minecraft.getInstance().player?.let {
                CriticalStasis.stop(it)
                releaseSprint(it)
                CriticalStasis.attach(it)
            }
            attackedDuringJump = false
            jumpPulsePending = false
        }
        listen(AttackEvent::class.java) { event ->
            pendingCriticalTarget = null
            val player = Minecraft.getInstance().player ?: return@listen
            if (!allowsAttack(player, event.target)) event.isCancelled = true
        }
    }

    override val suffix: String
        get() = when {
            CriticalLowTimer.waiting -> CriticalLowTimer.suffix
            CriticalStasis.holding -> "Stasis"
            else -> mode.value
        }

    override fun onEnable() {
        onWorldChange()
        Minecraft.getInstance().player?.let {
            owner = it
            CriticalStasis.attach(it)
        }
    }

    @JvmStatic
    fun onUpdate(player: LocalPlayer) {
        jumpPulsePending = false
        val mc = Minecraft.getInstance()
        if (!enabled) {
            return
        }
        if (owner !== player) {
            onWorldChange()
            owner = player
            CriticalStasis.attach(player)
        }

        val target = findTarget(player)
        val packetStasis = mode.isMode("Packet Stasis")
        val canPrepare = canPrepare(player, target)

        if (!mode.isMode("Low Timer") || player.onGround()) {
            resetLowTimer()
        } else if (!canPrepare || CriticalStasis.hasPendingInterruption) {
            CriticalLowTimer.stopWaiting()
            pendingCriticalTarget = null
        } else {
            refreshLowTimer(player)
        }

        CriticalStasis.update(player, packetStasis && canPrepare)
        if (player.onGround() && attackedDuringJump) {
            attackedDuringJump = false
            releaseSprint(player)
            return
        }

        if (canPrepare && (!mode.isMode("Jump") || onlyCriticalHits.value && pauseSprint.value)) {
            if (!sprintSuppressed) {
                sprintSuppressed = true
                sprintIntentBeforeSuppression = player.isSprinting || mc.options.keySprint.isDown
            }
            player.isSprinting = false
        } else {
            releaseSprint(player)
        }

        if (!autoJump.value || !canPrepare || attackedDuringJump || CriticalStasis.holding ||
            !player.onGround() || player.isSprinting || mc.options.keyJump.isDown
        ) return

        // Let LocalPlayer process a one-tick jump input so both movement and
        // the serverbound input packet describe the same vanilla jump.
        jumpPulsePending = true
    }

    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) {
        if (shouldPauseSprint(player)) player.isSprinting = false
    }

    @JvmStatic
    fun onMoveInput(player: LocalPlayer, event: MoveInputEvent) {
        if (enabled && autoJump.value && jumpPulsePending && !CriticalStasis.holding) {
            event.jump = true
            jumpPulsePending = false
        }

        if (shouldPauseSprint(player)) {
            if (event.sprint) sprintIntentBeforeSuppression = true
            event.sprint = false
        }
        if (shouldHoldMovement()) {
            event.forward = 0f
            event.strafe = 0f
            event.jump = false
            event.sprint = false
        }
    }

    @JvmStatic
    fun allowsAttack(player: LocalPlayer, target: Entity): Boolean {
        if (!enabled || target !is LivingEntity || mode.isMode("Jump") && !onlyCriticalHits.value) return true
        if (mode.isMode("Low Timer") && (!canPrepare(player, target) || !CriticalLowTimer.allowsAttack())) return false
        if (!isCriticalWindow(player, target) || !CriticalStasis.hasFallingFrame(player)) return false
        if (!CriticalStasis.canAttackWithoutPost()) return false
        // Modern critical damage requires a charged hit even when Aura's Combat Mode is 1.8.
        return ProtocolUtil.is1_8OrOlder() || player.getAttackStrengthScale(0.5f) > 0.9f
    }

    @JvmStatic
    fun shouldPauseSprint(player: LocalPlayer): Boolean = enabled && owner === player && sprintSuppressed

    @JvmStatic fun shouldHoldMovement(): Boolean = enabled && mode.isMode("Packet Stasis") && CriticalStasis.holding

    @JvmStatic fun suppressesPacket(packet: Packet<*>): Boolean = shouldHoldMovement() && CriticalStasis.suppresses(packet)

    @JvmStatic fun onPacketSent(packet: Packet<*>) { if (enabled) CriticalStasis.onSent(packet) }

    @JvmStatic fun onPacketReceive(packet: Packet<*>) { if (enabled) CriticalStasis.onReceive(packet) }

    @JvmStatic
    fun afterPhysics(player: LocalPlayer) {
        if (!enabled) return
        CriticalStasis.afterPhysics(player)
        if (mode.isMode("Low Timer") && owner === player) refreshLowTimer(player)
    }

    @JvmStatic
    fun onAttackStart(player: LocalPlayer, target: Entity) {
        pendingCriticalTarget = null
        // Capture the accepted window before vanilla resets charge. Not every
        // client-side target emits critical particles, including translated 1.8 hits.
        if (enabled && owner === player && mode.isMode("Low Timer") &&
            allowsAttack(player, target) && isCriticalWindow(player, target)) {
            pendingCriticalTarget = target
        }
    }

    @JvmStatic
    fun onCriticalHit(player: LocalPlayer, target: Entity) {
        // Vanilla invokes LocalPlayer.crit only after a charged critical hit succeeds locally.
        if (enabled && owner === player && mode.isMode("Low Timer") && target is LivingEntity) {
            pendingCriticalTarget = target
        }
    }

    @JvmStatic
    fun onAttackComplete(player: LocalPlayer, target: Entity) {
        if (!enabled || owner !== player || target !is LivingEntity) return
        val completedCritical = pendingCriticalTarget === target
        pendingCriticalTarget = null
        CriticalStasis.afterAttack(player)
        if (!player.onGround()) attackedDuringJump = true
        if (mode.isMode("Low Timer") && completedCritical && !CriticalStasis.hasPendingInterruption) {
            CriticalLowTimer.onCriticalAttack(
                player,
                target,
                if (KillAura.enabled) KillAura.nextAttackIntervalMillis() else 0L,
                KillAura.enabled && KillAura.attackMode.isMode("1.9+")
            )
        }
    }

    /** Cap the existing timer result, preserving other modules' timer settings on release. */
    @JvmStatic
    fun applyTickTargetMillis(current: Float): Float {
        if (!CriticalLowTimer.waiting) return current
        val player = Minecraft.getInstance().player
        if (!enabled || !mode.isMode("Low Timer") || player == null || owner !== player ||
            player.onGround() || CriticalStasis.hasPendingInterruption || !canPrepare(player, findTarget(player))) {
            CriticalLowTimer.stopWaiting()
            pendingCriticalTarget = null
            return current
        }
        return CriticalLowTimer.applyTickTargetMillis(current)
    }

    @JvmStatic
    fun applyAttackStrengthScale(player: LocalPlayer, partialTicks: Float, vanilla: Float): Float {
        if (!enabled || !mode.isMode("Low Timer") || owner !== player || player.onGround() || CriticalStasis.hasPendingInterruption) return vanilla
        return CriticalLowTimer.applyAttackStrengthScale(player, partialTicks, vanilla)
    }

    override fun onDisable() {
        attackedDuringJump = false
        jumpPulsePending = false
        Minecraft.getInstance().player?.takeIf { it === owner }?.let {
            CriticalStasis.stop(it)
            releaseSprint(it)
        }
        onWorldChange()
    }

    @JvmStatic
    fun onWorldChange() {
        resetLowTimer()
        owner = null
        sprintSuppressed = false
        sprintIntentBeforeSuppression = false
        attackedDuringJump = false
        jumpPulsePending = false
        CriticalStasis.reset()
    }

    private fun resetLowTimer() {
        CriticalLowTimer.reset()
        pendingCriticalTarget = null
    }

    private fun refreshLowTimer(player: LocalPlayer) {
        CriticalLowTimer.refresh(
            player,
            if (CriticalLowTimer.waiting && KillAura.enabled) KillAura.nextAttackIntervalMillis() else 0L,
            KillAura.enabled && KillAura.attackMode.isMode("1.9+")
        )
    }

    private fun canPrepare(player: LocalPlayer, target: LivingEntity?): Boolean {
        val mc = Minecraft.getInstance()
        return target != null && target.isAlive && target.level() === player.level() &&
            player.distanceTo(target).toDouble() <= targetRange.value &&
            mc.level != null && mc.gui.screen() == null && player.isAlive && !player.isSpectator &&
            !player.isInWater && !player.isInLava && !player.onClimbable() &&
            !player.isMobilityRestricted && !player.isPassenger && !player.isShiftKeyDown &&
            !player.abilities.flying && !player.isFallFlying && !player.isUsingItem &&
            !Scaffold.enabled && !Fly.enabled && !Stuck.enabled &&
            (mode.isMode("Jump") || !hasCompetingPacketControl())
    }

    private fun releaseSprint(player: LocalPlayer) {
        val restore = sprintSuppressed && sprintIntentBeforeSuppression
        sprintSuppressed = false
        sprintIntentBeforeSuppression = false
        val mc = Minecraft.getInstance()
        val moving = mc.options.keyUp.isDown || Sprint.enabled && Sprint.mode.isMode("Omni") &&
            (mc.options.keyDown.isDown || mc.options.keyLeft.isDown || mc.options.keyRight.isDown)
        if (restore && moving && player.isAlive && !player.isSpectator && !player.isShiftKeyDown &&
            !player.isMobilityRestricted && !player.isUsingItem &&
            (player.abilities.mayfly || player.foodData.hasEnoughFood()) && !KillAura.shouldPauseSprint(player)) {
            player.isSprinting = true
        }
    }

    private fun hasCompetingPacketControl(): Boolean = KillAura.hasWatchdogAutoBlock() ||
        KnockbackDisplace.enabled && KnockbackDisplace.blink.value ||
        NoSlow.enabled && (NoSlow.blink.value || NoSlow.blinkBlock.value)

    private fun isCriticalWindow(player: LocalPlayer, target: Entity): Boolean =
        target is LivingEntity && player.fallDistance > 0.0f && !player.onGround() &&
            !player.onClimbable() && !player.isInWater && !player.isInLava && !player.isMobilityRestricted &&
            !player.isPassenger && !player.isSprinting && !player.abilities.flying && !player.isFallFlying

    private fun findTarget(player: LocalPlayer): LivingEntity? {
        val mc = Minecraft.getInstance()
        val auraTarget = KillAura.target?.takeIf {
            KillAura.enabled && it.isAlive && it.level() === player.level() &&
                player.distanceTo(it).toDouble() <= targetRange.value
        }
        val crosshairTarget = (mc.crosshairPickEntity as? LivingEntity)?.takeIf {
            it.isAlive && it.level() === player.level() &&
                player.distanceTo(it).toDouble() <= targetRange.value
        }
        return auraTarget ?: crosshairTarget
    }
}

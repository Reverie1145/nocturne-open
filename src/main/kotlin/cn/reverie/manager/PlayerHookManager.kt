package cn.reverie.manager

import cn.reverie.module.combat.AimAssist
import cn.reverie.module.combat.Backtrack
import cn.reverie.module.combat.BlockHit
import cn.reverie.module.combat.BowAim
import cn.reverie.module.combat.KillAura
import cn.reverie.module.combat.KnockbackDisplace
import cn.reverie.module.combat.Criticals
import cn.reverie.module.combat.ProjectileAura
import cn.reverie.module.combat.Velocity
import cn.reverie.module.misc.Disabler
import cn.reverie.module.movement.Fly
import cn.reverie.module.movement.InventoryMove
import cn.reverie.module.movement.NoSlow
import cn.reverie.module.movement.Sprint
import cn.reverie.module.movement.Stuck
import cn.reverie.module.player.AutoMLG
import cn.reverie.module.player.BedDefender
import cn.reverie.module.player.ChestAura
import cn.reverie.module.player.AutoTool
import cn.reverie.module.world.Scaffold
import cn.reverie.module.world.scaffold.useScaffoldItemOn
import cn.reverie.util.player.PlayerUtil
import cn.reverie.util.network.ProtocolUtil
import cn.reverie.util.rotation.RotationManager
import cn.reverie.util.rotation.Rotation
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

/** Preserves the feature priority/order at player mixin injection points. */
object PlayerHookManager {
    @JvmStatic
    fun onPlayerTick(player: LocalPlayer) {
        Stuck.onTick(player)
        InventoryMove.moveFreely()
        if (player.onGround()) {
            PlayerUtil.offGroundTicks = 0
            PlayerUtil.onGroundTicks++
        } else {
            PlayerUtil.onGroundTicks = 0
            PlayerUtil.offGroundTicks++
        }
        NoSlow.onPreUpdate()
        AutoMLG.onUpdate(player)
        Scaffold.onUpdate(player)
        Criticals.onUpdate(player)
        Velocity.onBeforeCombat(player)
        KillAura.onUpdate(player)
        KnockbackDisplace.onUpdate(player)
        Backtrack.onUpdate(player)
        ProjectileAura.onUpdate(player)
        BowAim.onUpdate(player)
        Fly.onTick(player)
        ChestAura.onTick(player)
        Disabler.onUpdate(player)
        Velocity.onUpdate(player)
    }

    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) {
        BlockHit.onLivingUpdate(player)
        Scaffold.onLivingUpdate(player)
        KillAura.onLivingUpdate(player)
        ProjectileAura.onLivingUpdate(player)
        RotationHookManager.onLivingUpdate(player)
        Velocity.onLivingUpdate(player)
        Disabler.onLivingUpdate(player)
        Sprint.onLivingUpdate(player)
        Criticals.onLivingUpdate(player)
    }

    @JvmStatic
    fun onAimAssistRenderFrame() {
        Minecraft.getInstance().player?.let { AimAssist.onRenderFrame(it) }
    }

    @JvmStatic fun applySilentPhysicsYaw(player: LocalPlayer): Float? = Velocity.silentPhysicsYaw(player)

    @JvmStatic fun afterPlayerPhysics(player: LocalPlayer) = Criticals.afterPhysics(player)

    /** Resolve fresh rotation and Aura actions before input/physics changes the attack origin. */
    @JvmStatic
    fun prepareAuraMovement(player: LocalPlayer): FloatArray? {
        if (!KillAura.hasWatchdogAutoBlock()) return null
        NoSlow.prepareAuraControl(player)
        val rotation = RotationManager.onMotionPre(player)
        KillAura.onPreTick(player, Rotation(rotation[0], rotation[1]))
        return rotation
    }

    @JvmStatic fun afterPlayerTickPackets() = KillAura.onPostTick()
    @JvmStatic fun shouldPauseAuraSprint(player: LocalPlayer): Boolean = KillAura.shouldPauseSprint(player)

    @JvmStatic
    fun shouldUseAuraLegacySword(stack: ItemStack, user: LivingEntity?): Boolean =
        (user == null || user === Minecraft.getInstance().player) && KillAura.isLegacySwordStack(stack)

    @JvmStatic
    fun auraLegacyBlockingItem(entity: LivingEntity): ItemStack? =
        if (entity is LocalPlayer) KillAura.legacyBlockingItem(entity) else null

    @JvmStatic fun shouldSendAuraIdlePacket(): Boolean =
        KillAura.hasWatchdogAutoBlock() && ProtocolUtil.is1_8OrOlder()

    @JvmStatic
    fun beforeMotionPacket(player: LocalPlayer) {
        NoSlow.onPreMotion(player)
        AutoMLG.onPreMotion(player)
        KillAura.onPreMotion(player)
    }

    @JvmStatic fun resolveMotionRotation(player: LocalPlayer): FloatArray = RotationManager.onMotionPre(player)
    @JvmStatic fun autoMlgWillPlace(player: LocalPlayer): Boolean = AutoMLG.shouldPlace(player)
    @JvmStatic fun onScaffoldPostPlace(player: LocalPlayer): Boolean = Scaffold.onPostPlace(player)

    @JvmStatic fun placeBedDefenderBeforePacket(player: LocalPlayer, yaw: Float, pitch: Float): Boolean =
        BedDefender.placeBeforeMotion(player, yaw, pitch)

    @JvmStatic
    fun useScaffoldItemOn(gameMode: MultiPlayerGameMode, player: LocalPlayer, hand: InteractionHand, hit: BlockHitResult): InteractionResult =
        Scaffold.useScaffoldItemOn(gameMode, player, hand, hit)

    @JvmStatic
    fun onPostMotionPacket(player: LocalPlayer) {
        KillAura.onPostMotion(player)
        Velocity.onPostMotion()
        ChestAura.afterMotion(player)
        BedDefender.afterMotion(player)
    }

    @JvmStatic fun placeAutoMlgBeforePacket(player: LocalPlayer, yaw: Float, pitch: Float) = AutoMLG.placeBeforePacket(player, yaw, pitch)
    @JvmStatic fun placeAutoMlgAfterPacket(player: LocalPlayer) = AutoMLG.placeAfterFlying(player)

    @JvmStatic
    fun onVisualTick(player: LocalPlayer) {
        KillAura.onVisualTick(player)
        Scaffold.onVisualTick(player)
        ProjectileAura.onVisualTick(player)
        BowAim.onVisualTick(player)
    }

    @JvmStatic fun passengerRotation(player: LocalPlayer): FloatArray = RotationManager.onMotionPre(player)
    @JvmStatic
    fun shouldOmniSprint(): Boolean = Scaffold.shouldOmniSprint() || Sprint.shouldOmniSprint()

    @JvmStatic fun shouldKeepSprint(player: LocalPlayer): Boolean = KillAura.shouldKeepSprint(player)

    @JvmStatic fun isStuckEnabled(): Boolean = shouldHoldStuckPositionUpdate()
    @JvmStatic fun shouldHoldStuckPositionUpdate(): Boolean = Stuck.shouldHoldPositionUpdate() || Criticals.shouldHoldMovement()
    @JvmStatic fun shouldCancelStuckTravel(): Boolean = Stuck.shouldCancelTravel() || Criticals.shouldHoldMovement()
    @JvmStatic fun shouldCancelStuckEntityMove(): Boolean = Stuck.shouldCancelEntityMove() || Criticals.shouldHoldMovement()
    @JvmStatic fun shouldClearStuckVelocity(): Boolean = Stuck.shouldCancelTravel() &&
        !Criticals.shouldHoldMovement() && Stuck.shouldClearVelocityOnTravelCancel()

    @JvmStatic fun onSendPosition(player: LocalPlayer, yaw: Float, pitch: Float) = NoSlow.onSendPosition(player, yaw, pitch)

    @JvmStatic fun onItemUseSlowdown(original: Float): Float = NoSlow.onItemUseSlowdown(original)


    @JvmStatic fun onAutoToolStartDestroy(pos: BlockPos, direction: Direction) = AutoTool.onStartDestroyBlock(pos, direction)
    @JvmStatic fun onAutoToolContinueDestroy(pos: BlockPos, direction: Direction) = AutoTool.onContinueDestroyBlock(pos, direction)
    @JvmStatic fun onAutoToolStopDestroy() = AutoTool.onStopDestroyBlock()
    @JvmStatic fun onAutoToolDestroy(pos: BlockPos) = AutoTool.onDestroyBlock(pos)
    @JvmStatic
    fun onAutoToolPreAttack(player: LocalPlayer, target: Entity) {
        AutoTool.onPreAttack(player, target)
        Criticals.onAttackStart(player, target)
    }

    @JvmStatic fun onCriticalHit(player: LocalPlayer, target: Entity) = Criticals.onCriticalHit(player, target)

    @JvmStatic fun applyCriticalAttackStrengthScale(player: LocalPlayer, partialTicks: Float, vanilla: Float): Float =
        Criticals.applyAttackStrengthScale(player, partialTicks, vanilla)

    @JvmStatic
    fun onAutoToolPostAttack(player: LocalPlayer, target: Entity) {
        AutoTool.onPostAttack(player, target)
        KillAura.onAttackComplete(player, target)
        Criticals.onAttackComplete(player, target)
    }
    @JvmStatic fun lockSprintFov(vanilla: Float): Float = Scaffold.onFovModifier(vanilla)
}

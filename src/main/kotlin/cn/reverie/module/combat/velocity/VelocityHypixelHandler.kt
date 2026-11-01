package cn.reverie.module.combat.velocity

import cn.reverie.module.combat.*

import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.util.player.MovementUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.hypot
    internal fun Velocity.onHypixelMotion(player: LocalPlayer, movement: Vec3, explode: Boolean = false) {
        val hx = hypot(movement.x, movement.z)
        if (hx < 0.02 && abs(movement.y) < 0.05) return
        if (hypixelBlocked(player)) return

        hypixelKbX = if (explode) hypixelKbX + movement.x else movement.x
        hypixelKbZ = if (explode) hypixelKbZ + movement.z else movement.z
        hypixelSeenMotionThisHurt = true
        if (!hypixelChanceRolled) {
            hypixelChanceRolled = true
            hypixelWantJump = hypixelRollChance()
        }

        if (hypixelAttackReduce.value) hypixelReducePending = true

        if (player.onGround() && !hypixelJumpedThisHurt && hypixelWantJump) {
            hypixelJumpPending = true
            hypixelSprintJump = hypixelSprint.value && hypixelSprintReducesKb(player, hypixelKbX, hypixelKbZ)
        } else if (hypixelReverse.value && hx >= 0.02) {
            hypixelReverseTicks = 2
        }
    }

    internal fun Velocity.onHypixelUpdate(player: LocalPlayer) {
        if (player.hurtTime <= 0) {
            hypixelSeenMotionThisHurt = false
            hypixelJumpPending = false
            hypixelSprintJump = false
            hypixelJumpedThisHurt = false
            hypixelWantJump = false
            hypixelChanceRolled = false
            hypixelReducePending = false
            hypixelReducedThisHurt = false
        }

        if (!hypixelJumpPending &&
            !hypixelJumpedThisHurt &&
            hypixelSeenMotionThisHurt &&
            hypixelWantJump &&
            player.onGround() &&
            player.hurtTime >= 8 &&
            !hypixelBlocked(player)
        ) {
            hypixelJumpPending = true
            hypixelSprintJump = hypixelSprint.value && hypixelSprintReducesKb(player, hypixelKbX, hypixelKbZ)
        }

        // Grim PacketPlayerAttack：AttackSlow 看 lastSprinting（上一 tick 的 sprint 包），
        // 不是本地刚 setSprinting。无 knockback 附魔时同 tick 第二刀 maxAttackSlow 锁死为 1。
        // 只在已经冲刺时吃原版 *0.6；KA 出过刀就不再打。
        if (hypixelReducePending && hypixelAttackReduce.value && !hypixelReducedThisHurt) {
            if (KillAura.attackedThisTick) {
                hypixelReducePending = false
                hypixelReducedThisHurt = true
            } else if (tryAttackReduce(player)) {
                hypixelReducePending = false
                hypixelReducedThisHurt = true
            }
        }
    }

    internal fun Velocity.onHypixelMoveInput(player: LocalPlayer, event: MoveInputEvent) {
        if (hypixelJumpPending && player.onGround() && !hypixelBlocked(player)) {
            event.jump = true
            if (hypixelSprint.value) {
                event.sprint = hypixelSprintJump
            }
            if (hypixelReverse.value && !hypixelSprintJump) {
                applyVanillaReverse(player, event)
            }
            hypixelJumpPending = false
            hypixelJumpedThisHurt = true
            if (hypixelReverse.value) hypixelReverseTicks = 2
            return
        }

        if (hypixelReverseTicks > 0 && hypixelReverse.value) {
            applyVanillaReverse(player, event)
            hypixelReverseTicks--
        }
    }

    /**
     * Grim 2.3.73 PacketPlayerAttack：
     * lastSprinting && (1.8 或无冷却) => minAttackSlow=maxAttackSlow=1，预测 XZ*0.6。
     * lastSprinting 在上一 tick flying 预测结束时从 isSprinting 拷来，START_SPRINTING 只改当前 isSprinting。
     * 同 tick 先 START_SPRINT 再 ATTACK：Grim 仍认为没减速，客户端却 *0.6 => AntiKB flag。
     * 无 KB 附魔第二刀不会再加 AttackSlow。所以：已冲刺才打，且一伤只一刀。
     */
    internal fun Velocity.tryAttackReduce(player: LocalPlayer): Boolean {
        if (KillAura.attackedThisTick) return false
        if (hypixelBlocked(player)) return false
        if (!player.isSprinting) return false
        if (player.foodData.foodLevel <= 6) return false
        val target = hypixelReduceTarget(player) ?: return false
        val gameMode = Minecraft.getInstance().gameMode ?: return false
        gameMode.attack(player, target)
        player.swing(InteractionHand.MAIN_HAND)
        return true
    }

    internal fun Velocity.hypixelReduceTarget(player: LocalPlayer): Entity? {
        val ka = KillAura.target
        if (ka != null && ka.isAlive && ka !== player && player.distanceTo(ka) <= 3.0f) {
            return ka
        }
        val pointed = Minecraft.getInstance().crosshairPickEntity
        if (pointed is LivingEntity && pointed.isAlive && pointed !== player && player.distanceTo(pointed) <= 3.0f) {
            return pointed
        }
        return null
    }

    internal fun Velocity.hypixelRollChance(): Boolean {
        val chance = hypixelChance.value.toInt()
        return chance >= 100 || ThreadLocalRandom.current().nextInt(100) < chance
    }

    internal fun Velocity.hypixelSprintReducesKb(player: LocalPlayer, kbX: Double, kbZ: Double): Boolean {
        if (hypot(kbX, kbZ) < 0.02) return false
        val yaw = player.yRot * (Math.PI / 180.0)
        val sx = -Mth.sin(yaw) * 0.2
        val sz = Mth.cos(yaw) * 0.2
        return sx * kbX + sz * kbZ < -0.002
    }

    internal fun Velocity.applyVanillaReverse(player: LocalPlayer, event: MoveInputEvent) {
        val kbX = hypixelKbX
        val kbZ = hypixelKbZ
        if (hypot(kbX, kbZ) < 0.02) return
        if (event.forward == 0f && event.strafe == 0f) return

        val kbYaw = Math.toDegrees(kotlin.math.atan2(-kbX, kbZ)).toFloat()
        val reverseYaw = Mth.wrapDegrees(kbYaw + 180f)
        val current = Mth.wrapDegrees(Math.toDegrees(MovementUtil.getDirection(player.yRot, event.forward, event.strafe)).toFloat())
        val error = abs(Mth.wrapDegrees(current - reverseYaw))
        if (error <= 35f) return

        var bestForward = event.forward
        var bestStrafe = event.strafe
        var bestError = error
        var f = -1f
        while (f <= 1f) {
            var s = -1f
            while (s <= 1f) {
                if (f != 0f || s != 0f) {
                    val predicted = Mth.wrapDegrees(
                        Math.toDegrees(MovementUtil.getDirection(player.yRot, f, s)).toFloat()
                    )
                    val diff = abs(Mth.wrapDegrees(predicted - reverseYaw))
                    if (diff < bestError) {
                        bestError = diff
                        bestForward = f
                        bestStrafe = s
                    }
                }
                s += 1f
            }
            f += 1f
        }
        event.forward = bestForward
        event.strafe = bestStrafe
    }

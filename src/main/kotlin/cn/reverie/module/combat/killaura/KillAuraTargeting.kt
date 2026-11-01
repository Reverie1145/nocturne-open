package cn.reverie.module.combat.killaura

import cn.reverie.module.combat.AntiBot
import cn.reverie.module.combat.Criticals
import cn.reverie.module.combat.KillAura
import cn.reverie.module.combat.ProjectileAura
import cn.reverie.module.combat.Velocity
import cn.reverie.module.player.Teams
import cn.reverie.util.misc.MathUtil
import cn.reverie.util.rotation.*
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import java.util.Comparator
import kotlin.math.max
    internal fun KillAura.selectTarget() {
        if (target == null || targets.isEmpty()) {
            switchTimer.time = 0
        }

        if (targetMode.isMode("Switch")) {
            if (switchTimer.hasTimeElapsed(switchDelay.value.toLong())) {
                val index = when (switchSelection.value) {
                    "Sequential" -> { switchIndex = (switchIndex + 1) % targets.size; switchIndex }
                    "First" -> 0
                    "Last" -> targets.lastIndex
                    else -> if (targets.size > 1) (Math.random() * targets.size).toInt() else 0
                }
                target = if (targets.isNotEmpty()) targets[index] else null
                switchTimer.reset()
            }
        } else {
            target = if (targets.isNotEmpty()) targets.first() else null
        }
    }

    internal fun KillAura.canAttack(player: LocalPlayer, target: LivingEntity): Boolean {
        // ProjectileAura may leave its throw item selected for a configured
        // switch-back delay. Do not let that interval turn sword attacks into
        // weak projectile-hand hits; ProjectileAura restores the slot this tick.
        if (ProjectileAura.isWaitingToRestoreSlot(player)) {
            return false
        }
        if (Velocity.isBufferTargetRemoved(target)) return false
        val targetBox = combatBox(target)
        val distance = VecCalculation.newGetDistanceToEntityBoxSafe(player, target, wallRange.value, targetBox)
        val range = attackRange.value

        if (distance > range) {
            return false
        }
        if (distance < minTargetRange.value || target.hurtTime > maxHurtTimeAttack.value ||
            target.hurtTime < minHurtTimeAttack.value || onlyGroundAttack.value && !player.onGround() ||
            onlySwordAttack.value && !isHoldingSword(player) ||
            pauseWhileUsing.value && player.isUsingItem) return false

        if (!Criticals.allowsAttack(player, target)) return false

        if (rayCast.value) {
            val current = RotationManager.currentRotation ?: Rotation(player.yRot, player.xRot)
            val rayCastRange = max(range, distance + 1.0)
            val result = rayCastCombat(current, rayCastRange, player)

            if (result !is EntityHitResult || result.entity !== target) {
                return false
            }
        }

        return true
    }

    internal fun KillAura.canBlockImpl(player: LocalPlayer, target: LivingEntity?): Boolean {
        if (autoBlockMode.isMode("None") || target == null) return false
        if (!canUseBlockItem(player)) return false
        if (autoBlockMode.isMode("WatchdogBW")) return KillAuraWatchdogBlock.wantsBlock(player)
        return combatDistance(player, target) <= blockRange.value
    }

    internal fun KillAura.updateFakeBlockRenderState(player: LocalPlayer, target: LivingEntity?) {
        if (target == null || !target.isAlive || target.level() !== player.level() || !isHoldingSword(player)) {
            renderBlock = false
            fakeBlockRenderTarget = null
            fakeBlockOutOfRangeTicks = 0
            return
        }

        if (canBlock(player, target)) {
            renderBlock = true
            fakeBlockRenderTarget = target
            fakeBlockOutOfRangeTicks = 0
            return
        }

        if (!renderBlock || fakeBlockRenderTarget !== target) {
            renderBlock = false
            fakeBlockRenderTarget = null
            fakeBlockOutOfRangeTicks = 0
            return
        }

        // Hits can knock the target across the exact range boundary for a tick.
        // Require a sustained excursion before dropping the visual block state.
        val distance = combatDistance(player, target)
        if (distance > blockRange.value + 0.35) {
            fakeBlockOutOfRangeTicks++
        } else {
            fakeBlockOutOfRangeTicks = 0
        }
        if (fakeBlockOutOfRangeTicks >= 3) {
            renderBlock = false
            fakeBlockRenderTarget = null
            fakeBlockOutOfRangeTicks = 0
        }
    }
    internal fun KillAura.wantsRealBlockImpl(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return KillAuraWatchdogBlock.wantsBlock(player)
    }



    internal fun KillAura.updateTargets(player: LocalPlayer) {
        targets.clear()
        val level = player.level()
        val searchBox = player.boundingBox.inflate(rotationRange.value + 4.0)
        val entities = if (Velocity.usesBufferedTargetPositions()) Minecraft.getInstance().level?.entitiesForRendering() ?: emptyList()
        else level.getEntities(player, searchBox)
        for (entity in entities) {
            if (entity is LivingEntity && filter(player, entity)) {
                targets.add(entity)
            }
        }

        if (targets.isNotEmpty()) {
            targets.sortWith(sortTargets(player, priority.value))
            val limit = maxCandidates.getInt()
            if (limit > 0 && targets.size > limit) targets.subList(limit, targets.size).clear()
        }
    }

    internal fun KillAura.sortTargets(player: LocalPlayer, priority: String): Comparator<LivingEntity> {
        return when (priority) {
            "Health" -> Comparator.comparingDouble { entity -> (entity.health + entity.absorptionAmount).toDouble() }
            "Fov" -> Comparator.comparingDouble { entity -> getRotationDifference(player, entity) }
            "LivingTime" -> Comparator.comparingInt<LivingEntity> { entity -> entity.tickCount }.reversed()
            "Armor" -> Comparator.comparingInt { entity -> entity.armorValue }
            else -> Comparator.comparingDouble { entity -> getDistanceToEntity(player, entity) }
        }
    }

    internal fun KillAura.filter(player: LocalPlayer, entity: LivingEntity): Boolean {
        if (entity === player) return false
        if (Velocity.isBufferTargetRemoved(entity)) return false
        if (AntiBot.isBot(entity)) return false
        if (Teams.isTeammate(entity)) return false
        if (!entity.isAlive || entity.isSpectator || entity.isDeadOrDying || entity.health <= 0f) return false
        if (!invisibles.value && entity.isInvisible) return false
        if (entity.health < minTargetHealth.value || maxTargetHealth.value > 0.0 && entity.health > maxTargetHealth.value) return false
        if (entity.tickCount < minTargetAge.value || maxTargetAge.value > 0.0 && entity.tickCount > maxTargetAge.value) return false
        val targetBox = combatBox(entity)
        val targetMovement = Velocity.bufferedMovement(entity) ?: entity.deltaMovement
        if (maxTargetSpeed.value > 0.0 && targetMovement.length() > maxTargetSpeed.value) return false
        if (kotlin.math.abs(targetBox.minY - player.y) > maxTargetHeight.value) return false
        if (maxTargetFov.value < 180.0 && getRotationDifference(player, entity) > maxTargetFov.value) return false
        if (requireLineOfSight.value) {
            val visible = if (Velocity.usesBufferedTargetPositions()) {
                VecCalculation.newGetPointToEntityBoxSafe(player, entity, 0.0, targetBox) != null
            } else player.hasLineOfSight(entity)
            if (!visible) return false
        }

        when (entity) {
            is Player -> if (!players.value) return false
            is Monster -> if (!monsters.value) return false
            is Animal -> if (!animals.value) return false
            is ArmorStand -> return false
            else -> if (!monsters.value && !animals.value) return false
        }

        val distance = VecCalculation.newGetDistanceToEntityBoxSafe(player, entity, wallRange.value, targetBox)
        return distance in minTargetRange.value..rotationRange.value
    }

    internal fun KillAura.getDistanceToEntity(player: LocalPlayer, entity: LivingEntity): Double {
        return combatDistance(player, entity)
    }

    internal fun KillAura.getRotationDifference(player: LocalPlayer, entity: LivingEntity): Double {
        val point = VecCalculation.newGetPointToEntityBoxSafe(player, entity, wallRange.value, combatBox(entity)) ?: return Double.MAX_VALUE
        return RotationUtil.getRotationDifference(
            RotationUtil.getRotationOrElseMC(),
            RotationUtil.vecToRotation(point, false)
        )
    }

    internal fun KillAura.getCps(): Long {
        val low = minCps.value.toLong()
        val high = maxCps.value.toLong()
        val min = minOf(low, high)
        val max = maxOf(low, high)
        return MathUtil.getRandomInRange(min, max)
    }

    internal fun KillAura.isHoldingSword(player: LocalPlayer): Boolean {
        return player.mainHandItem.`is`(ItemTags.SWORDS)
    }

    internal fun KillAura.canUseBlockItem(player: LocalPlayer): Boolean = isHoldingSword(player)

    internal fun KillAura.combatBox(entity: LivingEntity): AABB =
        Velocity.bufferedHitbox(entity) ?: entity.boundingBox

    internal fun KillAura.combatDistance(player: LocalPlayer, entity: LivingEntity): Double =
        VecCalculation.newGetDistanceToEntityBoxSafe(player, entity, wallRange.value, combatBox(entity))

    internal fun KillAura.rayCastCombat(rotation: Rotation, range: Double, player: LocalPlayer): net.minecraft.world.phys.HitResult? {
        if (!Velocity.usesBufferedTargetPositions()) return RayCastUtil.rayCast(rotation, range, 0f, player)
        return RayCastUtil.rayCast(
            rotation,
            range,
            0f,
            player,
            boxOverride = { entity -> (entity as? LivingEntity)?.let { combatBox(it) } },
            entityAllowed = { entity -> entity !is LivingEntity || !Velocity.isBufferTargetRemoved(entity) }
        )
    }

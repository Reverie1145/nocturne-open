package cn.reverie.manager

import cn.reverie.module.combat.BowAim
import cn.reverie.module.combat.KillAura
import cn.reverie.module.combat.KnockbackDisplace
import cn.reverie.module.combat.ProjectileAura
import cn.reverie.module.player.AutoMLG
import cn.reverie.module.player.BedBreaker
import cn.reverie.module.player.BedDefender
import cn.reverie.module.player.ChestAura
import cn.reverie.module.world.Scaffold
import cn.reverie.util.rotation.MoveFix
import cn.reverie.util.rotation.RotationManager
import net.minecraft.client.player.LocalPlayer

internal enum class RotationOwner { SCAFFOLD, AUTO_MLG, KNOCKBACK_DISPLACE, KILL_AURA, BOW_AIM, PROJECTILE_AURA, BED_BREAKER, CHEST_AURA, BED_DEFENDER, NONE }

/** Lazy predicates preserve the established short-circuit feature priority. */
internal fun selectRotationOwner(
    scaffoldEnabled: Boolean,
    killAura: () -> Boolean,
    bowAim: () -> Boolean,
    projectileAura: () -> Boolean,
    bedBreaker: () -> Boolean,
    chestAura: () -> Boolean = { false },
    autoMlg: () -> Boolean = { false },
    bedDefender: () -> Boolean = { false },
    knockback: () -> Boolean = { false }
): RotationOwner = when {
    scaffoldEnabled -> RotationOwner.SCAFFOLD
    autoMlg() -> RotationOwner.AUTO_MLG
    knockback() -> RotationOwner.KNOCKBACK_DISPLACE
    killAura() -> RotationOwner.KILL_AURA
    bowAim() -> RotationOwner.BOW_AIM
    projectileAura() -> RotationOwner.PROJECTILE_AURA
    bedBreaker() -> RotationOwner.BED_BREAKER
    chestAura() -> RotationOwner.CHEST_AURA
    bedDefender() -> RotationOwner.BED_DEFENDER
    else -> RotationOwner.NONE
}

/** Selects the active feature's rotation request in the established priority order. */
object RotationHookManager {
    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) {
        val scaffold = Scaffold
        when (selectRotationOwner(
            scaffold.enabled,
            KillAura::shouldRotate,
            BowAim::shouldRotate,
            ProjectileAura::shouldRotate,
            BedBreaker::shouldRotate,
            ChestAura::shouldRotate,
            AutoMLG::shouldRotate,
            BedDefender::shouldRotate,
            KnockbackDisplace::shouldRotate
        )) {
            RotationOwner.SCAFFOLD -> {
                if (scaffold.fast.value) {
                    RotationManager.clearRotationTarget()
                } else if (scaffold.isInstantRotation()) {
                    RotationManager.correctMovement = scaffold.getEffectiveMoveFix()
                } else if (scaffold.canRotation && scaffold.rotations != null) {
                    RotationManager.setRotations(
                        scaffold.rotations,
                        scaffold.getRotationSpeed().toDouble(),
                        scaffold.getEffectiveMoveFix()
                    )
                } else {
                    RotationManager.clearRotationTarget()
                }
            }
            RotationOwner.AUTO_MLG -> AutoMLG.getRotation()?.let {
                RotationManager.setRotations(
                    floatArrayOf(it.yaw, it.pitch), 180.0, AutoMLG.getMoveFix()
                )
            }
            RotationOwner.KNOCKBACK_DISPLACE -> KnockbackDisplace.rotation()?.let {
                RotationManager.setRotations(floatArrayOf(it.yaw, it.pitch), 180.0, MoveFix.Silent)
            }
            RotationOwner.KILL_AURA -> KillAura.rotations?.let {
                RotationManager.setRotations(
                    floatArrayOf(it.yaw, it.pitch), KillAura.getRotationSpeed(), KillAura.getMoveFix()
                )
            }
            RotationOwner.BOW_AIM -> BowAim.rotations?.let {
                RotationManager.setRotations(
                    floatArrayOf(it.yaw, it.pitch), BowAim.getRotationSpeed(), BowAim.getMoveFix()
                )
            }
            RotationOwner.PROJECTILE_AURA -> ProjectileAura.rotations?.let {
                RotationManager.setRotations(
                    floatArrayOf(it.yaw, it.pitch), ProjectileAura.getRotationSpeed(), ProjectileAura.getMoveFix()
                )
            }
            RotationOwner.BED_BREAKER -> BedBreaker.getRotation()?.let {
                RotationManager.setRotations(
                    floatArrayOf(it.yaw, it.pitch), BedBreaker.getRotationSpeed(), MoveFix.Silent
                )
            }
            RotationOwner.CHEST_AURA -> ChestAura.getRotation()?.let {
                RotationManager.setRotations(floatArrayOf(it.yaw, it.pitch), ChestAura.rotationSpeed.value, MoveFix.Silent)
            }
            RotationOwner.BED_DEFENDER -> BedDefender.getRotation()?.let {
                RotationManager.setRotations(floatArrayOf(it.yaw, it.pitch), BedDefender.getRotationSpeed(), BedDefender.getMoveFix())
            }
            RotationOwner.NONE -> RotationManager.clearRotationTarget()
        }

        RotationManager.advanceLivingUpdate(
            player,
            preserveFeatureRotation = scaffold.enabled && scaffold.isInstantRotation()
        )
    }
}

package cn.reverie.module.combat.killaura

/** Grim PacketOrderI requires a flying boundary between release and attack/use. */
internal class KillAuraAttackBoundary {
    @Volatile private var released = false

    val canAttackOrUse: Boolean get() = !released

    fun onRelease() { released = true }
    fun onMovement() { released = false }
}

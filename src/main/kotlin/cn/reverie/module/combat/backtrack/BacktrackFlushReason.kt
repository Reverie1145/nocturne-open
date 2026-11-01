package cn.reverie.module.combat.backtrack

enum class BacktrackFlushReason {
    MODULE_DISABLE,
    WORLD_CHANGE,
    DISCONNECT,
    SETBACK,
    EXPLOSION,
    TARGET_DESTROYED,
    TARGET_TELEPORT,
    VELOCITY_SESSION,
    DELAY_EXPIRED,
    NO_TARGET,
    BETTER_REAL_POSITION,
    UNHITTABLE,
    RELEASE_ON_HIT,
    UNSAFE,
    MANUAL
}

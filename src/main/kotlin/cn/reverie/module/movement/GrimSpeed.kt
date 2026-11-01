package cn.reverie.module.movement

import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot

/** Toggle on to bank horizontal velocity; toggle off to release it. */
object GrimSpeed : Module(
    name = "GrimSpeed",
    description = "Store horizontal momentum and release it on disable",
    category = Category.MOVEMENT
) {
    val minSpeed = NumberSetting("Min Speed", "Minimum speed eligible for storage (blocks/tick)", 0.32, 0.1, 1.5, 0.01)

    private var storedPlayer: LocalPlayer? = null
    private var storedHorizontal: Vec3? = null

    override val suffix: String?
        get() = if (storedHorizontal == null) "Waiting" else "Stored"

    override fun onEnable() {
        clearStorage()
        Minecraft.getInstance().player?.let(::storeIfFastEnough)
    }

    /** Allows enabling the module before an incoming impulse arrives. */
    @JvmStatic
    fun onMoveInput(player: LocalPlayer, event: MoveInputEvent) {
        if (!enabled || storedHorizontal != null) return
        storeIfFastEnough(player)
    }

    override fun onDisable() {
        val player = Minecraft.getInstance().player
        val momentum = storedHorizontal
        if (player != null && player === storedPlayer && momentum != null) {
            player.deltaMovement = Vec3(momentum.x, player.deltaMovement.y, momentum.z)
        }
        clearStorage()
    }

    private fun storeIfFastEnough(player: LocalPlayer) {
        if (player.isPassenger || player.isFallFlying || player.abilities.flying) return
        val velocity = player.deltaMovement
        if (hypot(velocity.x, velocity.z) < minSpeed.value) return

        storedPlayer = player
        storedHorizontal = Vec3(velocity.x, 0.0, velocity.z)
        player.deltaMovement = Vec3(0.0, velocity.y, 0.0)
    }

    private fun clearStorage() {
        storedPlayer = null
        storedHorizontal = null
    }
}

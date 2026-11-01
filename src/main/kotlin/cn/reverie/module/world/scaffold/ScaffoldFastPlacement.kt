package cn.reverie.module.world.scaffold

import cn.reverie.mixin.client.MultiPlayerGameModeAccessor
import cn.reverie.module.world.Scaffold
import cn.reverie.util.rotation.RotationUtil
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult

/** Keep restoration adjacent to the interaction, including PASS/FAIL and exceptions. */
internal inline fun <T> withScaffoldFastRotation(
    yaw: Float,
    pitch: Float,
    originalYaw: Float,
    originalPitch: Float,
    sendRotation: (Float, Float) -> Unit,
    interact: () -> T
): T {
    try {
        sendRotation(yaw, pitch)
        return interact()
    } finally {
        sendRotation(originalYaw, originalPitch)
    }
}

/** Used by both Direct placement and the vanilla Human right-click path. */
internal fun Scaffold.useScaffoldItemOn(
    gameMode: MultiPlayerGameMode,
    player: LocalPlayer,
    hand: InteractionHand,
    hit: BlockHitResult
): InteractionResult {
    val info = data
    if (!enabled || !fast.value || !canPlace || info == null ||
        hit.blockPos != info.blockPos || hit.direction != info.facing ||
        !isValidBlock(player.getItemInHand(hand)) ||
        !player.level().worldBorder.isWithinBounds(hit.blockPos)) {
        return gameMode.useItemOn(player, hand, hit)
    }

    val rotation = RotationUtil.getRotations(player.eyePosition, hit.location)
    val originalYaw = player.yRot
    val originalPitch = player.xRot
    val onGround = player.onGround()
    val horizontalCollision = player.horizontalCollision
    // Send the slot update before the rotation sandwich. useItemOn keeps prediction/sequence ownership.
    (gameMode as MultiPlayerGameModeAccessor).nocturneEnsureHasSentCarriedItem()
    return withScaffoldFastRotation(rotation[0], rotation[1], originalYaw, originalPitch,
        sendRotation = { yaw, pitch ->
            // Local prediction must see the same look as the server (directional block placement).
            player.yRot = yaw
            player.xRot = pitch
            player.connection.send(ServerboundMovePlayerPacket.Rot(yaw, pitch, onGround, horizontalCollision))
        },
        interact = { gameMode.useItemOn(player, hand, hit) }
    )
}

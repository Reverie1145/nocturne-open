package cn.reverie.module.misc

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.util.misc.ChatUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.PositionMoveRotation
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.abs

/** Watches server position corrections and can leave the server when one is detected. */
object FlagDeleter : Module(
    name = "FlagDeleter",
    description = "Detect server rotation corrections and setback-like position corrections",
    category = Category.MISC,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {
    val detectRotation = BooleanSetting(
        "Detect Rotation",
        "Detect a server correction that changes your yaw or pitch",
        true
    )
    val rotationThreshold = NumberSetting(
        "Rotation Threshold",
        "Minimum yaw or pitch correction in degrees",
        0.1,
        0.0,
        180.0,
        0.1,
        { detectRotation.value }
    )
    val detectSetback = BooleanSetting(
        "Detect Setback",
        "Detect a server position correction beyond the configured distance",
        true
    )
    val setbackDistance = NumberSetting(
        "Setback Distance",
        "Minimum position correction distance in blocks",
        0.01,
        0.0,
        10.0,
        0.01,
        { detectSetback.value }
    )
    val disconnect = BooleanSetting(
        "Disconnect",
        "Disconnect from the server when a correction is detected",
        false
    )

    private var lastNoticeAt = 0L
    private var disconnectRequested = false
    private var observedLevel: ClientLevel? = null
    private var observedPlayer: LocalPlayer? = null
    private var skipFirstCorrectionAfterWorldChange = false

    override fun onEnable() {
        lastNoticeAt = 0L
        disconnectRequested = false
        val mc = Minecraft.getInstance()
        observedLevel = mc.level
        observedPlayer = mc.player
        skipFirstCorrectionAfterWorldChange = false
    }

    override fun onDisable() {
        disconnectRequested = false
        observedLevel = null
        observedPlayer = null
        skipFirstCorrectionAfterWorldChange = false
    }

    /** Called by MixinClientPacketListener on the client thread before vanilla applies the correction. */
    @JvmStatic
    fun onPositionCorrection(packet: ClientboundPlayerPositionPacket) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (level !== observedLevel || player !== observedPlayer) {
            observedLevel = level
            observedPlayer = player
            // The first correction after joining/changing dimension is usually
            // spawn synchronization. Skip only that packet, not an entire second.
            skipFirstCorrectionAfterWorldChange = true
        }
        if (skipFirstCorrectionAfterWorldChange) {
            skipFirstCorrectionAfterWorldChange = false
            return
        }

        val current = PositionMoveRotation.of(player)
        val target = PositionMoveRotation.calculateAbsolute(current, packet.change(), packet.relatives())
        val correctionDistance = current.position().distanceTo(target.position())
        val yawDelta = abs(Mth.wrapDegrees(target.yRot() - current.yRot()))
        val pitchDelta = abs(target.xRot() - current.xRot())

        val reasons = buildList {
            if (detectRotation.value && FlagDeleterRules.isRotationCorrection(
                    yawDelta,
                    pitchDelta,
                    rotationThreshold.value.toFloat()
                )
            ) {
                add(
                    "rotation yaw=${"%.1f".format(Locale.ROOT, yawDelta)}° " +
                        "pitch=${"%.1f".format(Locale.ROOT, pitchDelta)}°"
                )
            }
            if (detectSetback.value && FlagDeleterRules.isSetbackCorrection(
                    correctionDistance,
                    setbackDistance.value
                )
            ) {
                add("position correction=${"%.2f".format(Locale.ROOT, correctionDistance)} blocks")
            }
        }
        if (reasons.isEmpty()) return

        handleDetection(mc, reasons.joinToString("; "))
    }

    private fun handleDetection(mc: Minecraft, details: String) {
        val now = System.currentTimeMillis()
        if (!disconnect.value) {
            if (now - lastNoticeAt < NOTICE_COOLDOWN_MILLIS) return
            lastNoticeAt = now
            ChatUtil.sendWarning("FlagDeleter detected: $details")
            return
        }

        if (disconnectRequested) return
        val connection = mc.connection?.connection ?: return
        disconnectRequested = true
        connection.disconnect(Component.literal("FlagDeleter: $details"))
    }

    private const val NOTICE_COOLDOWN_MILLIS = 250L
}

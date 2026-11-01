package cn.reverie.module.combat

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.mixin.input.KeyMappingAccessor
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.StringSetting
import cn.reverie.module.combat.AntiBot
import cn.reverie.module.player.Teams
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.network.PacketUtil
import cn.reverie.util.player.InvItemHelper
import cn.reverie.util.rotation.MoveFix
import cn.reverie.util.rotation.Rotation
import cn.reverie.util.rotation.RotationManager
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import com.mojang.blaze3d.vertex.PoseStack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.lwjgl.glfw.GLFW

/**
 * Port of OpenMyau-Plus's Displace module for Nocturne's current module,
 * rotation, packet, and world-rendering APIs.
 * Original implementation: OpenMyau-Plus, GPL-3.0, by strangerrrrs.
 */
object KnockbackDisplace : Module(
    name = "KnockbackDisplace",
    description = "Displace combat targets with timed silent movement rotation",
    category = Category.COMBAT,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {
    private const val DISPLACE_WINDOW_TICKS = 10
    private const val VOID_SCAN_DIRECTIONS = 32
    private const val VOID_SCAN_RINGS = 12
    private const val VOID_SCAN_DEPTH = 10
    private const val VOID_SCAN_STEP = 0.5
    private const val DYNAMIC_SCAN_STEP = 0.5
    private const val DYNAMIC_SCAN_DISTANCE = 6.0
    private const val DYNAMIC_SCAN_SIDE_STEP = 0.45
    private const val DYNAMIC_WALL_CHECK_STEP = 0.25
    private const val DYNAMIC_COLLISION_INSET = 0.03
    private const val ARROW_FADE_MS = 250L
    private const val ARROW_FORWARD_GAP = 0.24
    private const val ARROW_BODY_LENGTH = 0.74
    private const val ARROW_BODY_HALF_WIDTH = 0.08
    private const val ARROW_HEAD_BACKSET = 0.18
    private const val ARROW_HEAD_LENGTH = 0.52
    private const val ARROW_HEAD_HALF_WIDTH = 0.30
    private const val TARGET_RANGE_SQUARED = 9.0

    private val scanX = DoubleArray(VOID_SCAN_DIRECTIONS) { index ->
        cos(Math.PI * 2.0 * index / VOID_SCAN_DIRECTIONS)
    }
    private val scanZ = DoubleArray(VOID_SCAN_DIRECTIONS) { index ->
        sin(Math.PI * 2.0 * index / VOID_SCAN_DIRECTIONS)
    }

    val dynamicAngle = ModeSetting(
        "Dynamic-angle", "Choose a fixed side angle or search for a clear route toward void", "Static", listOf("Static", "Dynamic")
    )
    val yawOffset = NumberSetting(
        "Yaw-offset", "Angle offset from the current server-facing yaw", 90.0, 0.0, 180.0, 1.0,
        { dynamicAngle.isMode("Static") }
    )
    val delay = NumberSetting("Delay", "Delay the displacement within each target window", 0.0, 0.0, 500.0, 10.0)
    val direction = ModeSetting(
        "Direction", "Side to displace toward in Static mode", "Left", listOf("Left", "Right"),
        { dynamicAngle.isMode("Static") }
    )
    val showDirection = BooleanSetting("Show-direction", "Show the current displacement direction", true)
    val findVoid = BooleanSetting(
        "Find-void", "Aim toward a nearby void when Static mode can find one", false,
        { dynamicAngle.isMode("Static") }
    )
    val blink = BooleanSetting("Blink", "Hold outgoing packets until the next game tick", false)
    val ignoreTeammates = BooleanSetting("Ignore-teammates", "Do not target teammates", true)
    val hasKnockback = BooleanSetting(
        "Has-knockback", "Only run with a Knockback-enchanted item unless the whitelist matches", false
    )
    val itemWhitelistToggle = BooleanSetting(
        "Item-whitelist", "Allow items named in the comma-separated whitelist", false
    )
    val itemWhitelist = StringSetting(
        "Whitelisted-items", "Comma-separated item ids or names", "", { itemWhitelistToggle.value }
    )

    private var displaceThisTick = false
    private var active = false
    private var hasKnockbackEnchant = false
    private var compensateNextTick = false
    private var displaceLeft = false
    private var wasDisplacingLastTick = false
    private var releaseBlinkNextGameTick = false
    private var blinkingModule = false
    private var dynamicVoidYaw: Float? = null
    private var renderDisplaceYaw: Float? = null
    private var renderTarget: Player? = null
    private var fadingDisplaceYaw: Float? = null
    private var fadingTarget: Player? = null
    private var arrowFadeStartMs = 0L
    private var lastRenderedDisplaceYaw: Float? = null
    private var lastRenderedTarget: Player? = null
    private var lastRenderedArrowMs = 0L
    private var tickCounter = 0
    private var target: Player? = null
    private val targetWindowStartTicks = HashMap<Int, Int>()
    private val blinkedPackets = ConcurrentLinkedQueue<Packet<*>>()

    init {
        listen(PacketEvent::class.java) { onPacket(it) }
    }

    override val suffix: String
        get() = "${delay.getInt()}ms"

    override fun onEnable() = resetState()

    override fun onDisable() {
        releaseBlink()
        clearActiveState()
        clearArrowState()
        targetWindowStartTicks.clear()
        tickCounter = 0
        hasKnockbackEnchant = false
    }

    private fun resetState() {
        releaseBlink()
        displaceThisTick = false
        active = false
        hasKnockbackEnchant = false
        compensateNextTick = false
        wasDisplacingLastTick = false
        dynamicVoidYaw = null
        renderDisplaceYaw = null
        renderTarget = null
        target = null
        clearArrowState()
        tickCounter = 0
        targetWindowStartTicks.clear()
    }

    @JvmStatic
    fun onUpdate(player: LocalPlayer) {
        if (!enabled) return

        if (releaseBlinkNextGameTick) releaseBlink()

        val mc = Minecraft.getInstance()
        val level = mc.level
        if (mc.player !== player || level == null || !player.isAlive || player.isRemoved) {
            clearActiveState()
            targetWindowStartTicks.clear()
            releaseBlink()
            return
        }

        tickCounter++
        pruneTargetDelayStates(level)

        val knockbackEnchant = hasKnockbackEnchantment(player.mainHandItem)
        val kbPass = !hasKnockback.value || knockbackEnchant
        val whitelistPass = !itemWhitelistToggle.value || itemWhitelistMatches(player.mainHandItem)
        if (!kbPass && !whitelistPass) {
            clearActiveState()
            return
        }

        val attacking = mc.options.keyAttack.isDown || (KillAura.enabled && KillAura.target != null)
        val selectedTarget = if (attacking) findClosestTarget(player, level) else null
        target = selectedTarget
        active = selectedTarget != null && (knockbackEnchant || anyMovementKey(mc))
        if (!active || selectedTarget == null) {
            clearActiveState()
            return
        }

        dynamicVoidYaw = if (dynamicAngle.isMode("Dynamic")) {
            findDynamicVoidYaw(player, selectedTarget, level)
        } else if (findVoid.value) {
            findStaticVoidYaw(player, selectedTarget, level)
        } else {
            null
        }

        if (dynamicVoidYaw == null && dynamicAngle.isMode("Static")) {
            displaceLeft = direction.isMode("Left")
        }

        renderDisplaceYaw = when {
            dynamicVoidYaw != null -> dynamicVoidYaw
            dynamicAngle.isMode("Dynamic") -> null
            else -> getFixedDisplaceYaw(player)
        }
        renderTarget = renderDisplaceYaw?.let { selectedTarget }
        if (renderDisplaceYaw == null) {
            clearActiveState()
            return
        }

        hasKnockbackEnchant = knockbackEnchant
        displaceThisTick = !displaceThisTick
        if (displaceThisTick && !shouldDisplaceInCurrentWindow(selectedTarget, tickCounter)) {
            startArrowFade()
            displaceThisTick = false
            compensateNextTick = false
            wasDisplacingLastTick = false
            dynamicVoidYaw = null
            renderDisplaceYaw = null
            renderTarget = null
            return
        }

        if (!displaceThisTick && wasDisplacingLastTick) {
            KeyMapping.click((mc.options.keyAttack as KeyMappingAccessor).nocturneGetKey())
        }
        wasDisplacingLastTick = displaceThisTick
    }

    @JvmStatic
    fun shouldRotate(): Boolean = enabled && active && displaceThisTick && renderDisplaceYaw != null

    @JvmStatic
    fun rotation(): Rotation? {
        if (!shouldRotate()) return null
        val player = Minecraft.getInstance().player ?: return null
        return Rotation(renderDisplaceYaw ?: return null, player.xRot)
    }

    @JvmStatic
    fun onMoveInput(player: LocalPlayer, event: MoveInputEvent) {
        if (!enabled) return
        if (!active) {
            compensateNextTick = false
            return
        }

        if (compensateNextTick && !displaceThisTick) {
            compensateNextTick = false
            event.strafe = if (displaceLeft) -1f else 1f
            return
        }

        if (!displaceThisTick || hasKnockbackEnchant || !anyMovementKey(Minecraft.getInstance())) return
        event.forward = 1f
        compensateNextTick = true
    }

    /** Draws the source module's fading world-space direction arrow with Nocturne's render pipeline. */
    @JvmStatic
    fun renderWorld(levelRenderState: LevelRenderState, collector: SubmitNodeCollector) {
        if (!enabled || !showDirection.value) {
            if (!showDirection.value) clearArrowState()
            return
        }

        val now = System.currentTimeMillis()
        val currentTarget = renderTarget
        val activeArrow = active && renderDisplaceYaw != null && currentTarget != null &&
            currentTarget.isAlive && !currentTarget.isRemoved

        val arrowTarget: Player
        val arrowYaw: Float
        val alpha: Float
        if (activeArrow) {
            clearFadingArrow()
            arrowTarget = currentTarget
            arrowYaw = renderDisplaceYaw!!
            alpha = 1f
        } else {
            val fadeTarget = fadingTarget
            val fadeYaw = fadingDisplaceYaw
            if (fadeTarget == null || fadeYaw == null || !fadeTarget.isAlive || fadeTarget.isRemoved) {
                clearFadingArrow()
                return
            }
            val elapsed = now - arrowFadeStartMs
            if (elapsed >= ARROW_FADE_MS) {
                clearFadingArrow()
                return
            }
            arrowTarget = fadeTarget
            arrowYaw = fadeYaw
            alpha = 1f - elapsed.toFloat() / ARROW_FADE_MS
        }

        val mc = Minecraft.getInstance()
        val camera = levelRenderState.cameraRenderState.pos
        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val center = Vec3(
            Mth.lerp(partialTicks.toDouble(), arrowTarget.xo, arrowTarget.x),
            Mth.lerp(partialTicks.toDouble(), arrowTarget.yo, arrowTarget.y) + arrowTarget.bbHeight * 0.5,
            Mth.lerp(partialTicks.toDouble(), arrowTarget.zo, arrowTarget.z)
        )

        val yawRad = Math.toRadians(arrowYaw.toDouble())
        val forwardX = -sin(yawRad)
        val forwardZ = cos(yawRad)
        val sideX = -forwardZ
        val sideZ = forwardX
        val tailDistance = arrowTarget.bbWidth * 0.5 + ARROW_FORWARD_GAP
        val bodyEndDistance = tailDistance + ARROW_BODY_LENGTH
        val headBackDistance = bodyEndDistance - ARROW_HEAD_BACKSET
        val tipDistance = bodyEndDistance + ARROW_HEAD_LENGTH

        fun point(distance: Double, side: Double = 0.0) = Vec3(
            center.x + forwardX * distance + sideX * side,
            center.y,
            center.z + forwardZ * distance + sideZ * side
        )

        val tail = point(tailDistance)
        val bodyLeft = point(bodyEndDistance, -ARROW_BODY_HALF_WIDTH)
        val bodyRight = point(bodyEndDistance, ARROW_BODY_HALF_WIDTH)
        val headLeft = point(headBackDistance, -ARROW_HEAD_HALF_WIDTH)
        val headRight = point(headBackDistance, ARROW_HEAD_HALF_WIDTH)
        val tip = point(tipDistance)
        val segments = listOf(
            tail to bodyLeft,
            bodyLeft to headLeft,
            headLeft to tip,
            tip to headRight,
            headRight to bodyRight,
            bodyRight to tail,
            tail to point(bodyEndDistance),
            bodyLeft to bodyRight
        )

        val origin = tail.subtract(camera)
        val poseStack = PoseStack()
        poseStack.translate(origin.x, origin.y, origin.z)
        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent()) { pose, consumer ->
            fun drawSegments(color: Int, width: Float) {
                for ((from, to) in segments) {
                    val a = from.subtract(tail)
                    val b = to.subtract(tail)
                    consumer.addVertex(pose, a.x.toFloat(), a.y.toFloat(), a.z.toFloat())
                        .setColor(color)
                        .setNormal(pose, 0f, 1f, 0f)
                        .setLineWidth(width)
                    consumer.addVertex(pose, b.x.toFloat(), b.y.toFloat(), b.z.toFloat())
                        .setColor(color)
                        .setNormal(pose, 0f, 1f, 0f)
                        .setLineWidth(width)
                }
            }

            drawSegments(NanoVGColor(0f, 0f, 0f, 0.9f * alpha).toArgb(), 4f)
            drawSegments(NanoVGColor(1f, 1f, 1f, 0.9f * alpha).toArgb(), 2f)
        }

        if (activeArrow) {
            lastRenderedDisplaceYaw = arrowYaw
            lastRenderedTarget = arrowTarget
            lastRenderedArrowMs = now
        }
    }

    private fun onPacket(event: PacketEvent) {
        if (!enabled || !event.isSend) return
        val packet = event.packet

        if (blinkingModule) {
            if (!isBlinkExempt(packet)) {
                blinkedPackets.add(packet)
                event.isCancelled = true
            }
            return
        }

        if (blink.value && active && displaceThisTick && packet is ServerboundMovePlayerPacket) {
            blinkingModule = true
            releaseBlinkNextGameTick = true
            blinkedPackets.add(packet)
            event.isCancelled = true
        }
    }

    private fun isBlinkExempt(packet: Packet<*>): Boolean {
        val name = packet.javaClass.simpleName
        return name.contains("KeepAlive", ignoreCase = true) ||
            name.contains("Chat", ignoreCase = true) ||
            name.contains("Handshake", ignoreCase = true) ||
            name.contains("Login", ignoreCase = true) ||
            name.contains("Ping", ignoreCase = true) ||
            name.contains("Encryption", ignoreCase = true)
    }

    private fun releaseBlink() {
        blinkingModule = false
        releaseBlinkNextGameTick = false
        while (true) {
            val packet = blinkedPackets.poll() ?: break
            PacketUtil.sendPacketNoEvent(packet)
        }
    }

    private fun findClosestTarget(player: LocalPlayer, level: Level): Player? {
        val eyePosition = player.eyePosition
        return level.getEntities(player, player.boundingBox.inflate(3.5))
            .asSequence()
            .filterIsInstance<Player>()
            .filter { candidate ->
                candidate.isAlive && !candidate.isRemoved && !candidate.isSpectator &&
                    candidate.health > 0f && candidate.boundingBox.distanceToSqr(eyePosition) <= TARGET_RANGE_SQUARED &&
                    !AntiBot.isBot(candidate) && (!ignoreTeammates.value || !Teams.isTeammate(candidate))
            }
            .minByOrNull { it.boundingBox.distanceToSqr(eyePosition) }
    }

    private fun anyMovementKey(mc: Minecraft): Boolean =
        mc.options.keyUp.isDown || mc.options.keyDown.isDown ||
            mc.options.keyLeft.isDown || mc.options.keyRight.isDown

    private fun hasKnockbackEnchantment(stack: ItemStack): Boolean =
        InvItemHelper.getEnchantLevel(stack, Enchantments.KNOCKBACK) > 0

    private fun itemWhitelistMatches(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val terms = itemWhitelist.value.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) return false
        val candidates = listOf(
            BuiltInRegistries.ITEM.getKey(stack.item).toString(),
            stack.item.descriptionId,
            stack.hoverName.string
        ).map { it.lowercase() }
        return terms.any { term -> candidates.any { it.contains(term) } }
    }

    private fun findStaticVoidYaw(player: LocalPlayer, target: Player, level: Level): Float? {
        var bestX = 0.0
        var bestZ = 0.0
        var bestScore = Double.MAX_VALUE

        for (ring in 1..VOID_SCAN_RINGS) {
            val radius = ring * VOID_SCAN_STEP
            var foundInRing = false
            for (index in 0 until VOID_SCAN_DIRECTIONS) {
                val x = target.x + scanX[index] * radius
                val z = target.z + scanZ[index] * radius
                if (!isVoidColumn(level, x, target.y, z)) continue

                val playerDx = x - player.x
                val playerDz = z - player.z
                val score = radius * radius * 1000.0 + playerDx * playerDx + playerDz * playerDz
                if (score < bestScore) {
                    bestScore = score
                    bestX = x
                    bestZ = z
                    foundInRing = true
                }
            }
            if (foundInRing) break
        }

        if (bestScore == Double.MAX_VALUE) return null
        updateDisplaceSide(player, target, bestX, bestZ)

        val dx = bestX - target.x
        val dz = bestZ - target.z
        val distance = sqrt(dx * dx + dz * dz)
        if (distance < 0.001) return null

        val aimRadius = min(distance, max(0.35, target.bbWidth * 0.5 + 0.15))
        val aimX = target.x + dx / distance * aimRadius
        val aimZ = target.z + dz / distance * aimRadius
        val eyes = player.getEyePosition()
        val adx = aimX - eyes.x
        val ady = target.y + target.eyeHeight * 0.5 - eyes.y
        val adz = aimZ - eyes.z
        return Math.toDegrees(atan2(adz, adx)).toFloat() - 90f
    }

    private fun findDynamicVoidYaw(player: LocalPlayer, target: Player, level: Level): Float? {
        var bestForwardX = 0.0
        var bestForwardZ = 0.0
        var bestScore = 0.0
        for (index in 0 until VOID_SCAN_DIRECTIONS) {
            val score = scoreVoidPath(target, level, scanX[index], scanZ[index])
            if (score > bestScore) {
                bestScore = score
                bestForwardX = scanX[index]
                bestForwardZ = scanZ[index]
            }
        }
        if (bestScore <= 0.0) return null
        updateDisplaceSide(player, target, target.x + bestForwardX, target.z + bestForwardZ)
        return (Math.toDegrees(atan2(bestForwardZ, bestForwardX)) - 90.0).toFloat()
    }

    private fun scoreVoidPath(target: Player, level: Level, forwardX: Double, forwardZ: Double): Double {
        val sideX = -forwardZ
        val sideZ = forwardX
        val baseCollisionBox = target.boundingBox.inflate(-DYNAMIC_COLLISION_INSET, 0.0, -DYNAMIC_COLLISION_INSET)
        var score = 0.0
        var checkedForward = 0.0
        var consecutiveCenterVoid = 0

        for (step in 1..(DYNAMIC_SCAN_DISTANCE / DYNAMIC_SCAN_STEP).toInt()) {
            val forward = step * DYNAMIC_SCAN_STEP
            if (!isDynamicPathClear(target, level, baseCollisionBox, forwardX, forwardZ, checkedForward, forward)) break
            checkedForward = forward
            var centerVoid = false

            for (side in -1..1) {
                val sideOffset = side * DYNAMIC_SCAN_SIDE_STEP
                val x = target.x + forwardX * forward + sideX * sideOffset
                val z = target.z + forwardZ * forward + sideZ * sideOffset
                if (isVoidColumn(level, x, target.y, z)) {
                    score += (if (side == 0) 1.4 else 1.0) * (DYNAMIC_SCAN_DISTANCE + DYNAMIC_SCAN_STEP - forward)
                    centerVoid = centerVoid || side == 0
                }
            }

            if (centerVoid) {
                consecutiveCenterVoid++
                score += consecutiveCenterVoid * 2.0
            } else {
                consecutiveCenterVoid = 0
            }
        }
        return score
    }

    private fun isDynamicPathClear(
        target: Player,
        level: Level,
        baseCollisionBox: AABB,
        forwardX: Double,
        forwardZ: Double,
        fromForward: Double,
        toForward: Double
    ): Boolean {
        var forward = fromForward + DYNAMIC_WALL_CHECK_STEP
        while (forward <= toForward + 1.0E-4) {
            val box = baseCollisionBox.move(forwardX * forward, 0.0, forwardZ * forward)
            if (hasBlockCollision(target, level, box)) return false
            forward += DYNAMIC_WALL_CHECK_STEP
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun hasBlockCollision(target: Player, level: Level, box: AABB): Boolean {
        val minX = floor(box.minX).toInt()
        val maxX = floor(box.maxX).toInt()
        val minY = floor(box.minY).toInt()
        val maxY = floor(box.maxY).toInt()
        val minZ = floor(box.minZ).toInt()
        val maxZ = floor(box.maxZ).toInt()

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return true
                }
            }
        }
        return level.getBlockCollisions(target, box).iterator().hasNext()
    }

    @Suppress("DEPRECATION")
    private fun isVoidColumn(level: Level, x: Double, y: Double, z: Double): Boolean {
        val blockX = floor(x).toInt()
        val blockZ = floor(z).toInt()
        val startY = floor(y).toInt() - 1
        for (depth in 0..VOID_SCAN_DEPTH) {
            val pos = BlockPos(blockX, startY - depth, blockZ)
            if (level.isOutsideBuildHeight(pos)) return true
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir) return false
        }
        return true
    }

    private fun updateDisplaceSide(player: LocalPlayer, target: Player, voidX: Double, voidZ: Double) {
        val targetDx = target.x - player.x
        val targetDz = target.z - player.z
        val voidDx = voidX - player.x
        val voidDz = voidZ - player.z
        displaceLeft = targetDx * voidDz - targetDz * voidDx < 0.0
    }

    private fun getFixedDisplaceYaw(player: LocalPlayer): Float {
        val baseYaw = RotationManager.serverRotation?.yaw ?: player.yRot
        val offset = yawOffset.getFloat()
        return Mth.wrapDegrees(if (displaceLeft) baseYaw - offset else baseYaw + offset)
    }

    private fun shouldDisplaceInCurrentWindow(target: Player, currentTick: Int): Boolean {
        val targetId = target.id
        val windowStart = targetWindowStartTicks[targetId]
        if (windowStart == null || currentTick - windowStart >= DISPLACE_WINDOW_TICKS) {
            targetWindowStartTicks[targetId] = currentTick
            return true
        }
        val delayTicks = if (delay.value <= 0.0) 0 else ceil(delay.value / 50.0).toInt()
        return delayTicks <= 0 || currentTick - windowStart >= delayTicks
    }

    private fun pruneTargetDelayStates(level: Level) {
        val iterator = targetWindowStartTicks.keys.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            val entity = level.getEntity(id)
            if (entity !is Player || !entity.isAlive || entity.isRemoved) iterator.remove()
        }
    }

    private fun clearActiveState() {
        startArrowFade()
        active = false
        displaceThisTick = false
        compensateNextTick = false
        wasDisplacingLastTick = false
        dynamicVoidYaw = null
        renderDisplaceYaw = null
        renderTarget = null
        target = null
    }

    private fun clearFadingArrow() {
        fadingDisplaceYaw = null
        fadingTarget = null
        arrowFadeStartMs = 0L
    }

    private fun clearArrowState() {
        clearFadingArrow()
        lastRenderedDisplaceYaw = null
        lastRenderedTarget = null
        lastRenderedArrowMs = 0L
    }

    private fun startArrowFade() {
        val now = System.currentTimeMillis()
        val previousTarget = lastRenderedTarget
        val previousYaw = lastRenderedDisplaceYaw
        if (previousYaw != null && previousTarget != null && previousTarget.isAlive && !previousTarget.isRemoved &&
            now - lastRenderedArrowMs <= ARROW_FADE_MS
        ) {
            fadingDisplaceYaw = previousYaw
            fadingTarget = previousTarget
            arrowFadeStartMs = now
        }
        lastRenderedDisplaceYaw = null
        lastRenderedTarget = null
        lastRenderedArrowMs = 0L
    }
}

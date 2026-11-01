package cn.reverie.module.combat

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.combat.backtrack.BacktrackFlushReason
import cn.reverie.module.combat.backtrack.BacktrackPacketClassifier
import cn.reverie.module.combat.backtrack.BacktrackPacketQueue
import cn.reverie.module.combat.backtrack.BacktrackPosition
import cn.reverie.module.combat.backtrack.BacktrackReachMath
import cn.reverie.module.combat.backtrack.BacktrackTracker
import cn.reverie.module.player.Teams
import cn.reverie.render.hud.HUDModule
import cn.reverie.util.player.InvItemHelper
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.Packet
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

object Backtrack : Module(
    name = "Backtrack",
    description = "Holds target entity packets and releases them at a combat-safe frame",
    category = Category.COMBAT,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {
    private const val HARD_MAX_DELAY_MS = 100L
    private const val TARGET_SCAN_RANGE = 6.0

    val auto = BooleanSetting("Auto", "Choose a useful release frame from the configured delay window", true)
    val delay = NumberSetting("Delay", "Fixed packet delay in milliseconds", 80.0, 0.0, 100.0, 5.0, { !auto.value })
    val minDelay = NumberSetting("Min Delay", "Minimum automatic delay in milliseconds", 20.0, 0.0, 100.0, 5.0, { auto.value })
    val maxDelay = NumberSetting("Max Delay", "Maximum automatic delay in milliseconds", 80.0, 20.0, 100.0, 5.0, { auto.value })
    val range = NumberSetting("Range", "Maximum reach considered when selecting a release frame", 3.0, 2.5, 3.0, 0.01)
    val playersOnly = BooleanSetting("Players Only", "Only hold packets for player entities", true)
    val hurtTime = NumberSetting("Hurt Time", "Stop holding while target hurt time exceeds this value", 10.0, 0.0, 10.0, 1.0)
    val weaponOnly = BooleanSetting("Weapon Only", "Only backtrack while holding a combat weapon", true)
    val pauseOnVelocity = BooleanSetting("Pause On Velocity", "Flush while Velocity owns inbound packets", true)
    val debug = BooleanSetting("Debug", "Log packet queue flush reasons", false)

    // Keep Nocturne's existing world-space trajectory renderer and its settings.
    val render = BooleanSetting("Render", "Render smooth trajectory streamlines", true)
    val lineCount = NumberSetting("Line Count", "Number of trajectory streamline strands", 20.0, 4.0, 48.0, 2.0, { render.value })
    val lineWidth = NumberSetting("Line Width", "Width of trajectory lines", 2.0, 0.5, 4.0, 0.5, { render.value })

    private val queue = BacktrackPacketQueue()
    private val tracker = BacktrackTracker()
    private val trackedPlayers = ConcurrentHashMap<Int, TrackedPlayer>()

    @Volatile
    private var target: LivingEntity? = null

    private var lastFlushReason = BacktrackFlushReason.MANUAL
    private var lastScore = Double.NEGATIVE_INFINITY

    init {
        listen(PacketEvent::class.java) { onPacket(it) }
    }

    override val suffix: String
        get() {
            if (!queue.isEmpty()) {
                return queue.oldestAge(System.currentTimeMillis()).toString() + "ms"
            }

            val mc = Minecraft.getInstance()
            val ping = try {
                val player = mc.player
                if (player != null && mc.connection != null) {
                    mc.connection!!.getPlayerInfo(player.uuid)?.latency?.coerceAtLeast(0) ?: 0
                } else {
                    0
                }
            } catch (_: Throwable) {
                0
            }
            return "${ping}ms"
        }

    override fun onEnable() {
        queue.dropAll()
        resetTracking()
    }

    override fun onDisable() {
        flushNow(BacktrackFlushReason.MODULE_DISABLE)
        resetTracking()
    }

    @JvmStatic
    fun onUpdate(player: LocalPlayer) {
        if (!enabled) return

        val mc = Minecraft.getInstance()
        if (mc.player !== player || mc.level == null || mc.connection == null) {
            flushNow(BacktrackFlushReason.WORLD_CHANGE)
            resetTracking()
            return
        }

        if (pauseOnVelocity.value && velocityBusy()) {
            if (!queue.isEmpty()) flushNow(BacktrackFlushReason.VELOCITY_SESSION)
            updateSuffixState()
            return
        }

        refreshTarget(player)
        val currentTarget = target
        if (currentTarget == null) {
            if (!queue.isEmpty()) flushNow(BacktrackFlushReason.NO_TARGET)
            resetTracking()
            updateSuffixState()
            return
        }

        tracker.setCurrentTarget(currentTarget.id)
        if (!weaponAllowed(player)) {
            if (!queue.isEmpty()) flushNow(BacktrackFlushReason.UNSAFE)
            trackedPlayers.clear()
            tracker.captureApplied(currentTarget)
            updateSuffixState()
            return
        }

        val now = System.currentTimeMillis()
        queue.releaseExpired(now, configuredMaxDelay())
        if (queue.isEmpty()) {
            lastScore = Double.NEGATIVE_INFINITY
            syncRenderPath(currentTarget)
            updateSuffixState()
            return
        }

        syncRenderPath(currentTarget)
        if (auto.value) selectOptimalFrame(currentTarget, now)

        if (!queue.isEmpty() && !insideCompensatedUnion(currentTarget)) {
            queue.releaseUntilIndex(1)
            syncRenderPath(currentTarget)
        }

        syncRenderPath(currentTarget)
        updateSuffixState()
    }

    @JvmStatic
    fun reset() {
        flushNow(BacktrackFlushReason.WORLD_CHANGE)
        resetTracking()
    }

    @JvmStatic
    fun isBacktracking(): Boolean = enabled && !queue.isEmpty()

    @JvmStatic
    fun isBlinking(): Boolean = isBacktracking()

    @JvmStatic
    fun isActive(): Boolean = isBacktracking()

    @JvmStatic
    fun isHoldingPackets(): Boolean = enabled && !queue.isEmpty()

    @JvmStatic
    fun getTarget(): LivingEntity? = target

    @JvmStatic
    fun discardForVelocityRelease() {
        flushNow(BacktrackFlushReason.VELOCITY_SESSION)
    }

    @JvmStatic
    fun isHolding(entity: Entity): Boolean = enabled && queue.containsEntity(entity.id)

    private fun onPacket(event: PacketEvent) {
        if (!enabled || !event.isReceive || event.isCancelled || queue.isFlushing()) return

        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null || mc.connection == null) return
        if (pauseOnVelocity.value && velocityBusy()) return

        val original = event.packet
        val unwrapped = BacktrackPacketClassifier.unwrap(original)
        if (unwrapped.isEmpty()) return

        var flushPending = false
        var flushReason: BacktrackFlushReason? = null
        val delayed = ArrayList<Packet<*>>()
        val passthrough = ArrayList<Packet<*>>()
        val currentTarget = target
        val targetId = currentTarget?.id ?: tracker.currentTargetId

        for (child in unwrapped) {
            val classification = BacktrackPacketClassifier.classify(child, mc, targetId, playersOnly.value)
            if (classification.isFlush) {
                flushPending = true
                flushReason = classification.reason
                passthrough += child
            } else if (classification.isDelay && shouldHold(child, currentTarget)) {
                delayed += child
            } else {
                passthrough += child
            }
        }

        if (flushPending) {
            if (queue.isEmpty()) return
            event.isCancelled = true
            val reason = flushReason ?: BacktrackFlushReason.MANUAL
            runOnClientThread {
                flushNow(reason)
                queue.applyNow(original)
            }
            return
        }

        // Bundles with unrelated packets stay intact so their relative ordering is preserved.
        if (delayed.isEmpty() || passthrough.isNotEmpty()) return

        val heldTarget = currentTarget ?: return
        event.isCancelled = true
        val now = System.currentTimeMillis()
        val renderState = trackedPlayers.computeIfAbsent(heldTarget.id) {
            TrackedPlayer(
                heldTarget.id,
                heldTarget.position(),
                heldTarget.bbWidth / 2.0,
                heldTarget.bbHeight.toDouble()
            )
        }
        for (packet in delayed) {
            val related = BacktrackPacketClassifier.relatedEntityIds(packet, mc.level)
            queue.enqueue(packet, now, related, BacktrackPacketClassifier.isPositionPacket(packet))
            if (BacktrackPacketClassifier.isPositionPacket(packet)) {
                val current = tracker.getServerPosition(heldTarget.id) ?: renderState.serverPosition
                val next = tracker.applyPacketToPosition(heldTarget, current, packet)
                if (next != null) {
                    val hitbox = BacktrackReachMath.boxFromPosition(heldTarget, next)
                    tracker.noteServerPosition(heldTarget.id, next, hitbox)
                    renderState.moveTo(next)
                }
            }
        }
    }

    private fun runOnClientThread(runnable: () -> Unit) {
        val mc = Minecraft.getInstance()
        if (mc.isSameThread) runnable() else mc.execute(runnable)
    }

    private fun shouldHold(packet: Packet<*>, currentTarget: LivingEntity?): Boolean {
        if (pauseOnVelocity.value && velocityBusy()) return false
        val player = Minecraft.getInstance().player ?: return false
        val heldTarget = currentTarget ?: return false
        if (!isValidTarget(heldTarget, player) || !weaponAllowed(player)) return false
        if (hurtTime.getInt() > 0 && heldTarget.hurtTime > hurtTime.getInt()) return false
        return heldTarget.id in BacktrackPacketClassifier.relatedEntityIds(packet)
    }

    private fun refreshTarget(player: LocalPlayer) {
        val auraTarget = if (KillAura.enabled) KillAura.target else null
        val next = if (auraTarget != null && isValidTarget(auraTarget, player)) auraTarget else findNearestTarget(player)

        if (target?.id != next?.id) {
            if (!queue.isEmpty()) flushNow(BacktrackFlushReason.NO_TARGET)
            resetTracking()
            target = next
        }
    }

    private fun findNearestTarget(player: LocalPlayer): LivingEntity? {
        val searchBox = player.boundingBox.inflate(TARGET_SCAN_RANGE)
        return player.level().getEntities(player, searchBox)
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { isValidTarget(it, player) }
            .minByOrNull { player.distanceTo(it) }
    }

    private fun isValidTarget(entity: LivingEntity, player: LocalPlayer): Boolean {
        if (entity === player || !entity.isAlive || entity.isRemoved || entity.isSpectator || entity.isDeadOrDying) return false
        if (entity.health <= 0f || player.distanceTo(entity) > TARGET_SCAN_RANGE) return false
        if (playersOnly.value && entity !is Player) return false
        if (entity is ArmorStand || AntiBot.isBot(entity) || Teams.isTeammate(entity)) return false
        if (!KillAura.invisibles.value && entity.isInvisible) return false
        return true
    }

    private fun weaponAllowed(player: LocalPlayer): Boolean {
        if (!weaponOnly.value) return true
        val stack = player.mainHandItem
        if (stack.isEmpty) return false
        return InvItemHelper.isSword(stack) ||
            InvItemHelper.isAxe(stack) ||
            stack.get(DataComponents.ATTACK_RANGE) != null ||
            stack.get(DataComponents.WEAPON) != null
    }

    private fun velocityBusy(): Boolean =
        Velocity.enabled && ((Velocity.mode.isMode("Delay") && Velocity.delay > 0) || Velocity.buffering)

    private fun selectOptimalFrame(currentTarget: LivingEntity, now: Long) {
        val player = Minecraft.getInstance().player ?: return
        val snapshot = queue.snapshot()
        val frames = tracker.reconstruct(currentTarget.id, snapshot)
        if (frames.size < 2) return

        val visual = currentTarget.boundingBox
        val serverBox = frames.last().hitbox
        if (!BacktrackReachMath.isInsideCompensatedUnion(Minecraft.getInstance().player, visual, serverBox, range.value)) {
            queue.releaseUntilIndex(1)
            syncRenderPath(currentTarget)
            return
        }
        if (realPositionBetter(currentTarget, serverBox)) {
            flushNow(BacktrackFlushReason.BETTER_REAL_POSITION)
            syncRenderPath(currentTarget)
            return
        }

        var best: BacktrackPosition? = null
        var bestFrameScore = Double.NEGATIVE_INFINITY
        val minAge = configuredMinDelay()
        val maxAge = configuredMaxDelay()
        for (frame in frames) {
            if (frame.queueIndex < 0) continue
            val age = (now - frame.timestamp).coerceAtLeast(0L)
            if (age < minAge || age > maxAge) continue
            if (!BacktrackReachMath.isInsideCompensatedUnion(player, visual, frame.hitbox, range.value)) continue
            if (!BacktrackReachMath.canHitWithoutFlag(player, frame.hitbox, range.value) &&
                !BacktrackReachMath.canHitWithoutFlag(player, visual, range.value)
            ) continue

            val score = BacktrackReachMath.scorePosition(player, frame.hitbox, serverBox, range.value)
            if (score > bestFrameScore) {
                bestFrameScore = score
                best = frame
            }
        }

        lastScore = bestFrameScore
        val frame = best ?: return
        if (frame.queueIndex >= 0) {
            queue.releaseUntilIndex(frame.queueIndex + 1)
            syncRenderPath(currentTarget)
        }
    }

    private fun realPositionBetter(currentTarget: LivingEntity, serverBox: net.minecraft.world.phys.AABB): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val currentBox = currentTarget.boundingBox
        val visualScore = BacktrackReachMath.scorePosition(player, currentBox, serverBox, range.value)
        val serverScore = BacktrackReachMath.scorePosition(player, serverBox, serverBox, range.value)
        return serverScore > visualScore + 0.05
    }

    private fun insideCompensatedUnion(currentTarget: LivingEntity): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return BacktrackReachMath.isInsideCompensatedUnion(
            player,
            currentTarget.boundingBox,
            tracker.getServerHitbox(currentTarget.id, currentTarget),
            range.value
        )
    }

    private fun syncRenderPath(currentTarget: LivingEntity) {
        val state = trackedPlayers.computeIfAbsent(currentTarget.id) {
            TrackedPlayer(
                currentTarget.id,
                currentTarget.position(),
                currentTarget.bbWidth / 2.0,
                currentTarget.bbHeight.toDouble()
            )
        }
        val frames = tracker.reconstruct(currentTarget.id, queue.snapshot())
        state.syncToEntity(currentTarget.position())
        for (frame in frames.drop(1)) state.moveTo(frame.position)
        val predicted = frames.lastOrNull()
        if (predicted == null) {
            tracker.captureApplied(currentTarget)
        } else {
            tracker.noteServerPosition(currentTarget.id, predicted.position, predicted.hitbox)
        }
        state.onTick()
    }

    private fun configuredMaxDelay(): Long {
        val configured = if (auto.value) maxDelay.getInt().toLong() else delay.getInt().toLong()
        return configured.coerceIn(0L, HARD_MAX_DELAY_MS)
    }

    private fun configuredMinDelay(): Long {
        if (!auto.value) return 0L
        return minDelay.getInt().toLong().coerceIn(0L, configuredMaxDelay())
    }

    private fun flushNow(reason: BacktrackFlushReason) {
        lastFlushReason = reason
        log("Flush " + reason + " queued=" + queue.size())
        queue.flushNow()
    }

    private fun log(message: String) {
        if (!debug.value) return
        System.out.println(
            "[Backtrack] " + message + " score=" + String.format("%.3f", lastScore) + " lastFlush=" + lastFlushReason
        )
    }

    private fun resetTracking() {
        tracker.reset()
        trackedPlayers.clear()
        target = null
        lastScore = Double.NEGATIVE_INFINITY
    }

    private fun updateSuffixState() {
        if (queue.isEmpty()) lastScore = Double.NEGATIVE_INFINITY
    }
    /** Submits smooth multi-strand trajectory streamlines along the exact physical motion path of the target. */
    @JvmStatic
    fun renderWorldBox(levelRenderState: LevelRenderState, collector: SubmitNodeCollector) {
        if (!enabled || !render.value) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val cameraPosition = levelRenderState.cameraRenderState.pos
        val partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)
        val accent = HUDModule.sampleColor(0f)
        val animTime = (System.currentTimeMillis() % 1000000L) / 1000.0

        val strands = lineCount.getInt().coerceIn(4, 48)
        val width = lineWidth.getFloat().coerceIn(0.5f, 5.0f)

        for (tracked in trackedPlayers.values) {
            if (tracked.entityId == player.id) continue
            val entity = level.getEntity(tracked.entityId) ?: continue
            if (!entity.isAlive || entity.isRemoved) continue

            // 1. Precise interpolated delayed position (matching client entity model exactly)
            val delayedPos = Vec3(
                Mth.lerp(partialTicks.toDouble(), entity.xo, entity.x),
                Mth.lerp(partialTicks.toDouble(), entity.yo, entity.y),
                Mth.lerp(partialTicks.toDouble(), entity.zo, entity.z)
            )

            // 2. Precise tick-interpolated real position (synchronized with delayedPos, zero jitter, zero lag)
            val realPos = tracked.interpolatedPosition(partialTicks)

            if (realPos.distanceToSqr(cameraPosition) > 4096.0 && delayedPos.distanceToSqr(cameraPosition) > 4096.0) continue

            val displacement = realPos.subtract(delayedPos)
            val dist = displacement.length()
            if (dist < 0.02) continue // Negligible displacement, skip rendering

            // Smooth alpha fade-in/fade-out so lines don't abruptly pop in or out
            val alphaFade = ((dist - 0.02) / 0.18).coerceIn(0.0, 1.0).toFloat()

            // 3. Construct 3D trajectory control points: [delayedPos] -> [valid waypoints] -> [realPos]
            val waypoints = tracked.getWaypointsSnapshot()
            val rawPoints = ArrayList<Vec3>()
            rawPoints.add(delayedPos)
            for (wp in waypoints) {
                // Keep points that have distinct positions to avoid zero-length segments
                if (wp.distanceTo(rawPoints.last()) > 0.03) {
                    rawPoints.add(wp)
                }
            }
            if (realPos.distanceTo(rawPoints.last()) > 0.03) {
                rawPoints.add(realPos)
            } else if (rawPoints.size > 1) {
                // Ensure the curve terminates precisely at realPos
                rawPoints[rawPoints.size - 1] = realPos
            }

            if (rawPoints.size < 2) continue

            // Convert to relative coordinates with origin at delayedPos
            val relPoints = rawPoints.map { it.subtract(delayedPos) }

            // Translate poseStack to delayedPos relative to camera (avoids float precision loss)
            val delayedOrigin = delayedPos.subtract(cameraPosition)
            val poseStack = PoseStack()
            poseStack.translate(delayedOrigin.x, delayedOrigin.y, delayedOrigin.z)

            renderStreamlines(
                poseStack,
                collector,
                relPoints,
                tracked.halfWidth,
                tracked.height,
                strands,
                width,
                animTime,
                alphaFade,
                accent
            )
        }
    }

    private fun renderStreamlines(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        points: List<Vec3>,
        halfWidth: Double,
        height: Double,
        strands: Int,
        width: Float,
        animTime: Double,
        alphaFade: Float,
        accent: cn.reverie.render.nanovg.NanoVGColor
    ) {
        val segments = 24
        val ribs = 8

        // Precompute sampled backbone curve, tangents, and horizontal normal vectors
        val backbone = Array(segments + 1) { s ->
            val u = s.toDouble() / segments
            val pos = sampleSpline(points, u)
            val tan = getSplineTangent(points, u)
            val norm = getSplineNormal(tan)
            Triple(pos, tan, norm)
        }

        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent()) { pose, consumer ->
            val effectiveStrands = strands.coerceIn(4, 48)

            // 1. Render longitudinal streamlines distributed across the entity's 3D volume
            for (k in 0 until effectiveStrands) {
                val heightRatio = if (effectiveStrands > 1) k.toDouble() / (effectiveStrands - 1) else 0.5
                val yOffset = 0.10 * height + heightRatio * (height * 0.80)
                val wOffset = sin(k * 2.39996) * (halfWidth * 0.65) // Strictly inside entity bounding box

                var prevV: Vec3? = null
                var prevColor = 0

                for (s in 0..segments) {
                    val u = s.toDouble() / segments
                    val (curvePos, _, norm) = backbone[s]

                    // Exact 3D position along the curved trajectory
                    val v = Vec3(
                        curvePos.x + norm.x * wOffset,
                        curvePos.y + yOffset,
                        curvePos.z + norm.z * wOffset
                    )

                    val alphaBase = 0.15f + 0.80f * u.toFloat()
                    val pulse = (sin(u * 12.0 - animTime * 6.0 + k * 0.4) * 0.5 + 0.5).toFloat()
                    val alpha = (alphaBase * (0.75f + 0.25f * pulse) * alphaFade).coerceIn(0.01f, 1.0f)
                    val color = accent.multiplyAlpha(alpha).toArgb()

                    if (prevV != null) {
                        val diff = v.subtract(prevV)
                        val normal = if (diff.lengthSqr() > 1e-6) diff.normalize() else Vec3(0.0, 1.0, 0.0)

                        consumer.addVertex(pose, prevV.x.toFloat(), prevV.y.toFloat(), prevV.z.toFloat())
                            .setColor(prevColor)
                            .setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
                            .setLineWidth(width)

                        consumer.addVertex(pose, v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
                            .setColor(color)
                            .setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
                            .setLineWidth(width)
                    }

                    prevV = v
                    prevColor = color
                }
            }

            // 2. Render subtle transverse rib frames along the curve showing entity orientation
            val ribStep = (segments / ribs).coerceAtLeast(1)
            for (j in 0..ribs) {
                val segIdx = (j * ribStep).coerceAtMost(segments)
                val u = segIdx.toDouble() / segments
                val (pos, _, norm) = backbone[segIdx]

                val alphaBase = 0.10f + 0.50f * u.toFloat()
                val alpha = (alphaBase * 0.70f * alphaFade).coerceIn(0.01f, 1.0f)
                val color = accent.multiplyAlpha(alpha).toArgb()

                // Vertical spine rib
                val bottom = Vec3(pos.x, pos.y + 0.10 * height, pos.z)
                val top = Vec3(pos.x, pos.y + 0.90 * height, pos.z)
                val normalY = Vec3(0.0, 1.0, 0.0)

                consumer.addVertex(pose, bottom.x.toFloat(), bottom.y.toFloat(), bottom.z.toFloat())
                    .setColor(color)
                    .setNormal(pose, normalY.x.toFloat(), normalY.y.toFloat(), normalY.z.toFloat())
                    .setLineWidth(width * 0.7f)

                consumer.addVertex(pose, top.x.toFloat(), top.y.toFloat(), top.z.toFloat())
                    .setColor(color)
                    .setNormal(pose, normalY.x.toFloat(), normalY.y.toFloat(), normalY.z.toFloat())
                    .setLineWidth(width * 0.7f)

                // Horizontal waist rib
                val halfW = halfWidth * 0.65
                val left = Vec3(pos.x - norm.x * halfW, pos.y + 0.50 * height, pos.z - norm.z * halfW)
                val right = Vec3(pos.x + norm.x * halfW, pos.y + 0.50 * height, pos.z + norm.z * halfW)

                consumer.addVertex(pose, left.x.toFloat(), left.y.toFloat(), left.z.toFloat())
                    .setColor(color)
                    .setNormal(pose, normalY.x.toFloat(), normalY.y.toFloat(), normalY.z.toFloat())
                    .setLineWidth(width * 0.7f)

                consumer.addVertex(pose, right.x.toFloat(), right.y.toFloat(), right.z.toFloat())
                    .setColor(color)
                    .setNormal(pose, normalY.x.toFloat(), normalY.y.toFloat(), normalY.z.toFloat())
                    .setLineWidth(width * 0.7f)
            }
        }
    }

    private fun sampleSpline(points: List<Vec3>, u: Double): Vec3 {
        val count = points.size
        if (count == 0) return Vec3.ZERO
        if (count == 1) return points[0]
        if (count == 2) {
            val p0 = points[0]
            val p1 = points[1]
            return Vec3(
                Mth.lerp(u, p0.x, p1.x),
                Mth.lerp(u, p0.y, p1.y),
                Mth.lerp(u, p0.z, p1.z)
            )
        }

        val clampedU = u.coerceIn(0.0, 1.0)
        val numSegments = count - 1
        val globalT = clampedU * numSegments
        val segIdx = globalT.toInt().coerceIn(0, numSegments - 1)
        val t = (globalT - segIdx).coerceIn(0.0, 1.0)

        val p1 = points[segIdx]
        val p2 = points[segIdx + 1]
        val p0 = if (segIdx > 0) points[segIdx - 1] else p1.scale(2.0).subtract(p2)
        val p3 = if (segIdx + 2 < count) points[segIdx + 2] else p2.scale(2.0).subtract(p1)

        return catmullRomCentripetal(p0, p1, p2, p3, t)
    }

    private fun catmullRomCentripetal(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Double): Vec3 {
        val d01 = Math.sqrt(p0.distanceTo(p1).coerceAtLeast(1e-4))
        val d12 = Math.sqrt(p1.distanceTo(p2).coerceAtLeast(1e-4))
        val d23 = Math.sqrt(p2.distanceTo(p3).coerceAtLeast(1e-4))

        val t0 = 0.0
        val t1 = t0 + d01
        val t2 = t1 + d12
        val t3 = t2 + d23

        val currentT = t1 + t * (t2 - t1)

        val invT1T0 = 1.0 / (t1 - t0)
        val invT2T1 = 1.0 / (t2 - t1)
        val invT3T2 = 1.0 / (t3 - t2)

        val a1 = p0.scale((t1 - currentT) * invT1T0).add(p1.scale((currentT - t0) * invT1T0))
        val a2 = p1.scale((t2 - currentT) * invT2T1).add(p2.scale((currentT - t1) * invT2T1))
        val a3 = p2.scale((t3 - currentT) * invT3T2).add(p3.scale((currentT - t2) * invT3T2))

        val invT2T0 = 1.0 / (t2 - t0)
        val invT3T1 = 1.0 / (t3 - t1)

        val b1 = a1.scale((t2 - currentT) * invT2T0).add(a2.scale((currentT - t0) * invT2T0))
        val b2 = a2.scale((t3 - currentT) * invT3T1).add(a3.scale((currentT - t1) * invT3T1))

        return b1.scale((t2 - currentT) * invT2T1).add(b2.scale((currentT - t1) * invT2T1))
    }

    private fun getSplineTangent(points: List<Vec3>, u: Double): Vec3 {
        val eps = 0.01
        val u1 = (u - eps).coerceAtLeast(0.0)
        val u2 = (u + eps).coerceAtMost(1.0)
        val delta = sampleSpline(points, u2).subtract(sampleSpline(points, u1))
        return if (delta.lengthSqr() > 1e-6) delta.normalize() else Vec3(0.0, 0.0, 1.0)
    }

    private fun getSplineNormal(tangent: Vec3): Vec3 {
        val nx = -tangent.z
        val nz = tangent.x
        val lenSqr = nx * nx + nz * nz
        return if (lenSqr > 1e-6) {
            val len = Math.sqrt(lenSqr)
            Vec3(nx / len, 0.0, nz / len)
        } else {
            Vec3(1.0, 0.0, 0.0)
        }
    }

    private class TrackedPlayer(
        val entityId: Int,
        initialPosition: Vec3,
        val halfWidth: Double,
        val height: Double
    ) {
        @Volatile
        var serverPosition: Vec3 = initialPosition
            private set

        @Volatile
        private var prevTickPosition: Vec3 = initialPosition

        @Volatile
        private var currTickPosition: Vec3 = initialPosition

        private val waypoints = ArrayList<Vec3>()

        @Synchronized
        fun onTick() {
            prevTickPosition = currTickPosition
            currTickPosition = serverPosition
        }

        @Synchronized
        fun syncToEntity(position: Vec3) {
            serverPosition = position
            currTickPosition = position
            prevTickPosition = position
            waypoints.clear()
        }

        @Synchronized
        fun moveRelative(x: Double, y: Double, z: Double) {
            moveTo(serverPosition.add(x, y, z))
        }

        @Synchronized
        fun moveTo(position: Vec3) {
            serverPosition = position
            currTickPosition = position
            if (position.distanceToSqr(prevTickPosition) > 64.0) {
                prevTickPosition = position
                waypoints.clear()
            } else {
                if (waypoints.isEmpty() || position.distanceToSqr(waypoints.last()) > 0.0009) {
                    if (waypoints.size >= 120) {
                        waypoints.removeAt(0)
                    }
                    waypoints.add(position)
                }
            }
        }

        @Synchronized
        fun onQueueReleased() {
            waypoints.clear()
        }

        @Synchronized
        fun getWaypointsSnapshot(): List<Vec3> {
            return ArrayList(waypoints)
        }

        fun interpolatedPosition(partialTicks: Float): Vec3 {
            val prev = prevTickPosition
            val curr = currTickPosition
            val pt = partialTicks.toDouble().coerceIn(0.0, 1.0)
            return Vec3(
                Mth.lerp(pt, prev.x, curr.x),
                Mth.lerp(pt, prev.y, curr.y),
                Mth.lerp(pt, prev.z, curr.z)
            )
        }
    }
}

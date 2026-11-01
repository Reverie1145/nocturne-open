package cn.reverie.module.combat

import cn.reverie.module.combat.killaura.KillAuraRotationMixer
import cn.reverie.module.combat.killaura.KillAuraHypixelABlock
import cn.reverie.module.combat.killaura.KillAuraWatchdogBlock

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ColorSetting
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.combat.killaura.attackForVelocityImpl
import cn.reverie.module.combat.killaura.canAttack
import cn.reverie.module.combat.killaura.canBlockImpl
import cn.reverie.module.combat.killaura.combatBox
import cn.reverie.module.combat.killaura.combatDistance
import cn.reverie.module.combat.killaura.doAttack
import cn.reverie.module.combat.killaura.getCps
import cn.reverie.module.combat.killaura.isHoldingSword
import cn.reverie.module.combat.killaura.selectTarget
import cn.reverie.module.combat.killaura.rayCastCombat
import cn.reverie.module.combat.killaura.updateFakeBlockRenderState
import cn.reverie.module.combat.killaura.updateTargets
import cn.reverie.module.combat.killaura.wantsRealBlockImpl
import cn.reverie.module.world.Scaffold
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.misc.TimerUtil
import cn.reverie.util.misc.ClickCadence
import cn.reverie.util.player.VanillaClickQueue
import cn.reverie.util.rotation.*
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.EntityHitResult
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil
import kotlin.math.sin
object KillAura : Module(
    name = "KillAura",
    description = "Automatically attacks nearby entities",
    category = Category.COMBAT,
    keyCode = GLFW.GLFW_KEY_R
) {
    val targetMode = ModeSetting("Mode", "Target selection style", "Single", listOf("Single", "Switch"))
    val switchDelay = NumberSetting("Switch Delay", "Target switch delay in ms", 200.0, 0.0, 1000.0, 50.0, { targetMode.isMode("Switch") })
    val priority = ModeSetting("Priority", "Target sort order", "Distance", listOf("Distance", "Health", "Fov", "LivingTime", "Armor"))
    val attackMode = ModeSetting("Combat Mode", "Attack speed mode", "1.8", listOf("1.8", "1.9+"))
    val maxCps = NumberSetting("Max CPS", "Maximum clicks per second; above 20 needs Max Attacks Per Tick > 1", 10.0, 1.0, 100.0, 1.0, { attackMode.isMode("1.8") && attackMethod.isMode("Direct") })
    val minCps = NumberSetting("Min CPS", "Minimum clicks per second", 7.0, 1.0, 100.0, 1.0, { attackMode.isMode("1.8") && attackMethod.isMode("Direct") })
    val maxAttacksPerTick = NumberSetting("Max Attacks Per Tick", "Attack+swing pairs allowed in one tick; effective CPS cap is 20 x this", 1.0, 1.0, 5.0, 1.0, { attackMode.isMode("1.8") && attackMethod.isMode("Direct") })
    val extraAttackDelay = NumberSetting("Extra Attack Delay", "Additional milliseconds between attacks", 0.0, 0.0, 500.0, 5.0, { attackMethod.isMode("Direct") })
    val attackMethod = ModeSetting("Attack Method", "Direct keeps existing timing; Human schedules repeated attack clicks", "Direct", listOf("Direct", "Human"))
    val humanAttackCps = NumberSetting("Human Attack CPS", "Attack click attempts per second", 18.0, 1.0, 100.0, 1.0, { attackMethod.isMode("Human") })
    val cooldownThreshold = NumberSetting("Cooldown Threshold", "Required attack charge in 1.9+ mode", 1.0, 0.1, 1.0, 0.05, { attackMode.isMode("1.9+") })
    val onlyGroundAttack = BooleanSetting("Only Ground Attack", "Attack only while grounded", false)
    val onlySwordAttack = BooleanSetting("Only Sword Attack", "Require a sword for attacks", false)
    val pauseWhileUsing = BooleanSetting("Pause While Using", "Do not attack during item use", false)
    val maxHurtTimeAttack = NumberSetting("Max HurtTime Attack", "Highest target hurtTime allowed for attack", 20.0, 0.0, 20.0, 1.0)
    val minHurtTimeAttack = NumberSetting("Min HurtTime Attack", "Lowest target hurtTime allowed for attack", 0.0, 0.0, 20.0, 1.0)
    val keepSwing = BooleanSetting("Keep Swing", "Continuous hand swinging in 1.9+", false, { attackMode.isMode("1.9+") })
    val attackRange = NumberSetting("Range", "Attack range in blocks", 3.0, 3.0, 8.0, 0.1)
    val blockRange = NumberSetting("Block Range", "AutoBlock range", 4.0, 3.0, 8.0, 0.1, { autoBlockMode.isMode("Fake") || autoBlockMode.isMode("HypixelA") })
    val wallRange = NumberSetting("Wall Range", "Range through walls", 0.0, 0.0, 8.0, 0.1)
    val rotationRange = NumberSetting("Rotation Range", "Range to begin rotating to target", 4.0, 3.0, 8.0, 0.1)
    val minTargetRange = NumberSetting("Min Target Range", "Ignore entities closer than this distance", 0.0, 0.0, 6.0, 0.1)
    val maxTargetFov = NumberSetting("Max Target FOV", "Maximum angle from view to target", 180.0, 5.0, 180.0, 5.0)
    val maxTargetHeight = NumberSetting("Max Target Height", "Maximum vertical separation in blocks", 16.0, 0.5, 32.0, 0.5)
    val minTargetHealth = NumberSetting("Min Target Health", "Ignore entities below this health", 0.0, 0.0, 100.0, 1.0)
    val maxTargetHealth = NumberSetting("Max Target Health", "Ignore entities above this health; zero disables", 0.0, 0.0, 1000.0, 5.0)
    val minTargetAge = NumberSetting("Min Target Age", "Minimum ticks since entity spawned", 0.0, 0.0, 200.0, 1.0)
    val maxTargetAge = NumberSetting("Max Target Age", "Maximum entity age; zero disables", 0.0, 0.0, 12000.0, 100.0)
    val maxTargetSpeed = NumberSetting("Max Target Speed", "Maximum target motion per tick; zero disables", 0.0, 0.0, 3.0, 0.05)
    val requireLineOfSight = BooleanSetting("Require Line Of Sight", "Exclude entities behind blocks", false)
    val maxCandidates = NumberSetting("Max Candidates", "Limit sorted target candidates; zero keeps all", 0.0, 0.0, 50.0, 1.0)
    val switchSelection = ModeSetting("Switch Selection", "How Switch chooses a candidate", "Random", listOf("Random", "Sequential", "First", "Last"), { targetMode.isMode("Switch") })
    val autoBlockMode = ModeSetting("AutoBlock Mode", "Fake pose, the WatchdogBW cycle from Aura.java, or HypixelA swing-predicted blocking", "None", listOf("None", "Fake", "WatchdogBW", "HypixelA"))
    val autoBlockOnlyPressed = BooleanSetting("Only Pressed", "Run WatchdogBW only while holding right mouse", false, { autoBlockMode.isMode("WatchdogBW") })
    val rotationSpeed = NumberSetting("Rotation Speed", "Aim rotation speed in deg/tick", 180.0, 0.0, 360.0, 5.0, { rotationMode.isMode("Classic") })
    val rotationMode = ModeSetting("Rotation Mode", "Standalone turns or Mix controls", "Classic", listOf("Classic", "Focus", "Human", "Cautious", "Glide", "Pulse", "Mix"))
    val mixTurn = ModeSetting("Mix Turn", "Rotation speed curve", "Constant", KillAuraRotationMixer.turns, { rotationMode.isMode("Mix") })
    val mixPoint = ModeSetting("Mix Point", "Target hitbox point path", "Nearest", KillAuraRotationMixer.points, { rotationMode.isMode("Mix") })
    val mixBaseSpeed = NumberSetting("Mix Base Speed", "Base degrees per tick", 120.0, 1.0, 360.0, 1.0, { rotationMode.isMode("Mix") })
    val mixMinSpeed = NumberSetting("Mix Min Speed", "Lower speed clamp", 25.0, 1.0, 360.0, 1.0, { rotationMode.isMode("Mix") })
    val mixMaxSpeed = NumberSetting("Mix Max Speed", "Upper speed clamp", 180.0, 1.0, 360.0, 1.0, { rotationMode.isMode("Mix") })
    val mixSpeedVariation = NumberSetting("Mix Speed Variation", "Curve amplitude in degrees per tick", 45.0, 0.0, 150.0, 1.0, { rotationMode.isMode("Mix") })
    val mixCycle = NumberSetting("Mix Cycle", "Ticks per turn/point cycle", 14.0, 2.0, 80.0, 1.0, { rotationMode.isMode("Mix") })
    val mixPointSpread = NumberSetting("Mix Point Spread", "Maximum travel across target hitbox", 0.25, 0.0, 0.4, 0.01, { rotationMode.isMode("Mix") })
    val mixAimInset = NumberSetting("Mix Aim Inset", "Keep aim point inside target hitbox", 0.08, 0.0, 0.3, 0.01, { rotationMode.isMode("Mix") })
    val mixHeightOffset = NumberSetting("Mix Height Offset", "Vertical target-point offset", 0.0, -0.35, 0.35, 0.01, { rotationMode.isMode("Mix") })
    val mixSideOffset = NumberSetting("Mix Side Offset", "Horizontal target-point offset", 0.0, -0.35, 0.35, 0.01, { rotationMode.isMode("Mix") })
    val mixDepthOffset = NumberSetting("Mix Depth Offset", "Depth target-point offset", 0.0, -0.35, 0.35, 0.01, { rotationMode.isMode("Mix") })
    val mixPrediction = NumberSetting("Mix Prediction", "Target motion lead in ticks", 0.0, 0.0, 2.0, 0.05, { rotationMode.isMode("Mix") })
    val mixSelfPrediction = NumberSetting("Mix Self Prediction", "Player motion lead in ticks", 0.0, 0.0, 2.0, 0.05, { rotationMode.isMode("Mix") })
    val mixNoiseMode = ModeSetting("Mix Noise", "Point variation pattern", "Off", listOf("Off", "Sine", "Drift", "Step", "White"), { rotationMode.isMode("Mix") })
    val mixNoiseAmount = NumberSetting("Mix Noise Amount", "Point variation inside hitbox", 0.04, 0.0, 0.2, 0.01, { rotationMode.isMode("Mix") && !mixNoiseMode.isMode("Off") })
    val mixNoiseCycle = NumberSetting("Mix Noise Cycle", "Ticks per noise cycle", 10.0, 2.0, 40.0, 1.0, { rotationMode.isMode("Mix") && !mixNoiseMode.isMode("Off") })
    val mixDistanceSpeed = NumberSetting("Mix Distance Speed", "Additional turn speed per block to target", 0.0, -30.0, 30.0, 1.0, { rotationMode.isMode("Mix") })
    val mixMotionSpeed = NumberSetting("Mix Motion Speed", "Additional turn speed from target motion", 0.0, -100.0, 100.0, 1.0, { rotationMode.isMode("Mix") })
    val mixYawOffset = NumberSetting("Mix Yaw Offset", "Bounded yaw offset in degrees", 0.0, -5.0, 5.0, 0.1, { rotationMode.isMode("Mix") })
    val mixPitchOffset = NumberSetting("Mix Pitch Offset", "Bounded pitch offset in degrees", 0.0, -5.0, 5.0, 0.1, { rotationMode.isMode("Mix") })
    val mixYawDeadzone = NumberSetting("Mix Yaw Deadzone", "Ignore tiny yaw changes below this threshold", 0.0, 0.0, 5.0, 0.1, { rotationMode.isMode("Mix") })
    val mixPitchDeadzone = NumberSetting("Mix Pitch Deadzone", "Ignore tiny pitch changes below this threshold", 0.0, 0.0, 5.0, 0.1, { rotationMode.isMode("Mix") })
    val mixMaxYawStep = NumberSetting("Mix Max Yaw Step", "Independent per-tick yaw step limit; zero disables", 0.0, 0.0, 180.0, 1.0, { rotationMode.isMode("Mix") })
    val mixMaxPitchStep = NumberSetting("Mix Max Pitch Step", "Independent per-tick pitch step limit; zero disables", 0.0, 0.0, 90.0, 1.0, { rotationMode.isMode("Mix") })
    val movementFixMode = ModeSetting("MovementFix Mode", "Movement vector alignment", "None", listOf("None", "Silent", "Strict"))
    val rayCast = BooleanSetting("Ray Cast", "Verify crosshair raycast before attacking", false)
    val keepSprint = BooleanSetting("Keep Sprint", "Keep sprinting and avoid the vanilla hit slowdown", false)

    val players = BooleanSetting("Players", "Target players", true)
    val monsters = BooleanSetting("Monsters", "Target monsters", true)
    val animals = BooleanSetting("Animals", "Target animals", false)
    val invisibles = BooleanSetting("Invisibles", "Target invisible entities", false)

    // Target ESP (2D)
    val targetEsp = BooleanSetting("Target ESP", "Render 2D target indicator", true)
    val espMode = ModeSetting(
        "ESP Mode",
        "Visual style of 2D Target ESP",
        "Corners",
        listOf("Corners", "Box"),
        { targetEsp.value }
    )
    val multiTarget = BooleanSetting("Multi Target", "Display on all aura targets in range", true, { targetEsp.value })
    val espColorMode = ModeSetting(
        "ESP Color",
        "Color palette for Target ESP",
        "Theme",
        listOf("Theme", "Custom", "Health", "Astolfo", "Rainbow"),
        { targetEsp.value }
    )
    val espColor = ColorSetting(
        "ESP Custom Color",
        "Primary color for custom mode",
        NanoVGColor.fromHex("#FF2A85"),
        visibility = { targetEsp.value && espColorMode.isMode("Custom") }
    )
    val espColor2 = ColorSetting(
        "ESP Custom Color 2",
        "Secondary color for custom gradient",
        NanoVGColor.fromHex("#00F0FF"),
        visibility = { targetEsp.value && espColorMode.isMode("Custom") }
    )
    val espGlow = BooleanSetting("Intense Glow", "High-intensity neon light pollution bloom", true, { targetEsp.value })
    val espCornerSize = NumberSetting("Corner Ratio", "Length ratio of corner brackets", 0.30, 0.15, 0.50, 0.05, { targetEsp.value && espMode.isMode("Corners") })
    val espAlpha = NumberSetting("ESP Opacity", "Visual opacity multiplier", 0.95, 0.2, 1.0, 0.05, { targetEsp.value })

    init {
        listen(Render2DEvent::class.java) { TargetESPRenderer.onRender(it.partialTicks) }
        listen(PacketEvent::class.java) { KillAuraWatchdogBlock.onPacket(it) }
        attackMethod.onChanged = {
            humanAttackCadence.reset()
            attackTimer.reset()
            nextDirectAttackDelay = -1L
        }
        autoBlockMode.onChanged = { mode ->
            KillAuraWatchdogBlock.reset()
            KillAuraHypixelABlock.reset()
            renderBlock = false
            fakeBlockRenderTarget = null
            fakeBlockOutOfRangeTicks = 0
            if (autoBlockMode.modes.none { it.equals(mode, ignoreCase = true) }) autoBlockMode.value = "None"
            KillAuraWatchdogBlock.onEnable()
        }
        autoBlockOnlyPressed.onChanged = {
            KillAuraWatchdogBlock.reset()
            KillAuraWatchdogBlock.onEnable()
        }
    }

    val targets = ArrayList<LivingEntity>()
    private const val TICK_MILLIS = 50L
    internal val switchTimer = TimerUtil()
    internal val attackTimer = TimerUtil()
    internal val humanAttackCadence = ClickCadence()
    private var nextDirectAttackDelay = -1L
    private var scheduledMinCps = Double.NaN
    private var scheduledMaxCps = Double.NaN
    private var scheduledExtraDelay = Double.NaN
    internal var switchIndex = -1

    var target: LivingEntity? = null
        internal set

    var rotations: Rotation? = null
        internal set

    var renderBlock = false
        internal set

    internal var fakeBlockRenderTarget: LivingEntity? = null
    internal var fakeBlockOutOfRangeTicks = 0
    internal var lastAttackTick = -1

    var blocking = false
        internal set

    val blockTick: Int get() = KillAuraWatchdogBlock.blockTicks
    val rightMouseABActive: Boolean get() = KillAuraWatchdogBlock.rightMouseABActive

    var blockingState = false
        internal set

    var attackedThisTick = false
        internal set

    var attackSwingSequence = 0L
        internal set

    @JvmStatic
    fun shouldKeepSprint(player: LocalPlayer): Boolean = enabled && keepSprint.value &&
        player.isSprinting && !Criticals.shouldPauseSprint(player)

    @JvmStatic
    fun shouldSuppressUseItem(): Boolean = enabled && autoBlockMode.isMode("WatchdogBW") && target != null

    @JvmStatic
    fun shouldSuppressAttackInput(): Boolean = target != null && Minecraft.getInstance().player?.let {
        KillAuraWatchdogBlock.blocksInputAttack(it)
    } == true

    @JvmStatic
    fun ownsUseItem(): Boolean = KillAuraWatchdogBlock.ownsUseItem() || KillAuraHypixelABlock.ownsUseItem()

    internal fun isLegacySwordStack(stack: ItemStack): Boolean =
        KillAuraWatchdogBlock.isLegacySwordStack(stack) || KillAuraHypixelABlock.isLegacySwordStack(stack)

    internal fun legacyBlockingItem(player: LocalPlayer): ItemStack? =
        KillAuraWatchdogBlock.legacyBlockingItem(player) ?: KillAuraHypixelABlock.legacyBlockingItem(player)

    @JvmStatic
    fun hasWatchdogAutoBlock(): Boolean = enabled && autoBlockMode.isMode("WatchdogBW")

    @JvmStatic
    fun shouldPauseSprint(player: LocalPlayer): Boolean =
        Criticals.shouldPauseSprint(player) || KillAuraWatchdogBlock.shouldPauseSprint(player)

    override val suffix: String
        get() = targetMode.value

    override fun onEnable() {
        reset()
        KillAuraWatchdogBlock.onEnable()
    }

    override fun onDisable() {
        reset()
    }

    @JvmStatic
    fun onWorldChange() {
        KillAuraWatchdogBlock.reset(discardPackets = true)
        KillAuraHypixelABlock.reset(discardPackets = true)
        reset()
    }

    @JvmStatic
    fun reset() {
        KillAuraWatchdogBlock.reset()
        KillAuraHypixelABlock.reset()
        TargetESPRenderer.reset()
        clearCombatTargets()
        renderBlock = false
        fakeBlockRenderTarget = null
        fakeBlockOutOfRangeTicks = 0
    }

    private fun clearCombatTargets() {
        switchTimer.reset()
        attackTimer.reset()
        humanAttackCadence.reset()
        nextDirectAttackDelay = -1L
        switchIndex = -1
        rotations = null
        targets.clear()
        target = null
        attackedThisTick = false
    }

    fun getMoveFix(): MoveFix = when {
        movementFixMode.isMode("Silent") -> MoveFix.Silent
        movementFixMode.isMode("Strict") -> MoveFix.Strict
        else -> MoveFix.None
    }

    fun getRotationSpeed(): Double = if (rotationMode.isMode("Mix")) KillAuraRotationMixer.speed(
        mixTurn.value, Minecraft.getInstance().player?.tickCount ?: 0, mixBaseSpeed.value +
            (target?.let { entity -> Minecraft.getInstance().player?.let { combatDistance(it, entity) } } ?: 0.0) * mixDistanceSpeed.value +
            (target?.deltaMovement?.length() ?: 0.0) * mixMotionSpeed.value,
        mixMinSpeed.value, mixMaxSpeed.value, mixCycle.value, mixSpeedVariation.value
    ) else when (rotationMode.value) {
        "Focus" -> 35.0
        "Human" -> 55.0 + (Minecraft.getInstance().player?.tickCount ?: 0) % 17
        "Cautious" -> 30.0
        "Glide" -> 65.0
        "Pulse" -> if ((Minecraft.getInstance().player?.tickCount ?: 0) % 4 == 0) 120.0 else 30.0
        else -> rotationSpeed.value
    }

    fun shouldRotate(): Boolean = enabled && target != null && !check()

    val isRenderBlock: Boolean
        get() = renderBlock
            || blocking
            || blockingState
            || (autoBlockMode.isMode("Fake") && target != null && canBlock(Minecraft.getInstance().player ?: return false, target))

    private fun check(): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: run { KillAuraWatchdogBlock.reset(discardPackets = true); return true }
        if (mc.level == null) {
            KillAuraWatchdogBlock.reset(discardPackets = true)
            return true
        }

        if (Scaffold.enabled) {
            if (target != null || targets.isNotEmpty() || rotations != null) {
                targets.clear()
                target = null
                rotations = null
                attackedThisTick = false
            }
            return true
        }

        if (!player.isAlive || player.isSpectator) {
            return true
        }
        return false
    }


    @JvmStatic
    fun onUpdate(player: LocalPlayer) {
        if (!enabled) return
        if (check()) {
            if (autoBlockMode.isMode("HypixelA")) KillAuraHypixelABlock.stop()
            return
        }

        if (attackMethod.isMode("Direct") || KillAuraWatchdogBlock.controlsAttack()) attackedThisTick = false
        updateTargets(player)
        if (targets.isEmpty()) {
            clearCombatTargets()
        } else {
            selectTarget()
        }
        val current = target
        // WatchdogBW decides allowAttack after its right-mouse transition in Tick PRE.
        if (hasWatchdogAutoBlock()) return
        val hypixelA = autoBlockMode.isMode("HypixelA")
        if (targets.isEmpty()) {
            if (hypixelA) KillAuraHypixelABlock.update(player, null)
            return
        }
        if (autoBlockMode.isMode("Fake")) {
            // Latch the visual pose from the freshly selected target, before
            // this tick's attack and the first-person render consume it.
            updateFakeBlockRenderState(player, current)
        }
        attackNormally(player)
        // HypixelA blocks after this tick's attack and before the movement packet.
        if (hypixelA) KillAuraHypixelABlock.update(player, current)
    }

    private fun attackNormally(player: LocalPlayer) {
        val current = target
        if (current != null) {
            if (canAttack(player, current)) {
                if (keepSwing.value) {
                    player.swing(InteractionHand.MAIN_HAND)
                }

                val cooldownReady = !attackMode.isMode("1.9+") || player.getAttackStrengthScale(0.5f) >= cooldownThreshold.value

                if (attackMethod.isMode("Direct") && cooldownReady) {
                    attackDirect(player, current)
                }
            }
        }
    }

    /**
     * Direct attacks run once per client tick, so a plain "interval elapsed" test quantises the
     * rate to multiples of 50 ms (15 CPS behaves like 10). Elapsed time is therefore treated as a
     * budget: every attack spends one sampled interval, the remainder (under one tick) carries
     * over, and up to [maxAttacksPerTick] attack+swing pairs may fire in the same tick for rates
     * above 20 CPS. All pairs target the same entity with the same rotation, which Grim's
     * MultiInteract/PacketOrderB checks accept (attack is always immediately followed by its swing).
     */
    private fun attackDirect(player: LocalPlayer, entity: LivingEntity) {
        val perTick = if (attackMode.isMode("1.8")) maxAttacksPerTick.getInt().coerceIn(1, 5) else 1
        var budget = attackTimer.getTime()
        var done = 0
        while (done < perTick) {
            val interval = nextAttackIntervalMillis().coerceAtLeast(1L)
            if (budget < interval) break
            val sequence = attackSwingSequence
            doAttack(player, entity)
            if (attackSwingSequence == sequence) break
            budget = (budget - interval).coerceAtLeast(0L)
            done++
        }
        if (done > 0) attackTimer.time = System.currentTimeMillis() - budget.coerceAtMost(TICK_MILLIS)
    }

    /** Sample once per completed attack so Criticals can plan against the actual next interval. */
    internal fun nextAttackIntervalMillis(): Long {
        if (attackMethod.isMode("Human")) return ceil(1000.0 / humanAttackCps.value.coerceIn(1.0, 100.0)).toLong()
        if (nextDirectAttackDelay < 0L || scheduledMinCps != minCps.value || scheduledMaxCps != maxCps.value || scheduledExtraDelay != extraAttackDelay.value) {
            scheduledMinCps = minCps.value
            scheduledMaxCps = maxCps.value
            scheduledExtraDelay = extraAttackDelay.value
            nextDirectAttackDelay = 1000L / getCps().coerceAtLeast(1L) + extraAttackDelay.value.toLong()
        }
        return nextDirectAttackDelay
    }

    @JvmStatic
    fun queueHumanClicks() {
        if (!enabled || !attackMethod.isMode("Human")) return
        val player = Minecraft.getInstance().player ?: return
        if (KillAuraWatchdogBlock.blocksInputAttack(player)) return
        attackedThisTick = false
        val clicks = humanAttackCadence.attempts(System.currentTimeMillis(), humanAttackCps.value)
        if (clicks <= 0) return
        val mc = Minecraft.getInstance()
        val current = target ?: return
        if (!current.isAlive) return
        if (mc.gameMode == null || mc.gui.screen() != null || player.isSpectator || !player.isAlive) return
        if (Scaffold.enabled || !canAttack(player, current)) return
        if (player.isUsingItem) return
        val view = RotationManager.currentRotation ?: rotations ?: return
        val reach = maxOf(attackRange.value, combatDistance(player, current) + 1.0)
        val hit = rayCastCombat(view, reach, player) as? EntityHitResult ?: return
        if (hit.entity !== current) return
        if (attackMode.isMode("1.9+") && player.getAttackStrengthScale(0.5f) < cooldownThreshold.value) return
        VanillaClickQueue.overrideHit(mc, hit)
        VanillaClickQueue.left(mc, if (attackMode.isMode("1.9+")) clicks.coerceAtMost(1) else clicks)
    }

    @JvmStatic
    fun onAttackComplete(player: LocalPlayer, entity: net.minecraft.world.entity.Entity) {
        if (enabled && entity === target) {
            attackedThisTick = true
            lastAttackTick = player.tickCount
            attackSwingSequence++
            attackTimer.reset()
            nextDirectAttackDelay = -1L
        }
    }

    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) {
        if (!enabled || check()) return

        val current = target
        if (current != null) {
            val targetBox = combatBox(current)
            val distance = VecCalculation.newGetDistanceToEntityBoxSafe(player, current, wallRange.value, targetBox)
            if (distance <= rotationRange.value) {
                val point = when (rotationMode.value) {
                    "Mix" -> mixAimPoint(player, current)
                    "Focus", "Human", "Cautious", "Glide", "Pulse" -> standaloneAimPoint(player, current)
                    else -> VecCalculation.newGetPointToEntityBoxSafe(player, current, wallRange.value, targetBox)
                }
                if (point != null) {
                    val base = RotationUtil.vecToRotation(point, false)
                    val rot = if (rotationMode.isMode("Mix")) mixBoundRotation(base) else base
                    rotations = rot
                    RotationManager.setRotations(rot, getMoveFix(), true, SmoothMode.ADVANCED, getRotationSpeed().toFloat(), 1, Priority.MEDIUM)
                }
            }
        }
    }

    private fun standaloneAimPoint(player: LocalPlayer, entity: LivingEntity): Vec3? {
        val box = combatBox(entity)
        val fallback = VecCalculation.newGetPointToEntityBoxSafe(player, entity, wallRange.value, box) ?: return null
        if (rotationMode.isMode("Cautious")) return fallback
        val tick = player.tickCount.toDouble()
        val (x, y, z) = when (rotationMode.value) {
            "Focus" -> Triple(0.5, 0.5, 0.5)
            "Human" -> Triple(0.5 + sin(tick * 0.12) * 0.13, 0.65 + sin(tick * 0.07) * 0.07, 0.5)
            "Glide" -> Triple(0.5, 0.7, 0.5)
            else -> Triple(0.5 + sin(tick * 0.35) * 0.18, 0.55, 0.5)
        }
        val point = Vec3(box.minX + box.xsize * x, box.minY + box.ysize * y, box.minZ + box.zsize * z)
        return if (player.eyePosition.distanceTo(point) <= wallRange.value ||
            VecCalculation.isNotBlocked(player.level(), player.eyePosition, point, player)) point else fallback
    }

    private fun mixBoundRotation(raw: Rotation): Rotation {
        val previous = RotationManager.currentRotation ?: return Rotation(
            raw.yaw + mixYawOffset.getFloat(), (raw.pitch + mixPitchOffset.getFloat()).coerceIn(-90f, 90f))
        val desiredYaw = raw.yaw + mixYawOffset.getFloat()
        val desiredPitch = (raw.pitch + mixPitchOffset.getFloat()).coerceIn(-90f, 90f)
        var yawDelta = net.minecraft.util.Mth.wrapDegrees(desiredYaw - previous.yaw)
        var pitchDelta = desiredPitch - previous.pitch
        if (kotlin.math.abs(yawDelta) < mixYawDeadzone.value) yawDelta = 0f
        if (kotlin.math.abs(pitchDelta) < mixPitchDeadzone.value) pitchDelta = 0f
        if (mixMaxYawStep.value > 0.0) yawDelta = yawDelta.coerceIn(-mixMaxYawStep.getFloat(), mixMaxYawStep.getFloat())
        if (mixMaxPitchStep.value > 0.0) pitchDelta = pitchDelta.coerceIn(-mixMaxPitchStep.getFloat(), mixMaxPitchStep.getFloat())
        return Rotation(previous.yaw + yawDelta, (previous.pitch + pitchDelta).coerceIn(-90f, 90f))
    }

    private fun mixAimPoint(player: LocalPlayer, entity: LivingEntity): Vec3? {
        val baseBox = combatBox(entity)
        val fallback = VecCalculation.newGetPointToEntityBoxSafe(player, entity, wallRange.value, baseBox) ?: return null
        if (mixPoint.isMode("Nearest") && mixPrediction.value == 0.0 && mixSelfPrediction.value == 0.0 &&
            mixNoiseMode.isMode("Off") && mixHeightOffset.value == 0.0 && mixSideOffset.value == 0.0 && mixDepthOffset.value == 0.0) return fallback
        val targetMovement = Velocity.bufferedMovement(entity) ?: entity.deltaMovement
        val box = baseBox.move(targetMovement.scale(mixPrediction.value))
        val raw = if (mixPoint.isMode("Nearest")) Triple(
            ((fallback.x - baseBox.minX) / baseBox.xsize).coerceIn(0.0, 1.0),
            ((fallback.y - baseBox.minY) / baseBox.ysize).coerceIn(0.0, 1.0),
            ((fallback.z - baseBox.minZ) / baseBox.zsize).coerceIn(0.0, 1.0)
        ) else KillAuraRotationMixer.point(mixPoint.value, player.tickCount, mixCycle.value, mixPointSpread.value)
        val eye = player.eyePosition.add(player.deltaMovement.scale(mixSelfPrediction.value))
        val noisePhase = player.tickCount / mixNoiseCycle.value
        val noise = when (mixNoiseMode.value) {
            "Sine" -> sin(noisePhase * Math.PI * 2.0)
            "Drift" -> sin(noisePhase * 1.4) * 0.7 + sin(noisePhase * 0.37) * 0.3
            "Step" -> if (player.tickCount / mixNoiseCycle.getInt().coerceAtLeast(2) % 2 == 0) 1.0 else -1.0
            "White" -> {
                val bits = player.tickCount.toLong() * 6364136223846793005L + 1442695040888963407L
                (bits ushr 33).toDouble() / 2147483647.0 * 2.0 - 1.0
            }
            else -> 0.0
        } * mixNoiseAmount.value
        val inset = mixAimInset.value
        val x = (raw.first + mixSideOffset.value + noise).coerceIn(inset, 1.0 - inset)
        val y = (raw.second + mixHeightOffset.value + noise * 0.5).coerceIn(inset, 1.0 - inset)
        val z = (raw.third + mixDepthOffset.value - noise).coerceIn(inset, 1.0 - inset)
        val point = Vec3(box.minX + box.xsize * x, box.minY + box.ysize * y, box.minZ + box.zsize * z)
        return if (eye.distanceTo(point) <= wallRange.value || VecCalculation.isNotBlocked(player.level(), eye, point, player)) point else fallback
    }

    @JvmStatic
    fun onPreTick(player: LocalPlayer, rotation: Rotation) {
        if (!enabled) return
        if (KillAuraWatchdogBlock.onPreTick(player, rotation) &&
            KillAuraWatchdogBlock.allowsRegularAttack() && !check()) {
            attackNormally(player)
        }
    }

    @JvmStatic
    fun onPostTick() {
        if (enabled) KillAuraWatchdogBlock.onPostTick()
    }

    @JvmStatic
    fun onPreMotion(player: LocalPlayer) {
        if (!enabled) return
        KillAuraWatchdogBlock.onPreMotion(player)
    }

    @JvmStatic
    fun onPostMotion(player: LocalPlayer) {
        if (!enabled || check()) return
        if (autoBlockMode.isMode("Fake") && target == null) {
            renderBlock = false
            fakeBlockRenderTarget = null
            fakeBlockOutOfRangeTicks = 0
        }
    }

    @JvmStatic
    fun onVisualTick(player: LocalPlayer) {
        if (!enabled || Scaffold.enabled || !shouldRotate()) {
            return
        }
        RotationManager.updateRenderAngles(player)
    }

    fun canBlock(player: LocalPlayer, target: LivingEntity?): Boolean = canBlockImpl(player, target)

    @JvmStatic
    fun wantsRealBlock(): Boolean = wantsRealBlockImpl()

    @JvmStatic
    fun attackForVelocity(player: LocalPlayer, entity: LivingEntity): Boolean = attackForVelocityImpl(player, entity)
}

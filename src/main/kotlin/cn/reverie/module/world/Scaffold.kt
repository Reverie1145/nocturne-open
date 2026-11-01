package cn.reverie.module.world

import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ColorSetting
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.combat.KillAura
import cn.reverie.module.combat.ProjectileAura
import cn.reverie.module.movement.Stuck
import cn.reverie.module.world.scaffold.*
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.misc.ItemSpoofUtil
import cn.reverie.util.misc.TimerUtil
import cn.reverie.util.misc.ClickCadence
import cn.reverie.util.player.VanillaClickQueue
import cn.reverie.util.player.BlockUtil
import cn.reverie.util.player.MovementUtil
import cn.reverie.util.rotation.MoveFix
import cn.reverie.util.rotation.Rotation
import cn.reverie.util.rotation.RotationManager
import cn.reverie.util.rotation.RotationUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.BlockHitResult
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/** Never Scaffold's placement/search core adapted to Nocturne's module and rotation hooks. */
object Scaffold : Module(
    name = "Scaffold",
    description = "Automatically places blocks under player",
    category = Category.WORLD,
    keyCode = GLFW.GLFW_KEY_G
) {
    val mode = ModeSetting("Mode", "Scaffold movement style", "Normal", listOf("Normal", "SameY", "Telly"))
    val pickMode = ModeSetting("Pick Mode", "Select blocks normally, spoof the rendered slot, or do not switch", "Normal", listOf("Normal", "Spoof", "Off"))
    val swingMode = ModeSetting("Swing Mode", "How a successful placement swings the hand", "Client", listOf("Client", "Server", "Off"))
    val rotationPoint = ModeSetting("Rotation Point", "Placement face point selection", "Normal", listOf("Normal", "Corner", "Nearest", "Reduced", "Center", "Inner", "Outer", "Forward", "Backward", "Left", "Right", "Top", "Bottom", "Random", "Stable Random", "Adaptive", "Near Eye", "Far Eye", "Sweep", "Orbit", "Diagonal", "Cross"))
    val searchStep = NumberSetting("Rotation Search Step", "Search interval for Reduced rotation points", 0.02, 0.01, 0.1, 0.01, { rotationPoint.isMode("Reduced") })
    val inset = NumberSetting("Inset", "Inset for Reduced rotation points", 0.1, 0.05, 0.1, 0.01, { rotationPoint.isMode("Reduced") })
    val jitter = BooleanSetting("Jitter", "Jitter Reduced rotation point samples", true, { rotationPoint.isMode("Reduced") })
    val searchMode = ModeSetting("Search Mode", "How a support block is selected", "Normal", listOf("Normal", "Distance"))
    val switchMode = ModeSetting("SwitchBlock Mode", "When to switch to a scaffold block", "Biggest", listOf("Normal", "Biggest", "Threshold"))
    val switchThreshold = NumberSetting("Switch Threshold", "Switch when the selected stack reaches this size", 5.0, 0.0, 64.0, 1.0, { switchMode.isMode("Threshold") })

    val fast = BooleanSetting("Fast", "Snap rotation, interact with the block, then snap back", false)
    val rotationMode = ModeSetting(
        "Rotation Mode", "Placement rotation mode", "Normal",
        listOf("Normal", "Smooth", "Hypixel", "Heypixel", "Watchdog", "OnTick", "Facing", "Hit Vec", "Nearest", "Focus", "Human", "Cautious", "Glide", "Pulse", "Mix")
    )
    val mixTurn = ModeSetting("Mix Turn", "Turn-rate curve used by Mix", "Linear", ScaffoldRotationProfiles.turns, { rotationMode.isMode("Mix") })
    val mixPath = ModeSetting("Mix Path", "Face-point trajectory used by Mix", "Center", ScaffoldRotationProfiles.paths, { rotationMode.isMode("Mix") })
    internal fun mixProfileIndex(): Int = if (rotationMode.isMode("Mix")) ScaffoldRotationProfiles.index(mixTurn.value, mixPath.value) else -1
    val shrink = NumberSetting("Shrink", "Face inset for Nearest rotation mode", 0.1, 0.0, 0.45, 0.01, { rotationMode.isMode("Nearest") })
    val rotationSpeed = NumberSetting("Rotation Speed", "Degrees per tick for Nocturne rotation modes", 180.0, 0.0, 180.0, 5.0, { !rotationMode.isMode("Smooth") })
    val smoothRotationSpeed = NumberSetting("Smooth Speed", "Never Smooth speed (1-10)", 10.0, 1.0, 10.0, 1.0, { rotationMode.isMode("Smooth") })
    val rotationNoise = ModeSetting("Rotation Noise", "Bounded rotation variation with face-hit validation", "Off", listOf("Off", "White", "Smooth", "Sine", "Triangle", "Drift", "Micro"))
    val noiseAmount = NumberSetting("Noise Amount", "Maximum yaw/pitch variation in degrees", 0.35, 0.0, 2.0, 0.05, { !rotationNoise.isMode("Off") })
    val noiseInterval = NumberSetting("Noise Interval", "Ticks per noise cycle", 12.0, 2.0, 40.0, 1.0, { !rotationNoise.isMode("Off") })

    internal val profileTunings = ScaffoldRotationProfiles.modes.mapIndexed { index, name ->
        val range = ScaffoldRotationProfiles.tuningRange(index)
        NumberSetting(
            "Profile Tune: $name", ScaffoldRotationProfiles.tuningDescription(index),
            ScaffoldRotationProfiles.defaultTuning(index), range.start, range.endInclusive, 0.01,
            { mixProfileIndex() == index }
        )
    }
    val movementFixMode = ModeSetting("MovementFix Mode", "Position fix after silent rotations", "Silent", listOf("None", "Silent", "Strict"))
    val omniSprint = BooleanSetting("Omni Sprint", "Sprint in any direction via movement-direction yaw", false)

    val placeDelayMode = ModeSetting("Placeable Mode", "Telly placement timing", "Check Height", listOf("Check Height", "Check Tick", "Off"), { mode.isMode("Telly") && !isNeverRotationMode() })
    val height = NumberSetting("Height", "Minimum height above the bridge before placing", 0.5, 0.0, 1.25, 0.05, { mode.isMode("Telly") && !isNeverRotationMode() && placeDelayMode.isMode("Check Height") })
    val upTellyHeight = NumberSetting("UpTelly Height", "Height threshold while jump is held", 0.2, 0.0, 1.25, 0.05, { mode.isMode("Telly") && !isNeverRotationMode() && placeDelayMode.isMode("Check Height") && !keepY.value })
    val tick = NumberSetting("Tick", "Air ticks before placing", 3.0, 0.0, 5.0, 1.0, { mode.isMode("Telly") && !isNeverRotationMode() && placeDelayMode.isMode("Check Tick") })
    val upTellyTick = NumberSetting("UpTelly Tick", "Air ticks before placing while jump is held", 1.0, 0.0, 5.0, 1.0, { mode.isMode("Telly") && !isNeverRotationMode() && placeDelayMode.isMode("Check Tick") && !keepY.value })
    val jumpDelayMin = NumberSetting("Min Jump Delay", "Minimum grounded ticks before jumping in Telly/SameY", 1.0, 0.0, 3.0, 1.0, { mode.isMode("Telly") || mode.isMode("SameY") })
    val jumpDelayMax = NumberSetting("Max Jump Delay", "Maximum grounded ticks before jumping in Telly/SameY", 2.0, 0.0, 3.0, 1.0, { mode.isMode("Telly") || mode.isMode("SameY") })
    val placeDelayMin = NumberSetting("Min Place Delay", "Minimum randomized delay between placements", 0.0, 0.0, 200.0, 1.0, { placementMode.isMode("Direct") })
    val placeDelayMax = NumberSetting("Max Place Delay", "Maximum randomized delay between placements", 50.0, 0.0, 200.0, 1.0, { placementMode.isMode("Direct") })
    val placementMode = ModeSetting("Placement Mode", "Direct uses the existing placement timing; Human schedules repeated right-click attempts", "Direct", listOf("Direct", "Human"))
    val humanPlaceCps = NumberSetting("Human Place CPS", "Right-click attempts per second", 18.0, 1.0, 100.0, 1.0, { placementMode.isMode("Human") })
    val rayCast = ModeSetting("RayCast", "Raycast verification before placing", "Off", listOf("Simple", "Normal", "Medium", "New", "Off", "Hanabi", "Grim", "Strict", "Face", "Reach", "Short Reach", "Previous", "Dual", "Stable", "Permissive", "Visible", "Eye", "Precise", "Tight", "Wide", "Aim Point", "Hybrid"))

    val slow = BooleanSetting("Slow", "Stop sprinting after the first jump", false)
    val keepY = BooleanSetting("Keep Y", "Keep the placement height while jump is held", false)
    val autoSneak = BooleanSetting("Auto Sneak", "Sneak at the edge when the support below is replaceable", false)
    val autoStuck = BooleanSetting("Auto Stuck", "Freeze movement when the Never stuck conditions are met", false)
    val autoStuckMode = ModeSetting("Auto Stuck Mode", "Stuck mode used by Scaffold", "NoPacket", listOf("NoPacket", "CancelMove", "Ticks", "Expo Normal", "Expo Pulse"), { autoStuck.value })
    val sprint = BooleanSetting("Sprint", "Allow sprinting while scaffolding", false)
    val noFov = BooleanSetting("No Fov", "Lock the field of view while sprinting in Telly mode", false, { mode.isMode("Telly") })

    // Keep Nocturne's scaffold trail renderer and theme/custom color controls.
    val renderPlace = BooleanSetting("Render Place", "Draw the placed block trail", true)
    val useThemeColor = BooleanSetting("Use Theme Color", "Use the HUD theme color for the place trail", true)
    val placeColor = ColorSetting(
        "Place Color", "Custom place trail color",
        NanoVGColor(0.0f, 220.0f / 255.0f, 1.0f, 200.0f / 255.0f),
        visibility = { !useThemeColor.value }
    )
    val trailLength = NumberSetting("Trail Length", "Seconds a trail box fades over", 0.65, 0.1, 2.0, 0.05, { renderPlace.value })
    val placeScale = NumberSetting("Place Scale", "Trail box size", 1.0, 0.5, 1.2, 0.01, { renderPlace.value })
    val breath = BooleanSetting("Breath", "Breathing pulse on the trail boxes", true, { renderPlace.value })

    internal val delayTimer = TimerUtil()
    internal val humanClickCadence = ClickCadence()
    private var pendingHumanPlace: BlockPos? = null
    private var pendingHumanPlaceAt = 0L
    internal var canRotation = false
    internal var canPlace = false
    internal var onTickAiming = false
    internal var onTickReadyToPlace = false
    internal var omniSuppressed = false
    internal var omniYaw = 0f
    internal var omniRawForward = 0f
    internal var omniRawStrafe = 0f
    internal var oldSlot = -1
    internal var baseY = 0
    internal var placeY = 0
    internal var offGroundTicks = 0
    internal var firstJumpDone = false
    internal var nextPlaceDelay = 0L
    private var itemSpoofActive = false
    internal var rotations: FloatArray? = null
    internal var data: PlaceInfo? = null
    internal var placementAim: ScaffoldAim? = null
    internal var placedThisTick = false
    internal var lastPlaceInfo: PlaceInfo? = null
    internal var neverLastRotation: FloatArray? = null
    internal var neverCanRotate = false
    internal var neverRotationTick = 0
    internal var neverStage = 0
    internal var neverYawNormal = false
    internal var neverEnabledOnAir = false
    internal var neverStuckTicks = 0
    internal var autoStuckActive = false
    internal var ownsStuckModule = false
    internal var previousStuckMode: String? = null

    internal var currentPlacePos: BlockPos? = null
    internal val placeTrail = HashMap<BlockPos, LongArray>()
    internal var lastRenderTime = 0L
    internal var freeFovModifier = -1f
    internal var fovLockDegrees = -1f
    internal var placedWindow = 0
    internal var rateWindowStart = 0L
    internal var blocksPerSecondValue = 0f
    internal var blocksAtStart = 0
    internal var breathPhase = 0.0

    init {
        profileTunings.forEach(::addSetting)
        listen(Render2DEvent::class.java) { renderTrail() }
        mode.onChanged = {
            canPlace = false
            canRotation = false
            onTickAiming = false
            onTickReadyToPlace = false
            rotations = null
            data = null
            placementAim = null
            lastPlaceInfo = null
            resetNeverRotation()
            resetAutoStuck()
        }
        rotationMode.onChanged = { resetNeverRotation() }
        fast.onChanged = {
            onTickAiming = false
            onTickReadyToPlace = false
            omniSuppressed = false
            pendingHumanPlace = null
            humanClickCadence.reset()
            if (enabled) RotationManager.clearRotationTarget()
        }
        autoStuckMode.onChanged = { selected ->
            if (ownsStuckModule && Stuck.enabled) Stuck.mode.value = selected
        }
        placementMode.onChanged = {
            humanClickCadence.reset()
            delayTimer.reset()
        }
        pickMode.onChanged = { selectedMode ->
            if (enabled) {
                if (selectedMode.equals("Spoof", ignoreCase = true) && oldSlot in 0..8) {
                    ItemSpoofUtil.startSpoof(oldSlot)
                    itemSpoofActive = true
                } else if (itemSpoofActive) {
                    ItemSpoofUtil.stopSpoof()
                    itemSpoofActive = false
                }
            }
        }
    }

    override val suffix: String
        get() = mode.value

    private fun isTellyMode(): Boolean = mode.isMode("Telly")
    private fun fovLockEnabled(): Boolean = enabled && noFov.value && isTellyMode()

    @JvmStatic
    fun isFovLocked(): Boolean {
        if (!fovLockEnabled()) return false
        val player = Minecraft.getInstance().player ?: return false
        return player.isSprinting && !player.isScoping
    }

    @JvmStatic
    fun onFovModifier(vanilla: Float): Float {
        val mc = Minecraft.getInstance()
        if (!fovLockEnabled()) {
            freeFovModifier = -1f
            fovLockDegrees = -1f
            return vanilla
        }
        if (!isFovLocked()) {
            val player = mc.player
            if (player != null && !player.isScoping) freeFovModifier = vanilla
            fovLockDegrees = -1f
            return vanilla
        }
        val base = mc.options.fov().get().toFloat()
        if (base <= 0f) return vanilla
        if (fovLockDegrees <= 0f) fovLockDegrees = base * (if (freeFovModifier > 0f) freeFovModifier else vanilla)
        return Mth.clamp(fovLockDegrees / base, 0.1f, 1.5f)
    }

    fun getMoveFix(): MoveFix = when {
        movementFixMode.isMode("None") -> MoveFix.None
        movementFixMode.isMode("Silent") -> MoveFix.Silent
        else -> MoveFix.Strict
    }

    fun getEffectiveMoveFix(): MoveFix = if (isOmniSprintEnabled()) MoveFix.Silent else getMoveFix()
    internal fun isOmniSprintEnabled(): Boolean = omniSprint.value

    @JvmStatic
    fun shouldOmniSprint(): Boolean = enabled && isOmniSprintEnabled() && !omniSuppressed

    @JvmStatic
    fun shouldOmniSprint(anyImpulse: Boolean): Boolean = shouldOmniSprint() && anyImpulse

    fun isInstantRotation(): Boolean = !fast.value && rotationMode.isMode("OnTick")

    private fun applyOnTickView(rot: FloatArray?) {
        if (rot == null) {
            RotationManager.enabled = false
            return
        }
        RotationManager.setView(rot, getEffectiveMoveFix())
    }

    override fun onEnable() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        ProjectileAura.reset()
        KillAura.reset()
        oldSlot = player.inventory.selectedSlot
        baseY = floor(player.y - 1.0).toInt()
        placeY = baseY
        canPlace = false
        canRotation = false
        onTickAiming = false
        onTickReadyToPlace = false
        omniSuppressed = false
        data = null
        placementAim = null
        lastPlaceInfo = null
        placedThisTick = false
        rotations = null
        offGroundTicks = 0
        resetNeverRotation()
        neverEnabledOnAir = !player.onGround()
        neverStuckTicks = 0
        autoStuckActive = false
        ownsStuckModule = false
        previousStuckMode = null
        firstJumpDone = false
        delayTimer.reset()
        humanClickCadence.reset()
        pendingHumanPlace = null
        nextPlaceDelay = randomPlaceDelay()
        currentPlacePos = null
        placeTrail.clear()
        lastRenderTime = 0L
        breathPhase = 0.0
        freeFovModifier = -1f
        fovLockDegrees = -1f
        placedWindow = 0
        rateWindowStart = System.currentTimeMillis()
        blocksPerSecondValue = 0f
        blocksAtStart = countBlocksInInventory()
        if (pickMode.isMode("Spoof") && oldSlot in 0..8) {
            ItemSpoofUtil.startSpoof(oldSlot)
            itemSpoofActive = true
        } else {
            itemSpoofActive = false
        }
    }

    override fun onDisable() {
        val player = Minecraft.getInstance().player
        if (player != null && oldSlot in 0..8) player.inventory.selectedSlot = oldSlot
        if (itemSpoofActive) ItemSpoofUtil.stopSpoof()
        itemSpoofActive = false
        resetAutoStuck()
        oldSlot = -1
        canPlace = false
        canRotation = false
        onTickAiming = false
        onTickReadyToPlace = false
        omniSuppressed = false
        omniRawForward = 0f
        omniRawStrafe = 0f
        data = null
        placementAim = null
        lastPlaceInfo = null
        placedThisTick = false
        rotations = null
        offGroundTicks = 0
        resetNeverRotation()
        firstJumpDone = false
        humanClickCadence.reset()
        pendingHumanPlace = null
        currentPlacePos = null
        placeTrail.clear()
        lastRenderTime = 0L
        breathPhase = 0.0
        freeFovModifier = -1f
        fovLockDegrees = -1f
    }

    @JvmStatic
    fun onWorldChange() {
        if (enabled) toggle()
    }

    @JvmStatic
    fun onUpdate(player: LocalPlayer) {
        if (!enabled) return
        confirmHumanPlacement()
        if (oldSlot !in 0..8) {
            oldSlot = player.inventory.selectedSlot
            if (pickMode.isMode("Spoof") && oldSlot in 0..8) {
                ItemSpoofUtil.startSpoof(oldSlot)
                itemSpoofActive = true
            }
        }
        placedThisTick = false
        canPlace = false
        canRotation = false
        placementAim = null
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.gameMode == null || player.isSpectator || !player.isAlive) return

        updateSprintState(player)
        updateBlockSelection(player)
        if (!enabled) return

        if (player.onGround()) {
            offGroundTicks = 0
        } else if (shouldBuild(player)) {
            offGroundTicks++
        }

        updateNeverRotationState(player)

        placeY = getPlaceY(player)
        if (!passesTellyPlacementGate(player)) {
            data = null
            rotations = null
            return
        }

        val origin = BlockPos(floor(player.x).toInt(), placeY, floor(player.z).toInt())
        var placeInfo = getBlockData(player, origin)
        if (placeInfo == null) placeInfo = lastPlaceInfo
        if (placeInfo == null) {
            data = null
            rotations = null
            return
        }

        lastPlaceInfo = placeInfo
        data = placeInfo
        val usesNeverRotation = rotationMode.isMode("Hypixel") || rotationMode.isMode("Heypixel") || rotationMode.isMode("Watchdog")
        val refRotation = if (usesNeverRotation) {
            neverLastRotation?.let { Rotation(it[0], it[1]) } ?: RotationUtil.getRotationOrElseMC()
        } else {
            RotationManager.serverRotation ?: RotationUtil.getRotationOrElseMC()
        }
        val aim = getPlacementAim(player, placeInfo, refRotation)
        placementAim = aim
        if (aim == null) {
            rotations = null
            return
        }
        rotations = if (usesNeverRotation && !fast.value) {
            getNeverRotation(player, aim.rotation)
        } else {
            aim.rotation
        }
        canPlace = true
        canRotation = true

        val airPos = placeInfo.blockPos.relative(placeInfo.facing)
        val now = System.currentTimeMillis()
        val times = placeTrail[airPos]
        if (times == null) placeTrail[airPos] = longArrayOf(now, now) else times[1] = now
        currentPlacePos = airPos

        if (isInstantRotation()) {
            onTickReadyToPlace = false
            if (canPlace && (placementMode.isMode("Human") || delayTimer.hasTimeElapsed(nextPlaceDelay))) {
                if (onTickAiming) {
                    onTickReadyToPlace = true
                    canRotation = true
                    applyOnTickView(rotations)
                } else {
                    onTickAiming = true
                    applyOnTickView(rotations)
                }
            } else {
                onTickAiming = false
                val view = if (isOmniMoving()) floatArrayOf(omniYaw, player.xRot) else null
                rotations = view
                canRotation = view != null
                applyOnTickView(view)
            }
        }

        onEarlyPlace(player)
        if (isInstantRotation() && onTickAiming && onTickReadyToPlace) {
            onTickAiming = false
            onTickReadyToPlace = false
            val view = if (isOmniMoving()) floatArrayOf(omniYaw, player.xRot) else null
            rotations = view
            canRotation = view != null
            applyOnTickView(view)
        }
    }

    private fun updateSprintState(player: LocalPlayer) {
        if (slow.value) {
            if (!firstJumpDone && !player.onGround()) firstJumpDone = true
            if (firstJumpDone) player.isSprinting = false
        } else {
            val mc = Minecraft.getInstance()
            player.isSprinting = mc.options.keySprint.isDown && sprint.value
        }
    }

    private fun updateBlockSelection(player: LocalPlayer) {
        if (pickMode.isMode("Off") || isValidBlock(player.offhandItem)) return
        val inventory = player.inventory
        val currentSlot = inventory.selectedSlot
        val current = inventory.getItem(currentSlot)
        val currentValid = isValidBlock(current)
        val targetSlot = getBlockSlot()
        val shouldSwitch = when {
            switchMode.isMode("Threshold") -> !currentValid || current.count <= switchThreshold.getInt()
            switchMode.isMode("Biggest") -> !currentValid || (targetSlot != -1 && targetSlot != currentSlot)
            else -> !currentValid
        }
        if (!shouldSwitch) return
        if (targetSlot == -1) {
            toggle()
            return
        }
        if (targetSlot != currentSlot) inventory.selectedSlot = targetSlot
    }

    private fun passesTellyPlacementGate(player: LocalPlayer): Boolean {
        if (!mode.isMode("Telly") || isNeverRotationMode()) return true
        val jumping = Minecraft.getInstance().options.keyJump.isDown && !keepY.value
        return when (placeDelayMode.value) {
            "Check Height" -> !(player.fallDistance == 0.0 && player.y < placeY + 1.0 + (if (jumping) upTellyHeight.value else height.value) && player.hurtTime == 0)
            "Check Tick" -> offGroundTicks >= (if (jumping) upTellyTick.getInt() else tick.getInt())
            else -> true
        }
    }

    internal fun getPlaceY(player: LocalPlayer): Int {
        if (Minecraft.getInstance().options.keyJump.isDown && !keepY.value) {
            baseY = floor(player.y - 1.0).toInt()
        } else if (baseY > player.y - 1.0 || player.onGround()) {
            baseY = floor(player.y - 1.0).toInt()
        }
        return baseY
    }

    private fun shouldBuild(player: LocalPlayer): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val pos = BlockPos(floor(player.x).toInt(), floor(player.y - 0.5).toInt(), floor(player.z).toInt())
        return level.getBlockState(pos).isAir
    }

    private fun randomPlaceDelay(): Long {
        val min = minOf(placeDelayMin.getInt(), placeDelayMax.getInt())
        val max = maxOf(placeDelayMin.getInt(), placeDelayMax.getInt())
        return if (min == max) min.toLong() else Random.nextInt(min, max + 1).toLong()
    }

    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) {
        if (!enabled || isInstantRotation()) return
        if (isNeverRotationMode()) return
        val placeInfo = data ?: return
        val reference = RotationManager.currentRotation ?: RotationManager.serverRotation ?: RotationUtil.getRotationOrElseMC()
        val aim = getPlacementAim(player, placeInfo, reference)
        placementAim = aim
        rotations = aim?.rotation
        canRotation = aim != null && canPlace
    }

    @JvmStatic
    fun onMoveInput(player: LocalPlayer, event: MoveInputEvent) = onMoveInputImpl(player, event)

    @JvmStatic
    fun onEarlyPlace(player: LocalPlayer) {
        if (!enabled) return
        if (placementMode.isMode("Human")) return
        if (fast.value) RotationManager.clearRotationTarget()
        val packetRotation = peekPlaceRotation(player)
        placedThisTick = tryPlace(player, player.xOld, player.yOld, player.zOld, packetRotation[0], packetRotation[1])
    }

    @JvmStatic
    fun queueHumanClicks() {
        if (!enabled || !placementMode.isMode("Human")) return
        val clicks = humanClickCadence.attempts(System.currentTimeMillis(), humanPlaceCps.value)
        if (clicks <= 0 || !canPlace) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val info = data ?: return
        val level = mc.level ?: return
        if (mc.gameMode == null || mc.gui.screen() != null || player.isSpectator || !player.isAlive) return
        if (!isValidBlock(player.mainHandItem) && !isValidBlock(player.offhandItem)) return
        val destination = info.blockPos.relative(info.facing)
        if (!level.getBlockState(destination).canBeReplaced()) return
        val aim = placementAim ?: return
        val view = if (fast.value) {
            val snap = RotationUtil.getRotations(player.eyePosition, aim.hitVec)
            Rotation(snap[0], snap[1])
        } else RotationManager.currentRotation ?: Rotation(aim.rotation[0], aim.rotation[1])
        if (!passesRaycast(info.blockPos, info.facing, player.xOld, player.yOld, player.zOld, view.yaw, view.pitch)) return
        val hit = BlockHitResult(if (fast.value) aim.hitVec else getVec(info.blockPos, info.facing), info.facing, info.blockPos, false)
        pendingHumanPlace = destination
        pendingHumanPlaceAt = System.currentTimeMillis()
        VanillaClickQueue.overrideHit(mc, hit)
        VanillaClickQueue.right(mc, clicks)
    }

    private fun confirmHumanPlacement() {
        val pos = pendingHumanPlace ?: return
        val level = Minecraft.getInstance().level ?: return
        if (!level.getBlockState(pos).canBeReplaced()) {
            onPlaced(pos)
            pendingHumanPlace = null
        } else if (System.currentTimeMillis() - pendingHumanPlaceAt > 500L) {
            pendingHumanPlace = null
        }
    }

    private fun peekPlaceRotation(player: LocalPlayer): FloatArray {
        val base = RotationManager.peekPacketRotation(player)
        if (!isOmniSprintEnabled() || rotationMode.isMode("OnTick")) return base
        val forward = player.input.moveVector.y
        val strafe = player.input.moveVector.x
        if (forward == 0f && strafe == 0f) return base
        val omni = Math.toDegrees(MovementUtil.getDirection(player.yRot, forward, strafe)).toFloat()
        val aimYaw = rotations?.get(0) ?: player.yRot
        return if (abs(Mth.wrapDegrees(omni - aimYaw)) <= 67.5f) floatArrayOf(omni, rotations?.get(1) ?: player.xRot) else base
    }

    @JvmStatic
    fun countBlocksInInventory(): Int = BlockUtil.getBlockCount()

    @JvmStatic
    fun blocksLeft(): Int = countBlocksInInventory()

    @JvmStatic
    fun placementRate(): Float {
        val now = System.currentTimeMillis()
        val elapsed = now - rateWindowStart
        if (elapsed >= 1000L) {
            blocksPerSecondValue = if (elapsed > 0) placedWindow * 1000f / elapsed else 0f
            placedWindow = 0
            rateWindowStart = now
        }
        return blocksPerSecondValue
    }

    @JvmStatic
    fun placedFraction(): Float {
        if (blocksAtStart <= 0) return 0f
        return ((blocksAtStart - countBlocksInInventory()) / blocksAtStart.toFloat()).coerceIn(0f, 1f)
    }

    @JvmStatic
    fun blocksRemainingFraction(): Float {
        if (blocksAtStart <= 0) return 0f
        return (countBlocksInInventory() / blocksAtStart.toFloat()).coerceIn(0f, 1f)
    }

    @JvmStatic
    fun onPostPlace(player: LocalPlayer): Boolean {
        if (!enabled) {
            placedThisTick = false
            return false
        }
        val placed = placedThisTick
        placedThisTick = false
        return placed
    }

    @JvmStatic
    fun onVisualTick(player: LocalPlayer) {
        if (enabled) RotationManager.updateRenderAngles(player)
    }

    internal fun onPlaced(pos: BlockPos) {
        val now = System.currentTimeMillis()
        placeTrail[pos] = longArrayOf(now, now)
        currentPlacePos = pos
        delayTimer.reset()
        nextPlaceDelay = randomPlaceDelay()
        placedWindow++
    }

    fun getYLevel(player: LocalPlayer? = Minecraft.getInstance().player): Double = getYLevelImpl(player)
    fun getBlockData(player: LocalPlayer?, belowBlockPos: BlockPos): PlaceInfo? = getBlockDataImpl(player, belowBlockPos)
    fun getVec(pos: BlockPos, facing: Direction): Vec3 = getVecImpl(pos, facing)
    fun getRotationSpeed(player: LocalPlayer? = Minecraft.getInstance().player): Int = getRotationSpeedImpl(player)

    internal fun getBlockSlot(): Int = getBlockSlotImpl()
    internal fun isValidBlock(stack: net.minecraft.world.item.ItemStack): Boolean = isValidBlockStack(stack)
    internal fun isOmniMoving(): Boolean = isOmniMovingImpl()

    internal fun isNeverRotationMode(): Boolean =
        rotationMode.isMode("Hypixel") || rotationMode.isMode("Heypixel") || rotationMode.isMode("Watchdog")

    private fun resetNeverRotation() {
        neverLastRotation = null
        neverCanRotate = false
        neverRotationTick = 0
        neverStage = 0
        neverYawNormal = false
        neverEnabledOnAir = false
    }

    internal fun resetAutoStuck() {
        autoStuckActive = false
        if (ownsStuckModule && Stuck.enabled) Stuck.enabled = false
        previousStuckMode?.let { Stuck.mode.value = it }
        previousStuckMode = null
        ownsStuckModule = false
        neverStuckTicks = 0
    }

    data class PlaceInfo(val blockPos: BlockPos, val facing: Direction)
}

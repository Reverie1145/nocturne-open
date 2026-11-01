package cn.reverie.render.hud.impl

import cn.reverie.module.BooleanSetting
import cn.reverie.module.ModeSetting
import cn.reverie.module.NumberSetting
import cn.reverie.app.music.CloudMusicManager
import cn.reverie.app.music.player.CloudMusicPlayer
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HudElement
import cn.reverie.render.hud.HudHorizontalAnchor
import cn.reverie.render.hud.HudVerticalAnchor
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.util.render.Render3DUtil
import cn.reverie.util.rotation.RotationUtil
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/**
 * Lyric HUD Widget with Vanilla Minecraft In-World 3D Rendering.
 *
 * Modes:
 * - 2D Screen HUD Mode: Classic fixed 2D HUD text with edge-fade blur.
 * - 3D In-World Paper Mode:
 *   * Native Minecraft in-world feature rendering (LevelRenderer submitFeatures -> submitText).
 *   * Configurable spawn distance in blocks ahead of the player.
 *   * Pure floating text with ABSOLUTELY NO background card, box, or border.
 *   * Faces the player ONLY when spawned; afterwards its world orientation is fixed like a 2D sheet of paper.
 *   * Size remains physical in the 3D Minecraft world without artificial ballooning.
 *   * Optical focus-pull bokeh blur entrance animation & dissolve blur exit.
 *   * Gentle animated paper fluttering in the breeze.
 */
class LyricHUDWidget : HudElement(
    name = "Lyric HUD",
    defaultX = 260f,
    defaultY = 360f,
    width = 380f,
    height = 68f,
    defaultEnabled = true,
    horizontalAnchor = HudHorizontalAnchor.CENTER,
    verticalAnchor = HudVerticalAnchor.BOTTOM
) {

    val mode = ModeSetting("Mode", "HUD mode: 2D fixed HUD or 3D in-world paper object", "3D", listOf("2D", "3D"))

    // 3D In-World Settings
    val distance3D = NumberSetting("Spawn Distance", "Distance in blocks ahead where lyrics spawn", 4.5, 1.0, 15.0, 0.5) { mode.isCurrent("3D") }
    val height3D = NumberSetting("World Height", "Vertical height offset in world space", 0.0, -1.5, 2.5, 0.1) { mode.isCurrent("3D") }
    val lingerTime = NumberSetting("Linger Duration", "How long past lines remain in world (sec)", 3.5, 1.0, 10.0, 0.5) { mode.isCurrent("3D") }
    val spawnBlurAmount = NumberSetting("Spawn Blur", "Initial blur when materializing in world", 7.0, 1.0, 12.0, 0.5) { mode.isCurrent("3D") }
    val dynamicMotionBlur = BooleanSetting("Motion Blur", "Apply dynamic blur when moving quickly", true) { mode.isCurrent("3D") }
    val floatingBreathing = BooleanSetting("Floating Sway", "Organic 2D paper fluttering in the world", true) { mode.isCurrent("3D") }
    val avoidObstacles = BooleanSetting("Block Raycast", "Raycast to avoid spawning inside blocks", true) { mode.isCurrent("3D") }
    val smoothSpeed = NumberSetting("Follow Smoothness", "View tracking responsiveness", 0.14, 0.03, 0.40, 0.01) { mode.isCurrent("3D") }
    val dynamicTilt = BooleanSetting("Dynamic Tilt", "3D banking/tilt when moving & turning", true) { mode.isCurrent("3D") }
    val depthSpacing3D = NumberSetting("3D Depth Spacing", "Depth spacing between lines", 0.35, 0.0, 1.0, 0.05) { mode.isCurrent("3D") }
    val seeThrough = BooleanSetting("See Through", "Render through walls/blocks", false) { mode.isCurrent("3D") }
    val doubleSided = BooleanSetting("Double Sided", "Make text readable from both front and back", true) { mode.isCurrent("3D") }
    val sprintPacing = BooleanSetting("Sprint Pacing", "Auto compute spawn distance so sprinting reaches it at next lyric", true) { mode.isCurrent("3D") }
    val textGlow = BooleanSetting("Text Glow", "Render luminous bloom halo around lyrics to make them eye-catching", true) { mode.isCurrent("3D") }
    val glowColorMode = ModeSetting("Glow Color", "Theme color for luminous highlight aura", "Cyan", listOf("Cyan", "White", "Purple", "Gold", "Pink")) { mode.isCurrent("3D") && textGlow.value }
    val textScale3D = NumberSetting("World Text Scale", "Size multiplier for in-world 3D lyrics", 1.55, 0.5, 3.5, 0.05) { mode.isCurrent("3D") }

    // Shared & 2D Settings
    val maxBlur = NumberSetting("Edge Blur", "Dissolve blur on expiring lines", 5.5, 0.0, 10.0, 0.5)
    val lineSpacing = NumberSetting("Line Spacing", "Vertical spacing between lyric lines", 23.0, 16.0, 36.0, 1.0)
    val showTranslation = BooleanSetting("Translation", "Display translated subtitle under active line", false)
    val autoHide = BooleanSetting("Auto Hide", "Hide when no music or lyrics are playing", true)

    // In-World 2D Paper Object Data
    private data class WorldLyric(
        val lineIndex: Int,
        val text: String,
        val translation: String,
        var baseWorldPos: Vec3,
        val spawnYaw: Float,
        val spawnPitch: Float,
        val spawnTimeMs: Long,
        val seed: Double = Math.random() * 100.0,
        var isExpired: Boolean = false,
        var expireTimeMs: Long = 0L
    )

    private val worldLyrics = mutableListOf<WorldLyric>()
    private var lastObservedIdx: Int = -1
    private var lastObservedTrackId: Long = -1L

    // 2D & Tracking States
    private var animatedScroll: Float = 0f
    private var prevCamYaw: Float = 0f
    private var prevCamPitch: Float = 0f
    private var motionBlurTracker: Float = 0f

    init {
        instance = this
        addSetting(mode)
        addSetting(distance3D)
        addSetting(height3D)
        addSetting(lingerTime)
        addSetting(spawnBlurAmount)
        addSetting(dynamicMotionBlur)
        addSetting(floatingBreathing)
        addSetting(avoidObstacles)
        addSetting(smoothSpeed)
        addSetting(dynamicTilt)
        addSetting(depthSpacing3D)
        addSetting(seeThrough)
        addSetting(doubleSided)
        addSetting(sprintPacing)
        addSetting(textGlow)
        addSetting(glowColorMode)
        addSetting(textScale3D)
        addSetting(maxBlur)
        addSetting(lineSpacing)
        addSetting(showTranslation)
        addSetting(autoHide)
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        val player = CloudMusicManager.player
        val isPlaying = player.playbackState == CloudMusicPlayer.PlaybackState.PLAYING
        val lyricData = player.currentLyric
        val lines = lyricData.lines
        val hasLiveLyrics = isPlaying && lines.isNotEmpty()

        if (mode.isCurrent("3D")) {
            // In 3D mode, lyrics exist as in-world objects; suppress all 2D screen HUD widgets/controls
            return
        }

        if (!isEditing && !hasLiveLyrics && autoHide.value) {
            return
        }

        render2DMode(partialTicks, isEditing, hasLiveLyrics, player, lyricData)
    }

    /**
     * Minecraft Native In-World Level Rendering:
     * Injected directly into LevelRenderer.submitFeatures -> SubmitNodeCollector.submitText.
     */
    private fun renderInWorldInternal(levelRenderState: LevelRenderState, submitNodeCollector: SubmitNodeCollector) {
        val mc = Minecraft.getInstance()
        val clientPlayer = mc.player ?: return
        val level = mc.level ?: return
        val cameraState = levelRenderState.cameraRenderState ?: return
        val cameraPos = cameraState.pos ?: return
        val font = mc.font ?: return
        val now = System.currentTimeMillis()

        // 1. Motion & rotation tracking for dynamic motion blur
        val deltaMovement = clientPlayer.deltaMovement
        val horizSpeed = sqrt(deltaMovement.x * deltaMovement.x + deltaMovement.z * deltaMovement.z).toFloat()
        val vertSpeed = abs(deltaMovement.y).toFloat()

        val camYaw = cameraState.yRot
        val camPitch = cameraState.xRot
        val deltaYaw = abs(Mth.wrapDegrees(camYaw - prevCamYaw))
        val deltaPitch = abs(camPitch - prevCamPitch)
        prevCamYaw = camYaw
        prevCamPitch = camPitch

        val rawMotion = (horizSpeed * 4.0f + vertSpeed * 1.8f + deltaYaw * 0.08f + deltaPitch * 0.08f).coerceIn(0f, 4.0f)
        motionBlurTracker += (rawMotion - motionBlurTracker) * 0.20f

        val lingerMs = (lingerTime.getFloat() * 1000f).toLong().coerceAtLeast(500L)

        // 2. Manage in-world lyric spawning
        val musicPlayer = CloudMusicManager.player
        val lyricData = musicPlayer.currentLyric
        val isPlaying = musicPlayer.playbackState == CloudMusicPlayer.PlaybackState.PLAYING
        val hasLiveLyrics = isPlaying && lyricData.lines.isNotEmpty()

        if (hasLiveLyrics) {
            val activeIdx = lyricData.findLineIndex(musicPlayer.currentPositionMs)
            val currentTrackId = musicPlayer.currentTrack?.id ?: 0L

            if (currentTrackId != lastObservedTrackId) {
                worldLyrics.clear()
                lastObservedTrackId = currentTrackId
                lastObservedIdx = -1
            }

            if (activeIdx != lastObservedIdx && activeIdx in lyricData.lines.indices) {
                val line = lyricData.lines[activeIdx]
                if (line.text.isNotBlank()) {
                    // Expire previous active lyrics
                    for (item in worldLyrics) {
                        if (!item.isExpired) {
                            item.isExpired = true
                            item.expireTimeMs = now
                        }
                    }

                    // Spawn position calculation: ahead of player's moving trajectory & view
                    val eyePos = clientPlayer.eyePosition
                    val lookVec = RotationUtil.getVectorForRotation(camYaw, camPitch.coerceIn(-25f, 25f)).normalize()
                    val isMoving = horizSpeed > 0.05f
                    val moveDir = if (isMoving) Vec3(deltaMovement.x, 0.0, deltaMovement.z).normalize() else lookVec
                    val spawnDir = moveDir.scale(0.55).add(lookVec.scale(0.45)).normalize()

                    // Auto compute distance by sprint pacing: player sprinting forward arrives right at the next line
                    val currentPosMs = musicPlayer.currentPositionMs
                    val nextLine = lyricData.lines.getOrNull(activeIdx + 1)
                    val timeUntilNextSec = if (nextLine != null && nextLine.timeMs > currentPosMs) {
                        (nextLine.timeMs - currentPosMs) / 1000.0
                    } else {
                        4.0
                    }.coerceIn(1.8, 8.5)

                    val actualSpeedBlocksPerSec = horizSpeed * 20.0
                    val sprintSpeed = if (clientPlayer.isSprinting) {
                        max(actualSpeedBlocksPerSec.toDouble(), 5.6)
                    } else if (isMoving) {
                        max(actualSpeedBlocksPerSec.toDouble(), 4.5)
                    } else {
                        5.6 // Anticipate player sprinting forward on beat
                    }

                    val desiredDist = if (sprintPacing.value) {
                        (sprintSpeed * timeUntilNextSec).coerceIn(3.0, 32.0)
                    } else {
                        distance3D.getDouble().coerceIn(1.0, 20.0)
                    }

                    var targetDist = desiredDist
                    if (avoidObstacles.value) {
                        val rayEnd = eyePos.add(spawnDir.scale(desiredDist + 0.8))
                        val hit = level.clip(
                            ClipContext(eyePos, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, clientPlayer)
                        )
                        if (hit.type != HitResult.Type.MISS) {
                            targetDist = (hit.location.distanceTo(eyePos) - 0.7).coerceAtLeast(1.0)
                        }
                    }

                    var spawnPos = eyePos.add(spawnDir.scale(targetDist)).add(0.0, height3D.getDouble(), 0.0)

                    // Ground clearance check
                    val groundHit = level.clip(
                        ClipContext(
                            spawnPos.add(0.0, 1.5, 0.0),
                            spawnPos.subtract(0.0, 4.0, 0.0),
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            clientPlayer
                        )
                    )
                    if (groundHit.type != HitResult.Type.MISS) {
                        val floorY = groundHit.location.y
                        spawnPos = Vec3(spawnPos.x, max(spawnPos.y, floorY + 1.2), spawnPos.z)
                    }

                    // Stagger if player is standing still
                    val prevActive = worldLyrics.lastOrNull { it.isExpired }
                    if (prevActive != null && prevActive.baseWorldPos.distanceTo(spawnPos) < 1.4) {
                        spawnPos = spawnPos.add(lookVec.scale(0.35))
                    }

                    // World facing: faces player ONLY at spawn time (spawnYaw is fixed afterwards)
                    worldLyrics.add(
                        WorldLyric(
                            lineIndex = activeIdx,
                            text = line.text,
                            translation = line.translation,
                            baseWorldPos = spawnPos,
                            spawnYaw = camYaw,
                            spawnPitch = camPitch,
                            spawnTimeMs = now
                        )
                    )
                }
                lastObservedIdx = activeIdx
            }
        } else if (!autoHide.value) {
            // Idle demo preview when auto-hide is off
            if (worldLyrics.isEmpty() || lastObservedTrackId != -999L) {
                worldLyrics.clear()
                lastObservedTrackId = -999L
                lastObservedIdx = 1

                val eyePos = clientPlayer.eyePosition
                val lookVec = RotationUtil.getVectorForRotation(camYaw, camPitch.coerceIn(-15f, 15f)).normalize()
                val dist = distance3D.getDouble().coerceIn(2.0, 10.0)

                val demo1Pos = eyePos.add(lookVec.scale(dist)).add(0.0, height3D.getDouble(), 0.0)
                val demo0Pos = eyePos.add(lookVec.scale(dist + 2.2)).add(0.0, height3D.getDouble() + 0.65, 0.0)
                val demo2Pos = eyePos.add(lookVec.scale(max(1.8, dist - 1.8))).add(0.0, height3D.getDouble() - 0.55, 0.0)

                worldLyrics.add(
                    WorldLyric(0, "Stars sink into the quiet night", "", demo0Pos, camYaw, camPitch, now - 3500L, isExpired = true, expireTimeMs = now - 1200L)
                )
                worldLyrics.add(
                    WorldLyric(1, "Chasing the eternal shadows and starlight", "追寻着永恒的暗影与星辉", demo1Pos, camYaw, camPitch, now - 800L)
                )
                worldLyrics.add(
                    WorldLyric(2, "Listening to the prelude of dreams", "", demo2Pos, camYaw, camPitch, now - 200L)
                )
            }
        } else {
            worldLyrics.clear()
        }

        // Clean up expired lyrics
        worldLyrics.removeIf { it.isExpired && (now - it.expireTimeMs) > lingerMs }
        worldLyrics.removeIf { it.baseWorldPos.distanceTo(cameraPos) > 60.0 }

        if (worldLyrics.isEmpty()) return

        val displayMode = if (seeThrough.value) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
        val lightCoords = 15728880 // 0x00F000F0 (Full Brightness)
        val baseScale = 0.025f * widgetScale.getFloat() * textScale3D.getFloat()

        val glowRgb = when (glowColorMode.value.lowercase()) {
            "cyan" -> 0x38BDF8
            "white" -> 0xF8FAFC
            "purple" -> 0xC084FC
            "gold" -> 0xFBBF24
            "pink" -> 0xF472B6
            else -> 0x38BDF8
        }

        // 3. Render each in-world 2D paper lyric via Minecraft's native submitText
        for (lyric in worldLyrics) {
            val ageSec = (now - lyric.spawnTimeMs) / 1000.0

            // Floating breeze animation (动的2D纸片)
            val hoverY = if (floatingBreathing.value) sin(ageSec * 1.8 + lyric.seed) * 0.035 else 0.0
            val hoverX = if (floatingBreathing.value) cos(ageSec * 1.3 + lyric.seed) * 0.02 else 0.0
            val flutterTilt = if (floatingBreathing.value) (sin(ageSec * 2.2 + lyric.seed) * 2.2).toFloat() else 0f

            // Dissolve rise & fade on expiration
            val expireElapsed = if (lyric.isExpired) (now - lyric.expireTimeMs).coerceAtLeast(0L) else 0L
            val dissolveProgress = if (lyric.isExpired) (expireElapsed.toFloat() / lingerMs.toFloat()).coerceIn(0f, 1f) else 0f
            val dissolveRise = dissolveProgress * 0.35
            val dissolveAlpha = if (lyric.isExpired) (1f - dissolveProgress).pow(1.5f) else 1f
            val dissolveBlur = if (lyric.isExpired) dissolveProgress * maxBlur.getFloat() else 0f

            // Spawn materialization blur & rise animation: 0ms -> 450ms
            val spawnElapsed = (now - lyric.spawnTimeMs).coerceAtLeast(0L)
            val spawnProgress = (spawnElapsed / 450f).coerceIn(0f, 1f)
            val spawnEase = 1f - (1f - spawnProgress).pow(3)
            val spawnBlur = (1f - spawnEase) * spawnBlurAmount.getFloat()
            val spawnAlpha = spawnEase
            val spawnRise = (1f - spawnEase) * -0.15

            val moveBlur = if (dynamicMotionBlur.value) motionBlurTracker else 0f
            val totalBlur = (spawnBlur + dissolveBlur + moveBlur).coerceIn(0f, 14.0f)
            val totalAlpha = (spawnAlpha * dissolveAlpha).coerceIn(0f, 1f)
            if (totalAlpha <= 0.01f) continue

            val currentWorldPos = lyric.baseWorldPos.add(hoverX, hoverY + dissolveRise + spawnRise, 0.0)
            val relX = currentWorldPos.x - cameraPos.x
            val relY = currentWorldPos.y - cameraPos.y
            val relZ = currentWorldPos.z - cameraPos.z

            val distSq = relX * relX + relY * relY + relZ * relZ
            if (distSq > 3600.0 || distSq < 0.12) continue

            // Setup in-world PoseStack:
            // Lock yaw at spawnYaw (faces player ONLY when spawned; fixed in world afterwards)
            val poseStack = PoseStack()
            poseStack.pushPose()
            poseStack.translate(relX, relY, relZ)
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - lyric.spawnYaw))
            if (dynamicTilt.value && flutterTilt != 0f) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(flutterTilt))
            }
            poseStack.scale(baseScale, -baseScale, baseScale)

            val isCurrentActive = !lyric.isExpired
            val mainText = lyric.text
            val mainWidth = font.width(mainText)
            val mainFormatted = Component.literal(mainText).visualOrderText
            val hasTrans = showTranslation.value && lyric.translation.isNotBlank()
            val mainY = if (hasTrans) -6f else -4.5f
            val mainRgb = if (isCurrentActive) 0xFFFFFF else 0xCFD8DC

            // Front face: native Minecraft submitText with luminous bloom halo & multi-sample optical blur
            submitBlurredText(
                submitNodeCollector, poseStack, -mainWidth / 2f, mainY,
                mainFormatted, totalBlur, totalAlpha, mainRgb,
                displayMode, lightCoords, isGlow = isCurrentActive, glowRgb = glowRgb
            )

            // Subtitle translation under main line
            if (hasTrans) {
                val transText = lyric.translation
                val transWidth = font.width(transText)
                val transFormatted = Component.literal(transText).visualOrderText
                val transRgb = 0xB2EBF2
                submitBlurredText(
                    submitNodeCollector, poseStack, -transWidth / 2f, mainY + 11.5f,
                    transFormatted, totalBlur * 0.7f, totalAlpha * 0.85f, transRgb,
                    displayMode, lightCoords, isGlow = false, glowRgb = glowRgb
                )
            }

            // Back face: if double-sided is enabled, rotate 180 deg so text is readable from the back
            if (doubleSided.value) {
                poseStack.pushPose()
                poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                submitBlurredText(
                    submitNodeCollector, poseStack, -mainWidth / 2f, mainY,
                    mainFormatted, totalBlur, totalAlpha, mainRgb,
                    displayMode, lightCoords, isGlow = isCurrentActive, glowRgb = glowRgb
                )
                if (hasTrans) {
                    val transText = lyric.translation
                    val transWidth = font.width(transText)
                    val transFormatted = Component.literal(transText).visualOrderText
                    val transRgb = 0xB2EBF2
                    submitBlurredText(
                        submitNodeCollector, poseStack, -transWidth / 2f, mainY + 11.5f,
                        transFormatted, totalBlur * 0.7f, totalAlpha * 0.85f, transRgb,
                        displayMode, lightCoords, isGlow = false, glowRgb = glowRgb
                    )
                }
                poseStack.popPose()
            }

            poseStack.popPose()
        }
    }

    /**
     * Submits native Minecraft in-world text with optical Gaussian/bokeh blur and radiant luminous bloom halo.
     * When blur <= 0.08f, renders a sharp highlighted text with luminous halo.
     * When blur > 0.08f, renders radial jittered bokeh samples with Gaussian weighting and glowing tint.
     * Absolutely NO background card/box (backgroundColor = 0).
     */
    private fun submitBlurredText(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        x: Float,
        y: Float,
        text: FormattedCharSequence,
        blur: Float,
        alpha: Float,
        rgb: Int,
        displayMode: Font.DisplayMode,
        lightCoords: Int,
        isGlow: Boolean = false,
        glowRgb: Int = 0x38BDF8
    ) {
        if (blur <= 0.08f) {
            // High-contrast dark shadow backing to ensure crisp readability against bright skies
            // 1. High-contrast 8-direction dark outline backing (超醒目深色8向轮廓底衬，保证在任意极亮背景下字迹如雕刻般清晰)
            if (textShadow) {
                val shadowAlpha = (alpha * 0.85f * 255f).toInt().coerceIn(1, 255)
                val shadowColor = (shadowAlpha shl 24) or 0x000000
                val so = 0.85f
                val soDiag = 0.60f
                val shadowOffsets = arrayOf(
                    Pair(-so, 0f), Pair(so, 0f), Pair(0f, -so), Pair(0f, so),
                    Pair(-soDiag, -soDiag), Pair(soDiag, -soDiag), Pair(-soDiag, soDiag), Pair(soDiag, soDiag),
                    Pair(so * 1.15f, so * 1.15f)
                )
                for ((sx, sy) in shadowOffsets) {
                    collector.submitText(
                        poseStack, x + sx, y + sy, text,
                        false, displayMode, lightCoords, shadowColor, 0, 0
                    )
                }
            }

            // 2. Radiant 8-direction luminous bloom aura (8向绚丽发光光晕)
            if (isGlow && textGlow.value && alpha > 0.08f) {
                val haloAlpha = (alpha * 0.55f * 255f).toInt().coerceIn(1, 255)
                val haloColor = (haloAlpha shl 24) or (glowRgb and 0x00FFFFFF)
                val hr = 0.95f
                val hrDiag = 0.67f
                val haloOffsets = arrayOf(
                    Pair(-hr, 0f), Pair(hr, 0f), Pair(0f, -hr), Pair(0f, hr),
                    Pair(-hrDiag, -hrDiag), Pair(hrDiag, -hrDiag), Pair(-hrDiag, hrDiag), Pair(hrDiag, hrDiag)
                )
                for ((hx, hy) in haloOffsets) {
                    collector.submitText(
                        poseStack, x + hx, y + hy, text,
                        false, displayMode, lightCoords, haloColor, 0, 0
                    )
                }
            }

            // 3. Primary sharp brilliant core text (核心纯白超亮文字)
            val a = (alpha * 255f).toInt().coerceIn(0, 255)
            val color = (a shl 24) or (rgb and 0x00FFFFFF)
            collector.submitText(
                poseStack, x, y, text,
                false, displayMode, lightCoords, color, 0, 0
            )
        } else {
            val r = blur * 0.40f
            val centerWeight = (1.0f / (1.0f + blur * 0.40f)).coerceIn(0.25f, 0.65f)
            val centerAlpha = (alpha * centerWeight * 255f).toInt().coerceIn(0, 255)
            val centerColor = (centerAlpha shl 24) or (rgb and 0x00FFFFFF)

            // Center sample
            collector.submitText(
                poseStack, x, y, text,
                false, displayMode, lightCoords, centerColor, 0, 0
            )

            // 8 surrounding jitter samples: glowing tint for blur
            val sampleColorRgb = if (isGlow && textGlow.value) glowRgb else rgb
            val surroundWeight = ((1.0f - centerWeight) / 8.0f) * 1.35f
            val surroundAlpha = (alpha * surroundWeight * 255f).toInt().coerceIn(1, 255)
            val surroundColor = (surroundAlpha shl 24) or (sampleColorRgb and 0x00FFFFFF)
            val diag = r * 0.7071f

            val offsets = arrayOf(
                Pair(-r, 0f), Pair(r, 0f), Pair(0f, -r), Pair(0f, r),
                Pair(-diag, -diag), Pair(diag, -diag), Pair(-diag, diag), Pair(diag, diag)
            )

            for ((dx, dy) in offsets) {
                collector.submitText(
                    poseStack, x + dx, y + dy, text,
                    false, displayMode, lightCoords, surroundColor, 0, 0
                )
            }

            // Outer ring for intense blur
            if (blur > 4.5f) {
                val r2 = r * 1.8f
                val outerWeight = surroundWeight * 0.45f
                val outerAlpha = (alpha * outerWeight * 255f).toInt().coerceIn(1, 255)
                val outerColor = (outerAlpha shl 24) or (sampleColorRgb and 0x00FFFFFF)
                val outerOffsets = arrayOf(
                    Pair(-r2, 0f), Pair(r2, 0f), Pair(0f, -r2), Pair(0f, r2)
                )
                for ((dx, dy) in outerOffsets) {
                    collector.submitText(
                        poseStack, x + dx, y + dy, text,
                        false, displayMode, lightCoords, outerColor, 0, 0
                    )
                }
            }
        }
    }

    /**
     * 2D Screen Space Mode: Classic fixed floating HUD widget.
     */
    private fun render2DMode(
        partialTicks: Float,
        isEditing: Boolean,
        hasLiveLyrics: Boolean,
        musicPlayer: CloudMusicPlayer,
        lyricData: cn.reverie.app.music.api.LyricData
    ) {
        val rx = x
        val ry = y
        val rw = width
        val rh = height
        val cx = rx + rw * 0.5f
        val cy = ry + rh * 0.5f

        val spacing = lineSpacing.getFloat()
        val blurMax = maxBlur.getFloat()

        if (isEditing) {
            Draw2D.roundedOutline(rx, ry, rw, rh, 4f, NanoVGColor(1f, 1f, 1f, 0.15f), 1f)
            NanoVGRenderer.drawCenteredString(
                "Lyric HUD (2D Fixed)", cx, ry - 7f,
                NanoVGColor(1f, 1f, 1f, 0.35f), 7.5f
            )
        }

        val edgeDistance = (rh * 0.50f).coerceAtLeast(spacing * 1.4f)
        val lines = lyricData.lines

        if (hasLiveLyrics) {
            val targetIdx = lyricData.findLineIndex(musicPlayer.currentPositionMs)
            animatedScroll += (targetIdx.toFloat() - animatedScroll) * 0.18f

            val scrollInt = animatedScroll.toInt()
            val startIdx = scrollInt - 2
            val endIdx = scrollInt + 2

            for (i in startIdx..endIdx) {
                if (i !in lines.indices) continue
                val line = lines[i]
                if (line.text.isBlank()) continue

                val lineY = cy + (i.toFloat() - animatedScroll) * spacing
                val pixelDist = abs(lineY - cy)

                if (pixelDist > edgeDistance * 1.25f) continue

                val edgeRatio = if (pixelDist <= 6.0f) 0.0f else ((pixelDist - 6.0f) / (edgeDistance - 6.0f)).coerceIn(0f, 1f)
                val blur = (edgeRatio.pow(1.15f) * blurMax).coerceIn(0f, blurMax)
                val alpha = (1.0f - edgeRatio.pow(1.4f) * 0.65f).coerceIn(0.20f, 1.0f)
                val fontSize = (12.5f - edgeRatio * 1.8f).coerceAtLeast(9.5f)

                val textColor = if (edgeRatio < 0.15f) {
                    NanoVGColor.WHITE.withAlpha(alpha)
                } else {
                    NanoVGColor(0.82f, 0.85f, 0.92f, alpha)
                }

                NanoVGRenderer.drawCenteredString(
                    line.text, cx, lineY,
                    textColor, fontSize,
                    shadow = textShadow, blur = blur
                )

                if (showTranslation.value && edgeRatio < 0.15f && line.translation.isNotBlank()) {
                    NanoVGRenderer.drawCenteredString(
                        line.translation, cx, lineY + 12.5f,
                        NanoVGColor(0.75f, 0.78f, 0.88f, 0.82f * alpha), 8.5f,
                        shadow = textShadow, blur = 0f
                    )
                }
            }
        } else {
            val demoLines = listOf(
                "Stars sink into the quiet night",
                "Chasing the eternal shadows and starlight",
                "Listening to the prelude of dreams"
            )

            for (offset in -1..1) {
                val lineText = demoLines[offset + 1]
                val lineY = cy + offset * spacing
                val pixelDist = abs(lineY - cy)

                val edgeRatio = if (pixelDist <= 6.0f) 0.0f else ((pixelDist - 6.0f) / (edgeDistance - 6.0f)).coerceIn(0f, 1f)
                val blur = (edgeRatio.pow(1.15f) * blurMax).coerceIn(0f, blurMax)
                val alpha = (1.0f - edgeRatio.pow(1.4f) * 0.65f).coerceIn(0.20f, 1.0f)
                val fontSize = (12.5f - edgeRatio * 1.8f).coerceAtLeast(9.5f)

                val textColor = if (edgeRatio < 0.15f) {
                    NanoVGColor.WHITE.withAlpha(alpha)
                } else {
                    NanoVGColor(0.82f, 0.85f, 0.92f, alpha)
                }

                NanoVGRenderer.drawCenteredString(
                    lineText, cx, lineY,
                    textColor, fontSize,
                    shadow = textShadow, blur = blur
                )
            }
        }
    }

    companion object {
        var instance: LyricHUDWidget? = null

        @JvmStatic
        fun renderInWorld(levelRenderState: LevelRenderState, submitNodeCollector: SubmitNodeCollector) {
            val widget = instance ?: return
            if (!widget.enabled || !widget.mode.isCurrent("3D")) return
            widget.renderInWorldInternal(levelRenderState, submitNodeCollector)
        }
    }
}

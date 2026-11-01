package cn.reverie.module.render.animations

import cn.reverie.module.render.Animations

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
    fun Animations.blockAnimationImpl(
        swingProgress: Float,
        equipProgress: Float,
        poseStack: PoseStack,
        armX: Int,
        fakeAutoBlock: Boolean = false
    ) {
        applyEquipOffset(poseStack, armX, equipProgress)

        fun applyStyleSwing(progress: Float) {
            if (fakeAutoBlock) {
                applyFakeBlockSwingOffset(poseStack, armX, progress)
            } else {
                applySwingOffset(poseStack, armX, progress)
            }
        }

        val process = sin(sqrt(swingProgress) * PI.toFloat())
        val processSqr = sin(swingProgress * swingProgress * PI.toFloat())

        val scaleVal = itemScale.getFloat()
        val posXVal = itemPosX.getFloat() * armX
        val posYVal = itemPosY.getFloat()
        val posZVal = itemPosZ.getFloat()
        poseStack.translate(posXVal, posYVal, posZVal)
        if (scaleVal != 1.0f) {
            poseStack.scale(scaleVal, scaleVal, scaleVal)
        }

        when (blockMode.value) {
            "1.7", "Vanilla" -> {
                applyStyleSwing(swingProgress)
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
            }

            "Swank" -> {
                applyStyleSwing(swingProgress / 2.0f)
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * process * 30.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(process * 40.0f))
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
            }

            "Swong" -> {
                applyStyleSwing(swingProgress / 2.0f)
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * -process * 20.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 30.0f))
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
            }

            "Sigma" -> {
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -process * 27.5f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 45.0f))
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.translate(0.2f * armX, -0.1f, 0.2f)
            }

            "Jello" -> {
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                val alpha = (System.currentTimeMillis() % 255).toFloat()
                poseStack.mulPose(Axis.YP.rotationDegrees(-alpha / 5.0f))
            }

            "Slide" -> {
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.translate(-0.05f * armX, 0.0f, 0.35f)
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -process * 30.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 70.0f))
            }

            "Lucky" -> {
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                val var9 = sin(sqrt(swingProgress) * 0.3215927f)
                poseStack.translate(-0.05f * armX, 0.0f, 0.3f)
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -var9 * 30.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-var9 * 70.0f))
            }

            "Ohare" -> {
                poseStack.translate(-0.05f * armX, 0.0f, -0.25f)
                applyStyleSwing(swingProgress / 2.0f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 60.0f))
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
            }

            "Wizzard" -> {
                applyStyleSwing(swingProgress / 3.0f)
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * process * 30.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(process * 10.0f))
                poseStack.translate(0.0f, 0.4f, 0.0f)
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
            }

            "Lennox" -> {
                poseStack.translate(0.0f, 0.125f, -0.1f)
                applyStyleSwing(swingProgress / 3.0f)
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -process * 16.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 50.0f))
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
            }

            "Butter" -> {
                applyStyleSwing(swingProgress * 0.5f)
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * process * 18.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 15.0f))
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.translate(0.2f * armX, -0.1f, 0.2f)
            }

            "Long Hit" -> {
                applyStyleSwing(swingProgress)
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.translate(-0.05f * armX, 0.6f, 0.3f)
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -process * 35.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 70.0f))
            }

            "Tiny Whack" -> {
                applyStyleSwing(swingProgress / 2.0f - 0.18f)
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -process * 20.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 30.0f))
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
            }

            "Skid" -> {
                applyStyleSwing(swingProgress * 0.5f)
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -process * 10.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 10.0f))
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.translate(-0.1f * armX, 0.0f, 0.1f)
            }

            "Slide2" -> {
                applyStyleSwing(swingProgress)
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.translate(0.2f * armX, -0.2f, 0.7f)
            }

            "Mix" -> {
                applyStyleSwing(swingProgress)
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
            }

            "SlideT" -> {
                applyStyleSwing(swingProgress)
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.translate(0.08f * armX, 0.2f, 0.5f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 19.0f))
            }

            "SlideA" -> {
                applyStyleSwing(swingProgress * 0.5f)
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * process * 18.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 15.0f))
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
            }

            "Epic" -> {
                applyStyleSwing(swingProgress)
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(5.0f * armX))
            }

            "Punch", "Push" -> {
                applyStyleSwing(swingProgress)
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.translate(0.0f, 0.4f, 0.1f)
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -process * 35.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 10.0f))
            }

            "Avatar" -> {
                poseStack.translate(0.56f * armX, -0.52f, -0.72f)
                poseStack.mulPose(Axis.YP.rotationDegrees(45.0f * armX))
                poseStack.mulPose(Axis.YP.rotationDegrees(-processSqr * 20.0f * armX))
                poseStack.mulPose(Axis.ZP.rotationDegrees(-process * 20.0f * armX))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 40.0f))
                poseStack.scale(0.4f, 0.4f, 0.4f)
                poseStack.translate(-0.15f * armX, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(16.0f * armX))
                poseStack.mulPose(Axis.ZP.rotationDegrees(-278.0f * armX))
            }

            "Stella" -> {
                applyStyleSwing(swingProgress)
                poseStack.translate(-0.15f, 0.1f, -0.06f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-95.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(-278.0f))
            }

            "Leaked" -> {
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-102.25f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 7.365f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * 78.05f))
                poseStack.mulPose(Axis.XP.rotationDegrees(process * -10.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * process * 30.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * process * -13.0f))
            }

            "SideDown" -> {
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * processSqr * -20.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * process * -20.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(process * -80.0f))
            }

            "Styles" -> {
                applyStyleSwing(0f)
                poseStack.translate(0.08f * armX, 0.02f, 0.0f)
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(-process * 41.0f))
            }

            "Flux" -> {
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.translate(0.0f, 0.0f, process * -0.25f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(process * -15.0f))
            }

            "Spin" -> {
                val spin = (System.currentTimeMillis() % 720) / 2.0f
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * (16.0f + spin)))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
            }

            "Screw" -> {
                val timeSpin = (System.currentTimeMillis() % 1000) / 2.7f
                poseStack.translate(armX * -0.15f, 0.05f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * timeSpin * 2.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(timeSpin))
            }

            "Swang" -> {
                applyStyleSwing(swingProgress)
                poseStack.translate(armX * -0.15f, 0.15f, 0.1f)
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * 16.0f))
                poseStack.mulPose(Axis.ZP.rotationDegrees(armX * -278.0f))
                poseStack.mulPose(Axis.XP.rotationDegrees(process * 40.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(armX * process * -10.0f))
            }
        }
    }

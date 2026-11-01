package cn.reverie.render.ui.notification
import cn.reverie.module.render.NotificationModule
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents

object NotificationSoundHelper {

    fun play(type: ToastType) {
        if (!NotificationModule.enabled) return
        val soundMode = NotificationModule.sound.value
        if (soundMode == "None") return

        try {
            val mc = Minecraft.getInstance()
            val soundManager = mc.soundManager

            when (soundMode) {
                "ModernChime" -> {
                    val pitch = when (type) {
                        ToastType.SUCCESS, ToastType.MODULE_ON -> 1.45f
                        ToastType.WARNING -> 0.95f
                        ToastType.ERROR -> 0.70f
                        ToastType.MODULE_OFF -> 0.85f
                        ToastType.INFO -> 1.20f
                    }
                    if (type == ToastType.SUCCESS || type == ToastType.MODULE_ON) {
                        soundManager.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch))
                    } else {
                        soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch))
                    }
                }
                "SoftClick" -> {
                    val pitch = when (type) {
                        ToastType.SUCCESS, ToastType.MODULE_ON -> 1.30f
                        ToastType.MODULE_OFF -> 0.80f
                        ToastType.ERROR -> 0.65f
                        else -> 1.0f
                    }
                    soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch))
                }
            }
        } catch (_: Throwable) {

        }
    }
}

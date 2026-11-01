package cn.reverie.gui.clickgui.arcade

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents

object ArcadeSoundHelper {
    private val mc: Minecraft get() = Minecraft.getInstance()

    fun playClick(pitch: Float = 1.0f) {
        try {
            mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch))
        } catch (_: Throwable) {}
    }

    fun playPop(pitch: Float = 1.0f) {
        try {
            mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch))
        } catch (_: Throwable) {}
    }

    fun playJump() {
        playPop(1.4f)
    }

    fun playScore() {
        playPop(1.85f)
    }

    fun playPowerup() {
        try {
            mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.8f))
        } catch (_: Throwable) {
            playPop(2.0f)
        }
    }

    fun playCrash() {
        try {
            mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 0.9f))
        } catch (_: Throwable) {
            playClick(0.6f)
        }
    }

    fun playUnlock() {
        try {
            mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.35f))
        } catch (_: Throwable) {
            playPop(1.8f)
        }
    }

    fun playComboTick(combo: Int) {
        val pitch = (1.0f + (combo.coerceIn(1, 10) * 0.09f))
        playPop(pitch)
    }

    fun playHarp(pitch: Float) {
        try {
            mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HARP, pitch))
        } catch (_: Throwable) {}
    }

    fun playBell(pitch: Float) {
        try {
            mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, pitch))
        } catch (_: Throwable) {
            playHarp(pitch)
        }
    }
}

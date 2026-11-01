package cn.reverie.client

import cn.reverie.gui.splash.ProtectionSplashWindow
import cn.reverie.nocturne.PackBoot
import net.fabricmc.api.ClientModInitializer

class nocturneClient : ClientModInitializer {

    companion object {
        // Keep release metadata here. build.gradle.kts reads these constants for
        // the mod version, upload id, and upload changelog.
        const val RELEASE_VERSION = "b2.5.2"
        const val RELEASE_CHANGELOG = "HUD color setting"

        init {
            try {
                System.setProperty("java.awt.headless", "false")
            } catch (ignored: Throwable) {
            }
            // Show the protection splash BEFORE any protection work runs. This
            // class-init happens ahead of the integrity hook and PackBoot, so the
            // window is already up while the real steps report their progress.
            try {
                ProtectionSplashWindow.show()
            } catch (ignored: Throwable) {
            }
        }
    }

    override fun onInitializeClient() {
        try {
            PackBoot.launch()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

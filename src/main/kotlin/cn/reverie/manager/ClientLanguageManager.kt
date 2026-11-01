package cn.reverie.manager

import net.minecraft.client.Minecraft

/** Applies the client's English UI locale once per game launch. */
object ClientLanguageManager {
    private const val ENGLISH = "en_us"
    private var checkedThisLaunch = false

    @JvmStatic
    fun ensureEnglish() {
        if (checkedThisLaunch) return
        checkedThisLaunch = true

        val client = Minecraft.getInstance()
        if (client.options.languageCode.equals(ENGLISH, ignoreCase = true) &&
            client.languageManager.selected.equals(ENGLISH, ignoreCase = true)
        ) {
            return
        }

        client.options.languageCode = ENGLISH
        client.languageManager.setSelected(ENGLISH)
        client.options.save()
        client.reloadResourcePacks()
    }
}

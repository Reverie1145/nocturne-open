package cn.reverie.module.misc

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module

object YuriGallery : Module(
    name = "Yuri Gallery",
    description = "Watanare × Yagakimi art companions across the client",
    category = Category.MISC
) {

    val menuArt = BooleanSetting("Menu Art", "Standing art & chibi on the main menu", true)
    val guiArt = BooleanSetting("GUI Art", "ClickGUI corner companions & panel gallery", true)
    val hudAvatars = BooleanSetting("HUD Avatars", "Watermark, Session & TargetHUD character portraits", true)
    val toastAvatars = BooleanSetting("Toast Avatars", "Character avatars on toast notifications", true)

    init {
        addSetting(menuArt)
        addSetting(guiArt)
        addSetting(hudAvatars)
        addSetting(toastAvatars)
    }
}

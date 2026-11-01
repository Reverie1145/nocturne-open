package cn.reverie.module

import cn.reverie.render.nanovg.NanoVGColor

enum class Category(
    val displayName: String,
    val iconName: String,
    val color: NanoVGColor = NanoVGColor.WHITE
) {
    COMBAT("Combat", "combat.png", NanoVGColor.WHITE),
    MOVEMENT("Movement", "movement.png", NanoVGColor.WHITE),
    RENDER("Render", "render.png", NanoVGColor.WHITE),
    PLAYER("Player", "player.png", NanoVGColor.WHITE),
    WORLD("World", "world.png", NanoVGColor.WHITE),
    MISC("Misc", "misc.png", NanoVGColor.WHITE)
}

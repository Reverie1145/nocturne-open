package cn.reverie.render.core

enum class RenderLayer(val zIndex: Int) {
    BACKGROUND(0),
    INGAME_HUD(100),
    OVERLAY(200),
    SCREEN(300),
    POPUP(400),
    MODAL(500),
    TOOLTIP(600),
    NOTIFICATION(700),
    DEVTOOLS(800)
}

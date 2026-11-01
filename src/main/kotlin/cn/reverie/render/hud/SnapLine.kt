package cn.reverie.render.hud

import cn.reverie.render.hud.impl.*
data class SnapLine(
    val isVertical: Boolean,
    val position: Float,
    val start: Float,
    val end: Float,
    val isCenter: Boolean = false,
    val label: String? = null
)

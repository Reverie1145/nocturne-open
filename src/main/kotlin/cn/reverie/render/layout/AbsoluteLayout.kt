package cn.reverie.render.layout

import cn.reverie.render.ui.UIElement

enum class Anchor {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER
}

class AbsoluteLayout : Layout {
    override fun layout(container: UIElement, children: List<UIElement>) {

    }
}

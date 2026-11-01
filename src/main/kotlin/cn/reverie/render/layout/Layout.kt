package cn.reverie.render.layout

import cn.reverie.render.ui.UIElement

interface Layout {
    fun layout(container: UIElement, children: List<UIElement>)
}

package cn.reverie.render.layout

import cn.reverie.render.ui.UIElement

class GridLayout(
    val columns: Int = 2,
    val horizontalGap: Float = 8.0f,
    val verticalGap: Float = 8.0f
) : Layout {

    override fun layout(container: UIElement, children: List<UIElement>) {
        val visible = children.filter { it.isVisible }
        if (visible.isEmpty() || columns <= 0) return

        val availableWidth = container.width - container.padding.horizontal - (columns - 1) * horizontalGap
        val cellWidth = availableWidth / columns

        for (i in visible.indices) {
            val child = visible[i]
            val col = i % columns
            val row = i / columns

            val x = container.padding.left + col * (cellWidth + horizontalGap)
            val y = container.padding.top + row * (child.height + verticalGap)

            child.x = x + child.margin.left
            child.y = y + child.margin.top
            child.width = cellWidth - child.margin.horizontal
        }
    }
}

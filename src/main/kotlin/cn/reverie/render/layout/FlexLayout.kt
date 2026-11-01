package cn.reverie.render.layout

import cn.reverie.render.ui.UIElement

class FlexLayout(
    val direction: FlexDirection = FlexDirection.COLUMN,
    val justifyContent: JustifyContent = JustifyContent.START,
    val alignItems: AlignItems = AlignItems.START,
    val gap: Float = 6.0f,
    val wrap: Boolean = false
) : Layout {

    override fun layout(container: UIElement, children: List<UIElement>) {
        val visibleChildren = children.filter { it.isVisible }
        if (visibleChildren.isEmpty()) return

        val isRow = (direction == FlexDirection.ROW || direction == FlexDirection.ROW_REVERSE)

        var currentMain = if (isRow) container.padding.left else container.padding.top

        for (child in visibleChildren) {
            if (isRow) {
                val childY = when (alignItems) {
                    AlignItems.START -> container.padding.top + child.margin.top
                    AlignItems.CENTER -> container.padding.top + (container.height - container.padding.vertical - child.height) * 0.5f
                    AlignItems.END -> container.height - container.padding.bottom - child.height - child.margin.bottom
                    AlignItems.STRETCH -> {
                        child.height = container.height - container.padding.vertical - child.margin.vertical
                        container.padding.top + child.margin.top
                    }
                }
                child.x = currentMain + child.margin.left
                child.y = childY
                currentMain += child.width + child.margin.horizontal + gap
            } else {
                val childX = when (alignItems) {
                    AlignItems.START -> container.padding.left + child.margin.left
                    AlignItems.CENTER -> container.padding.left + (container.width - container.padding.horizontal - child.width) * 0.5f
                    AlignItems.END -> container.width - container.padding.right - child.width - child.margin.right
                    AlignItems.STRETCH -> {
                        child.width = container.width - container.padding.horizontal - child.margin.horizontal
                        container.padding.left + child.margin.left
                    }
                }
                child.x = childX
                child.y = currentMain + child.margin.top
                currentMain += child.height + child.margin.vertical + gap
            }
        }
    }
}

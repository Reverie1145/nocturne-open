package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.IconFont
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class TreeNode(
    val name: String,
    val isFolder: Boolean = false,
    val children: MutableList<TreeNode> = ArrayList()
) {
    var isExpanded = false
}

class TreeViewWidget(
    val rootNodes: List<TreeNode>,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 160f
        height = 200f
    }

    private var hoveredNode: TreeNode? = null

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        Draw2D.roundedRect(absoluteBounds, 6f, theme.surface)
        Draw2D.roundedOutline(absoluteBounds, 6f, theme.border)

        val font = RenderEngine.fontRenderer
        var currentY = absoluteY + 6f

        fun renderNode(node: TreeNode, depth: Int) {
            val indent = depth * 14f
            val nodeX = absoluteX + 8f + indent
            val nodeH = 20f

            if (node.isFolder) {
                val icon = if (node.isExpanded) IconFont.CHEVRON_DOWN else IconFont.CHEVRON_RIGHT
                font.drawString(icon, nodeX, currentY + 5f, theme.textSecondary, 10f)
                font.drawString(IconFont.FOLDER, nodeX + 12f, currentY + 4f, theme.primary, 11f)
                font.drawString(node.name, nodeX + 26f, currentY + 4f, theme.textPrimary, 12f)
            } else {
                font.drawString(node.name, nodeX + 12f, currentY + 4f, theme.textSecondary, 12f)
            }

            currentY += nodeH

            if (node.isFolder && node.isExpanded) {
                for (child in node.children) {
                    renderNode(child, depth + 1)
                }
            }
        }

        for (root in rootNodes) {
            renderNode(root, 0)
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        var currentY = absoluteY + 6f

        fun checkClick(node: TreeNode, depth: Int): Boolean {
            val nodeH = 20f
            if (mouseY >= currentY && mouseY <= currentY + nodeH && isMouseOver(mouseX, mouseY)) {
                if (node.isFolder) {
                    node.isExpanded = !node.isExpanded
                }
                return true
            }
            currentY += nodeH
            if (node.isFolder && node.isExpanded) {
                for (child in node.children) {
                    if (checkClick(child, depth + 1)) return true
                }
            }
            return false
        }

        for (root in rootNodes) {
            if (checkClick(root, 0)) return true
        }
        return false
    }
}

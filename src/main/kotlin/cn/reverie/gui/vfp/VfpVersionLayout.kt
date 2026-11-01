package cn.reverie.gui.vfp

import com.viaversion.viafabricplus.screen.base.VFPScreen
import com.viaversion.viafabricplus.screen.impl.protocol.AbstractProtocolSelectionScreen

object VfpVersionLayout {

    private const val ROW_WIDTH = 260

    private const val PANEL_PADDING = 16

    const val SLOT_HEIGHT = 32

    fun rowWidth(screenWidth: Int): Int = minOf(ROW_WIDTH, screenWidth - 20)

    fun panelWidth(screenWidth: Int): Int = rowWidth(screenWidth) + PANEL_PADDING * 2

    fun panelX(screenWidth: Int): Int = (screenWidth - panelWidth(screenWidth)) / 2

    fun panelY(): Int = VFPScreen.SEARCH_TOP - 8

    fun panelBottom(screenHeight: Int): Int = screenHeight - 26

    @JvmStatic
    fun searchRect(screenWidth: Int): IntArray {
        val width = panelWidth(screenWidth) - PANEL_PADDING * 2
        return intArrayOf(panelX(screenWidth) + PANEL_PADDING, VFPScreen.SEARCH_TOP, width, VFPScreen.SEARCH_HEIGHT)
    }
}

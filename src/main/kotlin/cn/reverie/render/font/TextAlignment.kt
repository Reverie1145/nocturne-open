package cn.reverie.render.font

import cn.reverie.render.nanovg.NanoVGConstants

enum class TextAlignment(val nvgFlags: Int) {
    TOP_LEFT(NanoVGConstants.ALIGN_LEFT or NanoVGConstants.ALIGN_TOP),
    TOP_CENTER(NanoVGConstants.ALIGN_CENTER or NanoVGConstants.ALIGN_TOP),
    TOP_RIGHT(NanoVGConstants.ALIGN_RIGHT or NanoVGConstants.ALIGN_TOP),

    MIDDLE_LEFT(NanoVGConstants.ALIGN_LEFT or NanoVGConstants.ALIGN_MIDDLE),
    MIDDLE_CENTER(NanoVGConstants.ALIGN_CENTER or NanoVGConstants.ALIGN_MIDDLE),
    MIDDLE_RIGHT(NanoVGConstants.ALIGN_RIGHT or NanoVGConstants.ALIGN_MIDDLE),

    BOTTOM_LEFT(NanoVGConstants.ALIGN_LEFT or NanoVGConstants.ALIGN_BOTTOM),
    BOTTOM_CENTER(NanoVGConstants.ALIGN_CENTER or NanoVGConstants.ALIGN_BOTTOM),
    BOTTOM_RIGHT(NanoVGConstants.ALIGN_RIGHT or NanoVGConstants.ALIGN_BOTTOM),

    BASELINE_LEFT(NanoVGConstants.ALIGN_LEFT or NanoVGConstants.ALIGN_BASELINE),
    BASELINE_CENTER(NanoVGConstants.ALIGN_CENTER or NanoVGConstants.ALIGN_BASELINE),
    BASELINE_RIGHT(NanoVGConstants.ALIGN_RIGHT or NanoVGConstants.ALIGN_BASELINE)
}

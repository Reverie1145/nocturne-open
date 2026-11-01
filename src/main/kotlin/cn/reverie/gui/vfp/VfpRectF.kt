package cn.reverie.gui.vfp


internal data class VfpRectF(val x: Float, val y: Float, val width: Float, val height: Float) {
        fun contains(px: Float, py: Float): Boolean =
            px >= x && px <= x + width && py >= y && py <= y + height
    }

package cn.reverie.gui.splash

import java.awt.*
import java.awt.image.BufferedImage
internal object SplashImageFilters {
    fun fastBlur(src: BufferedImage, radius: Int): BufferedImage {
        val w = src.width
        val h = src.height
        val pix = src.getRGB(0, 0, w, h, null, 0, w)
        val out = IntArray(w * h)
    
        boxBlur(pix, out, w, h, radius)
        boxBlur(out, pix, w, h, radius)
    
        val dest = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        dest.setRGB(0, 0, w, h, out, 0, w)
        return dest
    }
    fun boxBlur(src: IntArray, dest: IntArray, w: Int, h: Int, r: Int) {
        val temp = IntArray(w * h)
        for (y in 0 until h) {
            val yw = y * w
            var rSum = 0
            var gSum = 0
            var bSum = 0
            var aSum = 0
            var count = 0
            for (i in -r..r) {
                val x = i.coerceIn(0, w - 1)
                val p = src[yw + x]
                aSum += (p ushr 24) and 0xFF
                rSum += (p ushr 16) and 0xFF
                gSum += (p ushr 8) and 0xFF
                bSum += p and 0xFF
                count++
            }
            for (x in 0 until w) {
                temp[yw + x] = ((aSum / count) shl 24) or
                               ((rSum / count) shl 16) or
                               ((gSum / count) shl 8) or
                               (bSum / count)
                val x1 = (x - r).coerceAtLeast(0)
                val x2 = (x + r + 1).coerceAtMost(w - 1)
                val p1 = src[yw + x1]
                val p2 = src[yw + x2]
                aSum += ((p2 ushr 24) and 0xFF) - ((p1 ushr 24) and 0xFF)
                rSum += ((p2 ushr 16) and 0xFF) - ((p1 ushr 16) and 0xFF)
                gSum += ((p2 ushr 8) and 0xFF) - ((p1 ushr 8) and 0xFF)
                bSum += (p2 and 0xFF) - (p1 and 0xFF)
            }
        }
        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            var aSum = 0
            var count = 0
            for (i in -r..r) {
                val y = i.coerceIn(0, h - 1)
                val p = temp[y * w + x]
                aSum += (p ushr 24) and 0xFF
                rSum += (p ushr 16) and 0xFF
                gSum += (p ushr 8) and 0xFF
                bSum += p and 0xFF
                count++
            }
            for (y in 0 until h) {
                dest[y * w + x] = ((aSum / count) shl 24) or
                                  ((rSum / count) shl 16) or
                                  ((gSum / count) shl 8) or
                                  (bSum / count)
                val y1 = (y - r).coerceAtLeast(0)
                val y2 = (y + r + 1).coerceAtMost(h - 1)
                val p1 = temp[y1 * w + x]
                val p2 = temp[y2 * w + x]
                aSum += ((p2 ushr 24) and 0xFF) - ((p1 ushr 24) and 0xFF)
                rSum += ((p2 ushr 16) and 0xFF) - ((p1 ushr 16) and 0xFF)
                gSum += ((p2 ushr 8) and 0xFF) - ((p1 ushr 8) and 0xFF)
                bSum += (p2 and 0xFF) - (p1 and 0xFF)
            }
        }
    }
}

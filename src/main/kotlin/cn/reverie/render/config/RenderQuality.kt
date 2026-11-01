package cn.reverie.render.config

enum class RenderQuality(
    val kawaseIterations: Int,
    val shadowBlurSamples: Int,
    val enableMsaa: Boolean,
    val enableBloom: Boolean
) {
    LOW(2, 4, false, false),
    MEDIUM(3, 8, false, false),
    HIGH(4, 16, true, true),
    ULTRA(6, 24, true, true)
}

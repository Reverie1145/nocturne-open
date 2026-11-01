package cn.reverie.render.ui

import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.nanovg.NanoVGColor

data class UITheme(
    val name: String,
    val background: NanoVGColor,
    val surface: NanoVGColor,
    val surfaceHover: NanoVGColor,
    val surfaceActive: NanoVGColor,
    val primary: NanoVGColor,
    val primaryHover: NanoVGColor,
    val textPrimary: NanoVGColor,
    val textSecondary: NanoVGColor,
    val textDisabled: NanoVGColor,
    val border: NanoVGColor,
    val borderFocus: NanoVGColor,
    val success: NanoVGColor,
    val warning: NanoVGColor,
    val error: NanoVGColor,
    val info: NanoVGColor,
    val cornerRadii: CornerRadii = CornerRadii(6f)
) {
    companion object {
        val DARK = UITheme(
            name = "Dark",
            background = NanoVGColor.fromHex("#121316"),
            surface = NanoVGColor.fromHex("#1A1B20"),
            surfaceHover = NanoVGColor.fromHex("#24262E"),
            surfaceActive = NanoVGColor.fromHex("#2C2E38"),
            primary = NanoVGColor.fromHex("#5E6AD2"),
            primaryHover = NanoVGColor.fromHex("#717DE3"),
            textPrimary = NanoVGColor.fromHex("#F4F4F6"),
            textSecondary = NanoVGColor.fromHex("#8E90A4"),
            textDisabled = NanoVGColor.fromHex("#4E5062"),
            border = NanoVGColor.fromHex("#2B2D38"),
            borderFocus = NanoVGColor.fromHex("#5E6AD2"),
            success = NanoVGColor.fromHex("#4EBA6F"),
            warning = NanoVGColor.fromHex("#E0A838"),
            error = NanoVGColor.fromHex("#EB5757"),
            info = NanoVGColor.fromHex("#3E95FF")
        )

        val MIDNIGHT = UITheme(
            name = "Midnight",
            background = NanoVGColor.fromHex("#0A0B10"),
            surface = NanoVGColor.fromHex("#12141F"),
            surfaceHover = NanoVGColor.fromHex("#1C2032"),
            surfaceActive = NanoVGColor.fromHex("#252A42"),
            primary = NanoVGColor.fromHex("#7B61FF"),
            primaryHover = NanoVGColor.fromHex("#9480FF"),
            textPrimary = NanoVGColor.fromHex("#FFFFFF"),
            textSecondary = NanoVGColor.fromHex("#7E8B9B"),
            textDisabled = NanoVGColor.fromHex("#424B5A"),
            border = NanoVGColor.fromHex("#202538"),
            borderFocus = NanoVGColor.fromHex("#7B61FF"),
            success = NanoVGColor.fromHex("#00C853"),
            warning = NanoVGColor.fromHex("#FFD600"),
            error = NanoVGColor.fromHex("#FF3D00"),
            info = NanoVGColor.fromHex("#00B0FF")
        )
    }
}

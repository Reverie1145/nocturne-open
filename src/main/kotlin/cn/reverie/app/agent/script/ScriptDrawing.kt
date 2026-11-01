package cn.reverie.app.agent.script

import cn.reverie.app.agent.jsonObject
import com.google.gson.*
import kotlin.math.abs

/** Only immutable values cross from Rhino's worker to the render thread. No native handles enter JS. */
data class ScriptRenderInput(
    val width: Float, val height: Float, val pixelRatio: Float = 1f,
    val mouseX: Float = -1f, val mouseY: Float = -1f, val partialTicks: Float = 0f,
    val screen: String? = null, val theme: JsonObject = JsonObject(), val nanoTime: Long = System.nanoTime()
) {
    init {
        require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
        require(pixelRatio.isFinite() && pixelRatio > 0)
    }
    fun sameCanvas(other: ScriptRenderInput) = width == other.width && height == other.height && pixelRatio == other.pixelRatio && screen == other.screen
}

internal data class ScriptDrawCommand(val op: String, val numbers: List<Float> = emptyList(),
    val text: String = "", val color: ScriptDrawColor? = null, val paint: ScriptDrawPaint? = null)
internal data class ScriptDrawColor(val r: Float, val g: Float, val b: Float, val a: Float) {
    companion object {
        private val HEX = Regex("#[a-fA-F0-9]{6}([a-fA-F0-9]{2})?")
        fun parse(value: JsonElement): ScriptDrawColor {
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "颜色应为 #RRGGBB 或 #RRGGBBAA" }
            val hex = value.asString
            require(hex.matches(HEX)) { "颜色应为 #RRGGBB 或 #RRGGBBAA（末两位为透明度）" }
            fun channel(offset: Int) = hex.substring(offset, offset + 2).toInt(16) / 255f
            return ScriptDrawColor(channel(1), channel(3), channel(5), if (hex.length == 9) channel(7) else 1f)
        }
    }
}
internal data class ScriptDrawPaint(val kind: String, val numbers: List<Float>, val inner: ScriptDrawColor, val outer: ScriptDrawColor)
internal data class ScriptDrawFrame(val input: ScriptRenderInput, val commands: List<ScriptDrawCommand>, val builtAt: Long = System.nanoTime(), val generation: Long = 0)

/** Bounded, transactional NanoVG vocabulary. Invalid commands never reach native code. */
internal class ScriptDrawRecorder(private val input: ScriptRenderInput) {
    private val commands = ArrayList<ScriptDrawCommand>()
    private var depth = 0
    private var path = false
    private var point = false
    private var geometry = 0
    private var draws = 0
    private var textChars = 0
    private var transform = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    private val transforms = ArrayDeque<FloatArray>()

    fun finish(): ScriptDrawFrame {
        require(depth == 0) { "render2d 中 save()/restore() 未配对（还差 $depth 次 restore）" }
        return ScriptDrawFrame(input, commands.toList())
    }
    fun record(op: String, args: List<JsonElement>) {
        require(commands.size < MAX_COMMANDS) { "每帧最多 $MAX_COMMANDS 条绘制指令" }
        fun arity(count: Int) { require(args.size == count) { "$op 需要 $count 个参数，收到 ${args.size} 个" } }
        fun n(index: Int, min: Float = -32768f, max: Float = 32768f) = number(args.getOrNull(index), "$op[$index]", min, max)
        fun ns(count: Int): List<Float> { arity(count); return (0 until count).map { n(it) } }
        fun s(index: Int): String {
            val value = args.getOrNull(index)
            require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isString) { "$op[$index] 需要字符串" }
            return value.asString
        }
        fun choice(index: Int, values: List<String>): Float {
            val value = s(index)
            require(value in values) { "$op 参数应为 ${values.joinToString("/")}" }
            return values.indexOf(value).toFloat()
        }
        fun shape(values: List<Float>, cost: Int = 4): ScriptDrawCommand {
            require(path) { "$op 前需要 beginPath()" }; geometry += cost; point = true
            return ScriptDrawCommand(op, values)
        }
        val command = when (op) {
            "save" -> {
                arity(0); require(depth < MAX_DEPTH) { "绘制状态最多嵌套 $MAX_DEPTH 层" }
                depth++; transforms.addLast(transform.copyOf()); ScriptDrawCommand(op)
            }
            "restore" -> {
                arity(0); require(depth > 0) { "restore() 没有对应 save()" }
                depth--; transform = transforms.removeLast(); ScriptDrawCommand(op)
            }
            "resetTransform" -> { arity(0); transform = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f); ScriptDrawCommand(op) }
            "translate", "rotate", "scale", "transform" -> {
                val values = ns(if (op == "rotate") 1 else if (op == "transform") 6 else 2)
                val next = when (op) {
                    "translate" -> floatArrayOf(1f, 0f, 0f, 1f, values[0], values[1])
                    "rotate" -> {
                        require(abs(values[0]) <= 1000) { "rotate 使用弧度，绝对值最多 1000" }
                        val c = kotlin.math.cos(values[0]); val sn = kotlin.math.sin(values[0]); floatArrayOf(c, sn, -sn, c, 0f, 0f)
                    }
                    "scale" -> {
                        require(values.all { abs(it) in 0.01f..16f }) { "scale 分量的绝对值应在 0.01..16" }
                        floatArrayOf(values[0], 0f, 0f, values[1], 0f, 0f)
                    }
                    else -> values.toFloatArray()
                }
                // NanoVG premultiplies the new local transform; this is current * local in column form.
                val a = transform; val b = next
                val combined = floatArrayOf(a[0]*b[0]+a[2]*b[1], a[1]*b[0]+a[3]*b[1],
                    a[0]*b[2]+a[2]*b[3], a[1]*b[2]+a[3]*b[3], a[0]*b[4]+a[2]*b[5]+a[4], a[1]*b[4]+a[3]*b[5]+a[5])
                require(combined.take(4).all { it.isFinite() && abs(it) <= 64f } && combined.drop(4).all { it.isFinite() && abs(it) <= 32768f }) { "累计变换超过安全范围" }
                transform = combined; ScriptDrawCommand(op, values)
            }
            "scissor", "intersectScissor", "rect", "roundedRect", "roundedRectVarying" -> {
                val count = when(op) { "roundedRect" -> 5; "roundedRectVarying" -> 8; else -> 4 }
                arity(count)
                val values = listOf(n(0), n(1), n(2, 0f, 8192f), n(3, 0f, 8192f)) + (4 until count).map { n(it, 0f, 4096f) }
                if (op.contains("Scissor") || op == "scissor") ScriptDrawCommand(op, values) else shape(values, 16)
            }
            "resetScissor", "beginPath" -> {
                arity(0); if (op == "beginPath") { path = true; point = false }; ScriptDrawCommand(op)
            }
            "moveTo", "lineTo", "bezierTo", "quadTo", "arcTo" -> {
                val values = ns(when(op) { "bezierTo" -> 6; "quadTo" -> 4; "arcTo" -> 5; else -> 2 })
                require(op == "moveTo" || point) { "$op 前需要 moveTo() 或形状" }
                if (op == "arcTo") require(values[4] in 0f..4096f) { "arcTo 半径应在 0..4096" }
                shape(values, if (op in setOf("bezierTo", "quadTo", "arcTo")) 32 else 1)
            }
            "closePath", "pathWinding" -> {
                arity(if (op == "closePath") 0 else 1); require(path && point) { "$op 前需要路径或形状" }
                ScriptDrawCommand(op, if (op == "pathWinding") listOf(choice(0, listOf("solid", "hole"))) else emptyList())
            }
            "circle", "ellipse", "arc" -> {
                arity(when(op) { "circle" -> 3; "ellipse" -> 4; else -> 6 })
                val values = listOf(n(0), n(1), n(2, 0f, 4096f)) + when(op) {
                    "ellipse" -> listOf(n(3, 0f, 4096f))
                    "arc" -> listOf(n(3, -1000f, 1000f), n(4, -1000f, 1000f), choice(5, listOf("cw", "ccw")))
                    else -> emptyList()
                }
                shape(values, 32)
            }
            "fill", "stroke" -> {
                arity(0); require(path && point) { "$op 前需要路径或形状" }; draws++; ScriptDrawCommand(op)
            }
            "fillColor", "strokeColor" -> { arity(1); ScriptDrawCommand(op, color = ScriptDrawColor.parse(args[0])) }
            "fillPaint", "strokePaint" -> { arity(1); ScriptDrawCommand(op, paint = parsePaint(args[0])) }
            "lineCap", "lineJoin" -> {
                arity(1); ScriptDrawCommand(op, listOf(choice(0, if (op == "lineCap") listOf("butt", "round", "square") else listOf("miter", "round", "bevel"))))
            }
            "globalAlpha", "strokeWidth", "miterLimit", "fontSize", "fontBlur", "textLetterSpacing", "textLineHeight" -> {
                arity(1)
                val bounds = when(op) {
                    "globalAlpha" -> 0f to 1f; "strokeWidth" -> 0.1f to 64f; "miterLimit" -> 1f to 20f
                    "fontSize" -> 6f to 128f; "fontBlur" -> 0f to 10f; "textLetterSpacing" -> -4f to 32f; else -> 0.5f to 4f
                }
                ScriptDrawCommand(op, listOf(n(0, bounds.first, bounds.second)))
            }
            "fontFace" -> {
                arity(1); require(s(0) in listOf("default", "ui", "mono")) { "fontFace 应为 default/ui/mono" }; ScriptDrawCommand(op, text = s(0))
            }
            "textAlign" -> {
                arity(2); ScriptDrawCommand(op, listOf(choice(0, listOf("left", "center", "right")), choice(1, listOf("top", "middle", "bottom", "baseline"))))
            }
            "text", "textBox", "textEllipsis" -> {
                arity(if (op == "text") 3 else 4)
                val value = s(if (op == "text") 2 else 3)
                require(value.length <= 4096 && !value.contains('\u0000')) { "文字最多 4096 字符，不含 NUL" }
                require(op == "textBox" || !value.contains('\n') && !value.contains('\r')) { "多行文字请用 textBox()" }
                textChars += value.length; draws++
                ScriptDrawCommand(op, listOf(n(0), n(1)) + if (op != "text") listOf(n(2, 1f, 8192f)) else emptyList(), text = value)
            }
            else -> error("未知绘制接口：$op")
        }
        require(geometry <= MAX_GEOMETRY) { "每帧路径复杂度最多 $MAX_GEOMETRY；请减少曲线和形状" }
        require(draws <= MAX_DRAWS) { "每帧最多 $MAX_DRAWS 次 fill/stroke/文字绘制" }
        require(textChars <= MAX_TEXT) { "每帧文字合计最多 $MAX_TEXT 字符" }
        commands.add(command)
    }
    companion object {
        const val MAX_COMMANDS = 1024
        const val MAX_DEPTH = 16
        const val MAX_GEOMETRY = 4096
        const val MAX_DRAWS = 256
        const val MAX_TEXT = 8192
        val operations = listOf("save", "restore", "resetTransform", "translate", "rotate", "scale", "transform", "globalAlpha",
            "scissor", "intersectScissor", "resetScissor", "beginPath", "moveTo", "lineTo", "bezierTo", "quadTo", "arcTo",
            "closePath", "pathWinding", "rect", "roundedRect", "roundedRectVarying", "circle", "ellipse", "arc", "fill", "stroke",
            "fillColor", "strokeColor", "fillPaint", "strokePaint", "strokeWidth", "miterLimit", "lineCap", "lineJoin",
            "fontFace", "fontSize", "fontBlur", "textLetterSpacing", "textLineHeight", "textAlign", "text", "textBox", "textEllipsis")
        private fun number(value: JsonElement?, name: String, min: Float, max: Float): Float {
            require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isNumber) { "$name 需要有限数字" }
            val result = value.asDouble
            require(result.isFinite() && result >= min && result <= max) { "$name 应在 $min..$max" }
            return result.toFloat()
        }
        fun rgba(args: List<JsonElement>): JsonElement {
            require(args.size == 4) { "rgba(r,g,b,a) 需要 4 个参数；rgb 为 0..255，a 为 0..1" }
            val rgb = (0..2).map { number(args[it], "rgba[$it]", 0f, 255f).toInt() }
            val alpha = kotlin.math.round(number(args[3], "rgba[3]", 0f, 1f) * 255).toInt()
            return JsonPrimitive("#" + (rgb + alpha).joinToString("") { it.toString(16).padStart(2, '0') })
        }
        fun gradient(kind: String, args: List<JsonElement>): JsonElement {
            val count = when(kind) { "linearGradient", "radialGradient" -> 4; "boxGradient" -> 6; else -> error("未知渐变") }
            require(args.size == count + 2) { "$kind 需要 ${count + 2} 个参数" }
            val values = (0 until count).map { number(args[it], "$kind[$it]", -32768f, 32768f) }
            if (kind == "radialGradient") require(values[2] >= 0 && values[3] > values[2] && values[3] <= 4096) { "radialGradient 要求 0 ≤ innerRadius < outerRadius ≤ 4096" }
            if (kind == "boxGradient") require(values[2] in 0f..8192f && values[3] in 0f..8192f && values[4] in 0f..4096f && values[5] in 0.1f..4096f) { "boxGradient 要求有效宽高、圆角和 feather（0.1..4096）" }
            ScriptDrawColor.parse(args[count]); ScriptDrawColor.parse(args[count + 1])
            return jsonObject("kind" to kind, "values" to values, "inner" to args[count].asString, "outer" to args[count + 1].asString)
        }
        private fun parsePaint(value: JsonElement): ScriptDrawPaint {
            require(value.isJsonObject) { "paint 应来自 linearGradient/radialGradient/boxGradient" }
            val paint = value.asJsonObject
            require(paint.keySet() == setOf("kind", "values", "inner", "outer") && paint.get("kind").isJsonPrimitive && paint.get("values").isJsonArray) { "paint 结构无效" }
            val kind = paint.get("kind").asString
            val values = paint.getAsJsonArray("values").toList()
            // Validate a copy even if JS has modified the returned plain object.
            gradient(kind, values + listOf(paint.get("inner"), paint.get("outer")))
            return ScriptDrawPaint(kind, values.map { it.asFloat }, ScriptDrawColor.parse(paint.get("inner")), ScriptDrawColor.parse(paint.get("outer")))
        }
    }
}

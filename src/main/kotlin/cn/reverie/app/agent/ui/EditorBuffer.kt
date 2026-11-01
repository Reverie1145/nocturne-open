package cn.reverie.app.agent.ui

/** Text editing is independent of rendering and supports Unicode, selection and undo/redo. */
class EditorBuffer(initial: String = "", private val maxLength: Int = 256000) {
    data class Snapshot(val text: String, val cursor: Int, val anchor: Int)
    var text: String = AgentTextLayout.prefix(initial.replace("\r\n", "\n").replace('\r', '\n'), maxLength)
        private set
    var cursor: Int = text.length
        private set
    var anchor: Int = cursor
        private set
    private val undo = ArrayDeque<Snapshot>()
    private val redo = ArrayDeque<Snapshot>()
    val selectionStart get() = minOf(cursor, anchor)
    val selectionEnd get() = maxOf(cursor, anchor)
    val selectedText get() = text.substring(selectionStart, selectionEnd)
    val line get() = text.take(cursor).count { it == '\n' }
    val column get() = cursor - (text.lastIndexOf('\n', cursor - 1) + 1)
    fun load(value: String) {
        text = AgentTextLayout.prefix(value.replace("\r\n", "\n").replace('\r', '\n'), maxLength)
        cursor = text.length; anchor = cursor; undo.clear(); redo.clear()
    }
    fun move(position: Int, select: Boolean = false) {
        cursor = position.coerceIn(0, text.length)
        if (cursor in 1 until text.length && text[cursor].isLowSurrogate() && text[cursor - 1].isHighSurrogate()) cursor--
        if (!select) anchor = cursor
    }
    fun selectAll() { anchor = 0; cursor = text.length }
    fun insert(value: String) {
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
            .filter { it == '\n' || it == '\t' || it.code >= 32 }
        val room = maxLength - text.length + selectionEnd - selectionStart
        var insert = normalized.take(room)
        if (insert.lastOrNull()?.isHighSurrogate() == true) insert = insert.dropLast(1)
        if (insert.isEmpty() && selectionStart == selectionEnd) return
        checkpoint()
        val start = selectionStart
        text = text.substring(0, start) + insert + text.substring(selectionEnd)
        cursor = start + insert.length; anchor = cursor
    }
    fun backspace() {
        if (selectionStart != selectionEnd) { insert(""); return }
        if (cursor == 0) return
        checkpoint()
        val start = Character.offsetByCodePoints(text, cursor, -1)
        text = text.removeRange(start, cursor); cursor = start; anchor = cursor
    }
    fun delete() {
        if (selectionStart != selectionEnd) { insert(""); return }
        if (cursor >= text.length) return
        checkpoint()
        val end = Character.offsetByCodePoints(text, cursor, 1)
        text = text.removeRange(cursor, end); anchor = cursor
    }
    fun horizontal(delta: Int, select: Boolean) {
        if (!select && cursor != anchor) move(if (delta < 0) selectionStart else selectionEnd)
        else if ((delta < 0 && cursor > 0) || (delta > 0 && cursor < text.length)) move(Character.offsetByCodePoints(text, cursor, delta), select)
    }
    fun vertical(delta: Int, select: Boolean) {
        val start = text.lastIndexOf('\n', cursor - 1) + 1
        val column = cursor - start
        if (delta < 0 && start > 0) {
            val previousStart = text.lastIndexOf('\n', start - 2) + 1
            move(minOf(previousStart + column, start - 1), select)
        } else if (delta > 0) {
            val end = text.indexOf('\n', cursor)
            if (end >= 0) {
                val nextEnd = text.indexOf('\n', end + 1).let { if (it < 0) text.length else it }
                move(minOf(end + 1 + column, nextEnd), select)
            }
        }
    }
    fun home(select: Boolean, entire: Boolean = false) = move(if (entire) 0 else text.lastIndexOf('\n', cursor - 1) + 1, select)
    fun end(select: Boolean, entire: Boolean = false) = move(if (entire) text.length else text.indexOf('\n', cursor).let { if (it < 0) text.length else it }, select)
    fun undo() { if (undo.isNotEmpty()) { redo.addLast(snapshot()); restore(undo.removeLast()) } }
    fun redo() { if (redo.isNotEmpty()) { undo.addLast(snapshot()); restore(redo.removeLast()) } }
    private fun snapshot() = Snapshot(text, cursor, anchor)
    private fun checkpoint() { undo.addLast(snapshot()); while (undo.size > 32) undo.removeFirst(); redo.clear() }
    private fun restore(value: Snapshot) { text = value.text; cursor = value.cursor; anchor = value.anchor }
}

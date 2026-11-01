package cn.reverie.app.agent.ui

import cn.reverie.app.agent.ScriptRecord

/** UI-thread reconciliation. A read completing after a keystroke cannot discard that edit. */
internal class AgentDraftSync(initial: ScriptRecord) {
    enum class Change { NONE, RELOAD, CONFLICT, DELETED }
    var saved: ScriptRecord? = initial
        private set
    var incoming: ScriptRecord? = null
        private set
    var deleted = false
        private set
    val conflict get() = incoming != null || deleted
    fun dirty(local: ScriptRecord) = saved?.let { !equivalent(local, it) } ?: true
    fun observe(local: ScriptRecord, disk: ScriptRecord?): Change {
        if (disk == null) {
            incoming = null; deleted = saved != null
            return if (deleted) Change.DELETED else Change.NONE
        }
        deleted = false
        if (!dirty(local) || equivalent(local, disk)) {
            saved = disk; incoming = null
            return if (equivalent(local, disk)) Change.NONE else Change.RELOAD
        }
        if (saved?.let { equivalent(disk, it) } == true) {
            saved = disk; incoming = null; return Change.NONE
        }
        incoming = disk; return Change.CONFLICT
    }
    fun adoptDisk(): ScriptRecord {
        val record = incoming ?: error("磁盘版本已删除，不能采用")
        saved = record; incoming = null; deleted = false
        return record
    }
    fun keepLocal() {
        check(conflict) { "没有需要解决的版本冲突" }
        saved = incoming; incoming = null; deleted = false
    }
    fun markSaved(record: ScriptRecord) { saved = record; incoming = null; deleted = false }
    companion object {
        // The editor normalizes line endings, while saved keeps the exact disk bytes for optimistic writes.
        private fun equivalent(a: ScriptRecord, b: ScriptRecord) = a.manifest == b.manifest &&
            normalize(a.source) == normalize(b.source)
        private fun normalize(source: String) = source.replace("\r\n", "\n").replace('\r', '\n')
    }
}

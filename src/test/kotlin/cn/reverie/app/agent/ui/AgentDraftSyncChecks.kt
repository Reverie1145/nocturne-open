package cn.reverie.app.agent.ui

import cn.reverie.app.agent.*
import java.nio.file.Files
import com.google.gson.JsonParser
import kotlin.test.*

class AgentDraftSyncChecks {
    private fun record(source: String, name: String = "Fixture") = ScriptRecord(ScriptManifest("draft", name), source)

    @Test fun cleanDraftTracksAgentChangesAndLineEndingNormalizationDoesNotCreateFalseEdits() {
        val original = record("console.log('old');\r\n")
        val editor = EditorBuffer(original.source)
        val state = AgentDraftSync(original)
        assertFalse(state.dirty(record(editor.text)))
        assertEquals(original.source, state.saved!!.source)
        val disk = record("console.log('new');\n", "Updated")
        assertEquals(AgentDraftSync.Change.RELOAD, state.observe(record(editor.text), disk))
        editor.load(disk.source)
        assertFalse(state.dirty(record(editor.text, "Updated"))); assertFalse(state.conflict)
    }

    @Test fun editsMadeDuringAnAsyncReadRemainIntactUntilAReviewedVersionIsChosen() {
        val original = record("console.log('old');\n")
        val state = AgentDraftSync(original); val editor = EditorBuffer(original.source)
        val disk = record("console.log('AI');\n")
        editor.insert("// user typed while a disk read was in flight\n")
        val local = record(editor.text)
        assertEquals(AgentDraftSync.Change.CONFLICT, state.observe(local, disk))
        assertEquals(original, state.saved); assertEquals(disk, state.incoming)
        assertContains(editor.text, "user typed")
        state.keepLocal()
        assertEquals(disk, state.saved); assertTrue(state.dirty(local)); assertFalse(state.conflict)
        val newer = record("console.log('AI changed again');\n")
        assertEquals(AgentDraftSync.Change.CONFLICT, state.observe(local, newer))
        assertEquals(newer, state.adoptDisk()); assertFalse(state.conflict)
        assertFalse(state.dirty(newer))
    }

    @Test fun externallyDeletedFilesKeepTheirDraftAndCanBeRecreatedOnlyAfterExplicitChoice() {
        val original = record("console.log('keep');")
        val state = AgentDraftSync(original)
        assertEquals(AgentDraftSync.Change.DELETED, state.observe(original, null))
        assertTrue(state.conflict); assertEquals(original, state.saved)
        assertFailsWith<IllegalStateException> { state.adoptDisk() }
        state.keepLocal()
        assertNull(state.saved); assertTrue(state.dirty(original)); assertFalse(state.conflict)
        assertEquals(AgentDraftSync.Change.NONE, state.observe(original, null))
        state.markSaved(original); assertFalse(state.dirty(original))
    }

    @Test fun conflictBackupsRetainCompleteRecoverableDraftsAndStayBounded() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("draft-backup")))
        val source = "// 😀\n".repeat(4000)
        val local = record(source).copy(manifest = ScriptManifest("draft", "Unsaved", capabilities = setOf("modules")))
        repeat(12) { repository.backupDraft(local, "choice-" + it) }
        val directory = repository.files.path("draft-backups/draft/.keep").parent
        val backups = Files.list(directory).use { it.toList() }
        assertEquals(10, backups.size)
        backups.forEach { file ->
            val recovered = agentGson.fromJson(JsonParser.parseString(Files.readString(file)).asJsonObject.get("record"), ScriptRecord::class.java)
            assertEquals(local, recovered)
        }
        assertNull(repository.readOrNull("draft"))
        repository.save(local); assertEquals(1, repository.revision)
        repository.trash("draft"); assertNull(repository.readOrNull("draft")); assertEquals(2, repository.revision)
    }
}

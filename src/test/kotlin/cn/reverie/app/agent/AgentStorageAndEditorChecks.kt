package cn.reverie.app.agent

import cn.reverie.app.agent.ui.EditorBuffer
import java.nio.file.Files
import kotlin.test.*

class AgentStorageAndEditorChecks {
    @Test fun pathsAndWindowsDeviceNamesCannotEscapeTheWorkspace() {
        val files = AgentFiles(Files.createTempDirectory("agent-paths"))
        assertFailsWith<IllegalArgumentException> { files.path("../outside") }
        assertFailsWith<IllegalArgumentException> { files.path(files.root.resolveSibling("outside").toAbsolutePath().toString()) }
        for (id in listOf("../x", "x/y", "CON", "COM1", "NUL", "x:stream", "")) assertFailsWith<IllegalArgumentException> { AgentFiles.requireId(id) }
        assertEquals("health-watch_2", AgentFiles.requireId("health-watch_2"))
    }
    @Test fun manifestsGrantsMemoryAndTrashRoundTripIndependently() {
        val repository = ScriptRepository(AgentFiles(Files.createTempDirectory("agent-repository")))
        val record = ScriptRecord(ScriptManifest("demo", "中文名称", "description", setOf("ai"), true), "console.log('hello')")
        repository.save(record)
        assertEquals(record, repository.read("demo")); assertEquals(listOf(record.manifest), repository.list())
        assertTrue(repository.grants("demo").isEmpty())
        repository.setGrants("demo", setOf("ai")); assertEquals(setOf("ai"), repository.grants("demo"))
        repository.storage("demo", jsonObject("value" to 42)); assertEquals(42, repository.storage("demo").int("value"))
        repository.trash("demo"); assertTrue(repository.list().isEmpty())
        assertEquals(2, Files.list(repository.files.root.resolve("trash")).use { it.count() })
        assertEquals(42, repository.storage("demo").int("value"))
    }
    @Test fun apiKeyIsEncryptedAndExcludedFromSettingsJson() {
        val files = AgentFiles(Files.createTempDirectory("agent-settings"))
        val store = AgentSettingsStore(files)
        val options = AgentSettings(model = "test-model", protocol = AgentProtocol.ANTHROPIC, apiKeyEnvironment = "NOCTURNE_TEST_UNSET")
        store.save(options, "synthetic-api-secret")
        assertFalse(files.read("settings.json")!!.contains("synthetic-api-secret"))
        assertFalse(files.read("api-key.enc")!!.contains("synthetic-api-secret"))
        val restored = AgentSettingsStore(files)
        assertEquals(options, restored.settings); assertEquals("synthetic-api-secret", restored.apiKey())
    }
    @Test fun editorSelectionUndoAndUnicodeNeverSplitSurrogatePairs() {
        val editor = EditorBuffer("a😀b\nsecond")
        editor.move(3); editor.backspace(); assertEquals("ab\nsecond", editor.text)
        editor.undo(); assertEquals("a😀b\nsecond", editor.text)
        editor.move(1); editor.horizontal(1, false); assertEquals(3, editor.cursor)
        editor.horizontal(1, true); assertEquals("b", editor.selectedText)
        editor.insert("new\r\nline"); assertEquals("a😀new\nline\nsecond", editor.text)
        editor.undo(); assertEquals("a😀b\nsecond", editor.text)
        editor.redo(); assertEquals("a😀new\nline\nsecond", editor.text)
    }
    @Test fun editorLineNavigationAndSelectionReplaceTheExpectedText() {
        val editor = EditorBuffer("first\nsecond\nthird")
        editor.move(3); editor.vertical(1, false); assertEquals(9, editor.cursor)
        editor.home(false); assertEquals(6, editor.cursor)
        editor.end(true); assertEquals("second", editor.selectedText)
        editor.insert("new"); assertEquals("first\nnew\nthird", editor.text)
        editor.selectAll(); editor.insert("short"); assertEquals("short", editor.text)
        editor.undo(); assertEquals("first\nnew\nthird", editor.text)
    }
}

package cn.reverie.app.agent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

/** All agent-managed paths are rooted here, including checks against symlinks and Windows devices. */
class AgentFiles(val root: Path) {
    init { Files.createDirectories(root) }
    fun path(relative: String): Path {
        val base = root.toAbsolutePath().normalize()
        val target = base.resolve(relative).normalize()
        require(target.startsWith(base) && target != base) { "路径必须位于 Agent 工作目录" }
        var current = base
        require(!Files.isSymbolicLink(base)) { "工作目录不能是符号链接" }
        for (part in base.relativize(target)) {
            current = current.resolve(part)
            require(!Files.isSymbolicLink(current)) { "不能访问符号链接" }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                require(current.toRealPath().startsWith(base.toRealPath())) { "路径越过工作目录" }
            }
        }
        return target
    }
    @Synchronized fun write(relative: String, content: String, limit: Int = 2_000_000) {
        require(content.toByteArray(Charsets.UTF_8).size <= limit) { "文件超过大小限制" }
        val target = path(relative)
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, ".agent-", ".tmp")
        try {
            Files.writeString(temp, content, Charsets.UTF_8)
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temp) }
    }
    fun read(relative: String, limit: Long = 2_000_000): String? {
        val target = path(relative)
        if (!Files.exists(target)) return null
        require(Files.size(target) <= limit) { "文件超过大小限制" }
        return Files.readString(target, Charsets.UTF_8)
    }
    companion object {
        private val idPattern = Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")
        private val devices = setOf("CON", "PRN", "AUX", "NUL") +
            (1..9).flatMap { listOf("COM$it", "LPT$it") }
        fun requireId(id: String): String {
            require(idPattern.matches(id) && id.uppercase() !in devices) { "名称只能包含字母、数字、下划线和短横线（最多 64 字符）" }
            return id
        }
    }
}

data class ScriptManifest(
    @field:SerializedName("id") val id: String,
    @field:SerializedName("name") val name: String = id,
    @field:SerializedName("description") val description: String = "",
    @field:SerializedName("capabilities") val capabilities: Set<String> = emptySet(),
    @field:SerializedName("autoStart") val autoStart: Boolean = false
)
data class ScriptRecord(@field:SerializedName("manifest") val manifest: ScriptManifest,
                        @field:SerializedName("source") val source: String)

class ScriptRepository(val files: AgentFiles) {
    private val changes = java.util.concurrent.atomic.AtomicLong()
    val revision: Long get() = changes.get()
    @Synchronized fun list(): List<ScriptManifest> {
        val dir = files.path("scripts/.keep").parent
        Files.createDirectories(dir)
        return Files.list(dir).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".js") && !Files.isSymbolicLink(it) }
                .map { it.fileName.toString().removeSuffix(".js") }
                .sorted().toList().map { id ->
                    runCatching { manifest(id) }.getOrElse { ScriptManifest(id, description = "元数据错误：${it.agentError()}") }
                }
        }
    }
    fun manifest(id: String): ScriptManifest {
        AgentFiles.requireId(id)
        val raw = files.read("scripts/$id.json", 64000) ?: return ScriptManifest(id)
        val obj = JsonParser.parseString(raw).asJsonObject
        val capabilities = obj.getAsJsonArray("capabilities")?.map { it.asString }?.toSet() ?: emptySet()
        require(capabilities.all { it in SCRIPT_CAPABILITIES }) { "未知脚本权限" }
        return ScriptManifest(id, obj.string("name", id).take(100), obj.string("description").take(500),
            capabilities, obj.boolean("autoStart"))
    }
    @Synchronized fun read(id: String): ScriptRecord = ScriptRecord(manifest(id),
        files.read("scripts/${AgentFiles.requireId(id)}.js", 256000) ?: error("脚本不存在：$id"))
    @Synchronized fun readOrNull(id: String): ScriptRecord? =
        if (Files.exists(files.path("scripts/${AgentFiles.requireId(id)}.js"), LinkOption.NOFOLLOW_LINKS)) read(id) else null
    @Synchronized fun save(record: ScriptRecord) {
        val id = AgentFiles.requireId(record.manifest.id)
        require(record.manifest.capabilities.all { it in SCRIPT_CAPABILITIES }) { "未知脚本权限" }
        require(record.manifest.name.length <= 100 && record.manifest.description.length <= 500)
        files.write("scripts/$id.js", record.source, 256000)
        files.write("scripts/$id.json", agentGson.toJson(record.manifest), 64000)
        changes.incrementAndGet()
    }
    @Synchronized fun trash(id: String) {
        AgentFiles.requireId(id)
        val stamp = System.currentTimeMillis()
        for (suffix in listOf("js", "json")) {
            val source = files.path("scripts/$id.$suffix")
            if (!Files.exists(source)) continue
            val target = files.path("trash/$id-$stamp.$suffix")
            Files.createDirectories(target.parent)
            Files.move(source, target)
        }
        changes.incrementAndGet()
    }
    @Synchronized internal fun backupDraft(record: ScriptRecord, reason: String): String {
        val id = AgentFiles.requireId(record.manifest.id)
        val name = "${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}"
        val relative = "draft-backups/$id/$name.json"
        files.write(relative, jsonObject("reason" to reason, "record" to record).toString(), 1_600_000)
        val directory = files.path(relative).parent
        Files.list(directory).use { entries -> entries.filter { it.fileName.toString().matches(Regex("[0-9]+-[a-f0-9-]{36}\\.json")) }
            .sorted(compareBy<java.nio.file.Path> { Files.getLastModifiedTime(it) }.reversed()).toList().drop(10)
            .forEach { Files.deleteIfExists(files.path("draft-backups/$id/" + it.fileName)) } }
        return relative
    }
    @Synchronized fun grants(id: String): Set<String> {
        AgentFiles.requireId(id)
        val raw = files.read("grants/$id.json", 64000) ?: return emptySet()
        return JsonParser.parseString(raw).asJsonArray.map { it.asString }.filter { it in SCRIPT_CAPABILITIES }.toSet()
    }
    @Synchronized fun setGrants(id: String, capabilities: Set<String>) {
        AgentFiles.requireId(id)
        require(capabilities.all { it in SCRIPT_CAPABILITIES })
        files.write("grants/$id.json", agentGson.toJson(capabilities), 64000)
    }
    @Synchronized fun storage(id: String): JsonObject {
        AgentFiles.requireId(id)
        return files.read("memory/$id.json", 64000)?.let { JsonParser.parseString(it).asJsonObject } ?: JsonObject()
    }
    @Synchronized fun storage(id: String, value: JsonObject) {
        files.write("memory/${AgentFiles.requireId(id)}.json", value.toString(), 64000)
    }
    companion object { val SCRIPT_CAPABILITIES = setOf("modules", "chat", "controls", "ai") }
}

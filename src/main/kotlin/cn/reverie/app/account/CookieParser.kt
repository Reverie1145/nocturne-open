package cn.reverie.app.account

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.LinkedHashMap

/**
 * Cookie 解析工具，支持：
 * 1. Netscape / Mozilla cookies.txt 格式（浏览器插件导出的制表符分隔格式）
 * 2. JSON 格式（EditThisCookie / Cookie-Editor 导出，包含 name 和 value）
 * 3. Raw "Cookie: name=value; ..." 格式
 */
object CookieParser {

    fun parse(content: String?): Map<String, String> {
        val jar = LinkedHashMap<String, String>()
        if (content.isNullOrBlank()) return jar
        val trimmed = content.trim()

        // 1. JSON 格式
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                parseJson(trimmed, jar)
                if (jar.isNotEmpty()) return jar
            } catch (_: Exception) {}
        }

        // 2. Netscape / Mozilla cookies.txt 格式
        var matchedNetscape = false
        for (raw in trimmed.lines()) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#HttpOnly_")) {
                line = line.substring("#HttpOnly_".length)
            } else if (line.startsWith("#")) {
                continue
            }

            var parts = line.split('\t')
            if (parts.size < 7) {
                parts = line.split("\\s+".toRegex())
            }
            if (parts.size >= 7) {
                val name = parts[5].trim()
                val value = parts.drop(6).joinToString("\t").trim()
                if (name.isNotEmpty()) {
                    jar[name] = value
                    matchedNetscape = true
                }
            }
        }
        if (matchedNetscape) return jar

        // 3. Raw "Cookie:" 格式
        var header = trimmed
        if (header.regionMatches(0, "cookie:", 0, 7, ignoreCase = true)) {
            header = header.substring(7).trim()
        }
        for (part in header.split(';')) {
            val kv = part.trim().split('=', limit = 2)
            if (kv.size == 2 && kv[0].isNotBlank()) {
                jar[kv[0].trim()] = kv[1].trim()
            }
        }

        return jar
    }

    private fun parseJson(json: String, jar: MutableMap<String, String>) {
        val element = JsonParser.parseString(json)
        val array = when {
            element.isJsonArray -> element.asJsonArray
            element.isJsonObject -> JsonArray().apply { add(element.asJsonObject) }
            else -> return
        }

        for (item in array) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject
            val name = obj.get("name")?.asString ?: continue
            val value = obj.get("value")?.asString ?: ""
            if (name.isNotBlank()) {
                jar[name] = value
            }
        }
    }
}

package cn.reverie.app.music.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import net.fabricmc.loader.api.FabricLoader
import java.awt.image.BufferedImage
import java.io.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * NetEase Cloud Music API Client port for Nocturne Client.
 * Handles authentication, QR login, recommendations, playlists, tracks, audio URLs, and lyrics.
 */
class CloudMusicApi {

    companion object {
        const val BASE_URL = "https://music.163.com"
        const val PLAY_URL_BASE = "https://interface3.music.163.com"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36"
    }

    private val configFile: Path by lazy {
        val gameDir = FabricLoader.getInstance().gameDir
        val cfgDir = gameDir.resolve("config")
        if (!Files.exists(cfgDir)) Files.createDirectories(cfgDir)
        cfgDir.resolve("nocturne_cloudmusic.json")
    }

    var cookies: String = ""
        private set

    var currentUser: MusicUser? = null
        private set

    val isLogged: Boolean get() = cookies.isNotBlank() && currentUser != null

    init {
        loadConfig()
    }

    fun loadConfig() {
        try {
            if (Files.exists(configFile)) {
                val json = JsonParser.parseString(Files.readString(configFile)).asJsonObject
                if (json.has("cookie")) {
                    cookies = json.get("cookie").asString
                }
            }
        } catch (e: Exception) {
            System.err.println("[CloudMusicApi] Failed to load config: ${e.message}")
        }
    }

    fun saveConfig() {
        try {
            val json = JsonObject()
            json.addProperty("cookie", cookies)
            currentUser?.let {
                json.addProperty("userId", it.id)
                json.addProperty("userName", it.name)
            }
            Files.writeString(configFile, json.toString())
        } catch (e: Exception) {
            System.err.println("[CloudMusicApi] Failed to save config: ${e.message}")
        }
    }

    fun setCookiesAndSave(newCookies: String) {
        this.cookies = newCookies
        saveConfig()
        refreshAccount()
    }

    fun logout() {
        this.cookies = ""
        this.currentUser = null
        saveConfig()
    }

    // ==========================================
    // HTTP Connection Utilities
    // ==========================================

    private fun createConnection(url: String, method: String): HttpURLConnection {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        conn.setRequestProperty("Connection", "keep-alive")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Referer", "https://music.163.com")
        conn.setRequestProperty("Host", "music.163.com")
        conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)

        val cookieHeader = StringBuilder("appver=2.7.1.198277; os=pc;")
        if (cookies.isNotBlank()) {
            cookieHeader.append(" ").append(cookies)
        }
        conn.setRequestProperty("Cookie", cookieHeader.toString())

        return conn
    }

    data class HttpResponse(
        val code: Int,
        val body: String,
        val setCookies: List<String>
    ) {
        val json: JsonObject by lazy {
            try {
                JsonParser.parseString(body).asJsonObject
            } catch (e: Exception) {
                JsonObject()
            }
        }

        fun getMergedCookies(): String {
            val map = mutableMapOf<String, String>()
            for (header in setCookies) {
                val parts = header.split(";")[0].split("=", limit = 2)
                if (parts.size == 2) {
                    map[parts[0].trim()] = parts[1].trim()
                }
            }
            return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
    }

    fun post(baseUrl: String, path: String, params: Map<String, Any?>? = null): HttpResponse {
        val conn = createConnection(baseUrl + path, "POST")
        conn.doOutput = true
        conn.doInput = true

        if (!params.isNullOrEmpty()) {
            val sb = StringBuilder()
            params.entries.forEachIndexed { i, entry ->
                if (i > 0) sb.append("&")
                sb.append(URLEncoder.encode(entry.key, StandardCharsets.UTF_8))
                sb.append("=")
                sb.append(URLEncoder.encode(entry.value?.toString() ?: "", StandardCharsets.UTF_8))
            }
            conn.outputStream.use { os ->
                os.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
            }
        }

        val code = conn.responseCode
        val isStream = if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
        val body = isStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
        val setCookies = conn.headerFields["Set-Cookie"] ?: emptyList()

        conn.disconnect()
        return HttpResponse(code, body, setCookies)
    }

    fun postApi(path: String, params: Map<String, Any?>? = null): JsonObject {
        val resp = post(BASE_URL, path, params)
        return resp.json
    }

    // ==========================================
    // QR Code Login
    // ==========================================

    fun requestQRKey(): String {
        val resp = postApi("/api/login/qrcode/unikey", mapOf("type" to 1))
        return resp.get("unikey")?.asString ?: throw IllegalStateException("Failed to get unikey: $resp")
    }

    fun generateQRCodePng(unikey: String, size: Int = 200): ByteArray {
        val url = "https://music.163.com/login?codekey=$unikey"
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "utf-8",
            EncodeHintType.MARGIN to 1
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size, hints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val black = 0xFF18181B.toInt()
        val white = 0xFFFFFFFF.toInt()

        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) black else white)
            }
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    fun checkQRStatus(unikey: String): Pair<QRStatus, String?> {
        val resp = post(BASE_URL, "/api/login/qrcode/client/login", mapOf("key" to unikey, "type" to 1))
        val json = resp.json
        val code = json.get("code")?.asInt ?: -1

        return when (code) {
            801 -> QRStatus.WAITING to null
            802 -> QRStatus.CONFIRMING to null
            803 -> {
                val cookie = resp.getMergedCookies()
                QRStatus.SUCCESS to cookie
            }
            800 -> QRStatus.EXPIRED to null
            else -> QRStatus.ERROR to null
        }
    }

    // ==========================================
    // User Profile
    // ==========================================

    fun refreshAccount(): MusicUser? {
        if (cookies.isBlank()) {
            currentUser = null
            return null
        }
        return try {
            val accountJson = postApi("/api/w/nuser/account/get")
            if (!accountJson.has("profile") || accountJson.get("profile").isJsonNull) {
                currentUser = null
                return null
            }
            val userId = accountJson.getAsJsonObject("profile").get("userId").asLong
            val detailJson = postApi("/api/v1/user/detail/$userId")
            val profile = detailJson.getAsJsonObject("profile")
            val user = MusicUser(
                id = userId,
                name = profile.get("nickname")?.asString ?: "Unknown",
                avatarUrl = profile.get("avatarUrl")?.asString ?: "",
                signature = profile.get("signature")?.asString ?: "",
                vipType = profile.get("vipType")?.asInt ?: 0,
                level = detailJson.get("level")?.asInt ?: 0,
                listenSongs = detailJson.get("listenSongs")?.asLong ?: 0,
                playlistCount = profile.get("playlistCount")?.asInt ?: 0
            )
            currentUser = user
            saveConfig()
            user
        } catch (e: Exception) {
            System.err.println("[CloudMusicApi] Failed to refresh account: ${e.message}")
            currentUser = null
            null
        }
    }

    // ==========================================
    // Recommendations & Discovery
    // ==========================================

    fun parseTrack(json: JsonObject): MusicTrack {
        val id = json.get("id").asLong
        val name = json.get("name").asString
        val alias = when {
            json.has("alia") && json.getAsJsonArray("alia").size() > 0 -> json.getAsJsonArray("alia")[0].asString
            json.has("alias") && json.getAsJsonArray("alias").size() > 0 -> json.getAsJsonArray("alias")[0].asString
            else -> ""
        }

        val artistsArray = when {
            json.has("ar") -> json.getAsJsonArray("ar")
            json.has("artists") -> json.getAsJsonArray("artists")
            else -> JsonArray()
        }
        val artists = artistsArray.joinToString("/") { (it as JsonObject).get("name").asString }

        val albumObj = when {
            json.has("al") -> json.getAsJsonObject("al")
            json.has("album") -> json.getAsJsonObject("album")
            else -> JsonObject()
        }
        val albumName = albumObj.get("name")?.asString ?: ""
        val picUrl = albumObj.get("picUrl")?.asString ?: ""
        val durationMs = when {
            json.has("dt") -> json.get("dt").asLong
            json.has("duration") -> json.get("duration").asLong
            else -> 0L
        }
        val fee = json.get("fee")?.asInt ?: 0

        return MusicTrack(id, name, artists, albumName, durationMs, picUrl, alias, fee)
    }

    fun parsePlaylist(json: JsonObject): MusicPlaylist {
        val playlist = if (json.has("playlist") && json.get("playlist").isJsonObject) json.getAsJsonObject("playlist") else json
        val id = playlist.get("id")?.asLong ?: 0L
        val name = playlist.get("name")?.asString ?: "Unknown Playlist"
        val coverUrl = when {
            playlist.has("coverImgUrl") && !playlist.get("coverImgUrl").isJsonNull -> playlist.get("coverImgUrl").asString
            playlist.has("picUrl") && !playlist.get("picUrl").isJsonNull -> playlist.get("picUrl").asString
            else -> ""
        }
        val trackCount = playlist.get("trackCount")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val playCount = playlist.get("playCount")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        val creatorObj = if (playlist.has("creator") && playlist.get("creator").isJsonObject) playlist.getAsJsonObject("creator") else null
        val creatorName = creatorObj?.get("nickname")?.takeIf { !it.isJsonNull }?.asString ?: "NetEase Cloud Music"
        val creatorId = creatorObj?.get("userId")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        val desc = playlist.get("description")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val tags = if (playlist.has("tags") && playlist.get("tags").isJsonArray) {
            playlist.getAsJsonArray("tags").mapNotNull { if (it.isJsonPrimitive) it.asString else null }
        } else emptyList()

        return MusicPlaylist(id, name, coverUrl, trackCount, playCount, creatorName, creatorId, desc, tags)
    }

    fun fetchDailySongs(): List<MusicTrack> {
        val resp = postApi("/api/v3/discovery/recommend/songs")
        val songs = resp.getAsJsonObject("data")?.getAsJsonArray("dailySongs") ?: return emptyList()
        return songs.map { parseTrack(it.asJsonObject) }
    }

    fun fetchDailyPlaylists(): List<MusicPlaylist> {
        val resp = postApi("/api/v1/discovery/recommend/resource")
        val list = resp.getAsJsonArray("recommend") ?: return emptyList()
        return list.map { parsePlaylist(it.asJsonObject) }
    }

    fun fetchPersonalFm(): List<MusicTrack> {
        val resp = postApi("/api/v1/radio/get")
        val list = resp.getAsJsonArray("data") ?: return emptyList()
        return list.map { parseTrack(it.asJsonObject) }
    }

    fun fetchTopPlaylists(cat: String = "\u5168\u90E8", limit: Int = 30): List<MusicPlaylist> {
        val resp = postApi("/api/playlist/list", mapOf(
            "cat" to cat,
            "limit" to limit,
            "offset" to 0,
            "order" to "hot",
            "total" to true
        ))
        val playlists = resp.getAsJsonArray("playlists") ?: return emptyList()
        return playlists.map { parsePlaylist(it.asJsonObject) }
    }

    fun fetchRankLists(): List<MusicPlaylist> {
        val resp = try {
            postApi("/api/toplist/detail")
        } catch (_: Exception) {
            postApi("/api/toplist")
        }
        val list = resp.getAsJsonArray("list") ?: return emptyList()
        return list.mapNotNull {
            try {
                if (it.isJsonObject) parsePlaylist(it.asJsonObject) else null
            } catch (_: Exception) {
                null
            }
        }
    }

    // ==========================================
    // Playlists & Tracks
    // ==========================================

    fun fetchUserPlaylists(userId: Long): List<MusicPlaylist> {
        val resp = postApi("/api/user/playlist", mapOf(
            "uid" to userId,
            "limit" to 50,
            "offset" to 0,
            "includeVideo" to true
        ))
        val list = resp.getAsJsonArray("playlist") ?: return emptyList()
        return list.map { parsePlaylist(it.asJsonObject) }
    }

    fun fetchPlaylistDetail(playlistId: Long): Pair<MusicPlaylist, List<MusicTrack>> {
        val resp = postApi("/api/v6/playlist/detail", mapOf(
            "id" to playlistId,
            "n" to 100000
        ))
        val playlistObj = resp.getAsJsonObject("playlist")
        val playlist = parsePlaylist(playlistObj)
        val tracksArray = playlistObj.getAsJsonArray("tracks") ?: JsonArray()
        val tracks = tracksArray.map { parseTrack(it.asJsonObject) }
        return playlist to tracks
    }

    fun fetchSongAudioUrl(songId: Long, quality: String = "standard"): String? {
        val resp = post(PLAY_URL_BASE, "/api/song/enhance/player/url/v1", mapOf(
            "ids" to "[$songId]",
            "level" to quality,
            "encodeType" to "flac"
        ))
        val dataArray = resp.json.getAsJsonArray("data") ?: return null
        if (dataArray.isEmpty) return null
        val item = dataArray[0].asJsonObject
        val code = item.get("code")?.asInt ?: 0
        if (code != 200) return null
        val url = item.get("url")?.takeIf { !it.isJsonNull }?.asString
        return url
    }

    fun fetchLyric(songId: Long): LyricData {
        return try {
            val resp = postApi("/api/song/lyric", mapOf(
                "id" to songId,
                "lv" to 0,
                "tv" to 0
            ))
            val lrcText = resp.getAsJsonObject("lrc")?.get("lyric")?.asString ?: ""
            val tLrcText = resp.getAsJsonObject("tlyric")?.get("lyric")?.asString ?: ""

            val lrcMap = parseLrcText(lrcText)
            val tLrcMap = parseLrcText(tLrcText)

            val lines = lrcMap.map { (time, text) ->
                LyricLine(time, text, tLrcMap[time] ?: "")
            }.sortedBy { it.timeMs }

            LyricData(lines)
        } catch (e: Exception) {
            LyricData(emptyList())
        }
    }

    private fun parseLrcText(raw: String): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("[")) continue
            val endIdx = trimmed.indexOf(']')
            if (endIdx <= 1) continue
            val timePart = trimmed.substring(1, endIdx)
            val textPart = trimmed.substring(endIdx + 1).trim()
            val timeMs = parseLrcTime(timePart)
            if (timeMs >= 0) {
                map[timeMs] = textPart
            }
        }
        return map
    }

    private fun parseLrcTime(timeStr: String): Long {
        return try {
            val parts = timeStr.split(":")
            if (parts.size < 2) return -1
            val min = parts[0].toLong()
            val secParts = parts[1].split(".")
            val sec = secParts[0].toLong()
            val ms = if (secParts.size > 1) {
                val rawMs = secParts[1]
                if (rawMs.length == 2) rawMs.toLong() * 10 else rawMs.toLong()
            } else 0L
            min * 60000L + sec * 1000L + ms
        } catch (e: Exception) {
            -1L
        }
    }

    fun likeSong(songId: Long, like: Boolean): Boolean {
        return try {
            val resp = postApi("/api/radio/like", mapOf(
                "alg" to "itembased",
                "trackId" to songId,
                "like" to like,
                "time" to 3
            ))
            resp.get("code")?.asInt == 200
        } catch (e: Exception) {
            false
        }
    }

    fun searchTracks(keywords: String, limit: Int = 30): List<MusicTrack> {
        val resp = postApi("/api/cloudsearch/pc", mapOf(
            "s" to keywords,
            "type" to 1,
            "limit" to limit,
            "offset" to 0,
            "total" to true
        ))
        val songs = resp.getAsJsonObject("result")?.getAsJsonArray("songs") ?: return emptyList()
        return songs.map { parseTrack(it.asJsonObject) }
    }

    fun searchPlaylists(keywords: String, limit: Int = 30): List<MusicPlaylist> {
        val resp = postApi("/api/cloudsearch/pc", mapOf(
            "s" to keywords,
            "type" to 1000,
            "limit" to limit,
            "offset" to 0,
            "total" to true
        ))
        val playlists = resp.getAsJsonObject("result")?.getAsJsonArray("playlists") ?: return emptyList()
        return playlists.map { parsePlaylist(it.asJsonObject) }
    }
}

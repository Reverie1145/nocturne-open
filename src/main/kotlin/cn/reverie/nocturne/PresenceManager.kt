package cn.reverie.nocturne

import com.google.gson.Gson
import net.minecraft.client.Minecraft
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 签名心跳 + 在线名单：
 * 每 10 秒向 /api/heartbeat 发 HMAC 签名心跳（含玩家名/服务器），
 * 服务器校验时间戳/nonce/签名，失败 3 次吊销 token（客户端自毁）。
 * 心跳同时拉取在线列表供 NameTags 标注。
 */
object PresenceManager {

    private val gson = Gson()
    private var executor: Executors? = null
    private var scheduler: java.util.concurrent.ScheduledExecutorService? = null

    @Volatile
    private var token = ""

    @Volatile
    private var onlineNames: Set<String> = emptySet()

    @Volatile
    private var onlinePlayers: List<PresenceEntry> = emptyList()

    data class PresenceEntry(val name: String, val server: String, val account: String)

    fun isNocturneUser(playerName: String): Boolean = playerName in onlineNames

    fun onlineUsers(): List<PresenceEntry> = onlinePlayers

    fun start(tokenValue: String) {
        token = tokenValue
        if (scheduler != null) return
        val exec = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r).apply { name = "Nocturne-Presence"; isDaemon = true }
        }
        scheduler = exec
        exec.scheduleAtFixedRate({ tick() }, 2L, 10L, TimeUnit.SECONDS)
    }

    fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
        onlineNames = emptySet()
        onlinePlayers = emptyList()
    }

    private fun tick() {
        if (!NocturneAuth.isAuthenticated) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: run {
            onlineNames = emptySet()
            onlinePlayers = emptyList()
            return
        }
        if (mc.level == null) return

        try {
            // 1) 签名心跳（服务器同时刷新在线状态并校验会话）
            val (code, body) = NocturneAuth.signedRequest(
                "POST", "/api/heartbeat",
                mapOf(
                    "name" to player.gameProfile.name,
                    "server" to (mc.currentServer?.ip ?: "")
                )
            )
            if (code == 403) {
                val obj = try { gson.fromJson(body, Map::class.java) } catch (e: Throwable) { null }
                if (obj?.get("kick") == true) {
                    NocturneAuth.onServerKick((obj["error"] as? String) ?: "heartbeat rejected")
                    return
                }
            }

            // 2) 签名拉取在线名单
            val (code2, body2) = NocturneAuth.signedRequest("GET", "/api/presence")
            if (code2 == 403) {
                val obj = try { gson.fromJson(body2, Map::class.java) } catch (e: Throwable) { null }
                if (obj?.get("kick") == true) {
                    NocturneAuth.onServerKick((obj["error"] as? String) ?: "presence rejected")
                    return
                }
            }
            if (code2 == 200) {
                val obj = gson.fromJson(body2, Map::class.java)
                if (obj?.get("ok") == true) {
                    val list = (obj["players"] as? List<*>) ?: emptyList<Any>()
                    val entries = list.mapNotNull { p ->
                        (p as? Map<*, *>)?.let {
                            PresenceEntry(
                                it["name"] as? String ?: return@mapNotNull null,
                                it["server"] as? String ?: "",
                                it["account"] as? String ?: ""
                            )
                        }
                    }
                    onlinePlayers = entries
                    onlineNames = entries.map { it.name }.toSet()
                }
            }
        } catch (ignored: Throwable) {
            // 网络抖动：下次心跳重试，会话由 /api/verify 循环与看门狗兜底
        }
    }
}

package cn.reverie.nocturne

import cn.reverie.gui.mainmenu.LoginScreen
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.client.Minecraft
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Nocturne Account Authentication & Session Security
 * - Login issues token + session_key; subsequent requests are HMAC-SHA256 signed.
 * - Signatures cover serverTime|nonce|body for timestamp offset and replay prevention.
 * - Watchdog: triggers hard crash if client is navigated outside login screen without authentication.
 */
object NocturneAuth {

    const val API_BASE = "https://nocturne.reverieapi.asia"

    private val gson: Gson = Gson()
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build()

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var sessionKey: String = ""
        private set

    @Volatile
    var username: String = ""
        private set

    @Volatile
    var role: String = "user"
        private set

    @Volatile
    var ircCertFp: String = ""
        private set

    @Volatile
    var ircHost: String = "nocturne.reverieapi.asia"
        private set

    @Volatile
    var ircPort: Int = 6667
        private set

    @Volatile
    var ircChannel: String = "#nocturne"
        private set

    @Volatile
    private var serverOffsetMs = 0L

    @Volatile
    private var lastVerifyAt = 0L

    @Volatile
    var lastKickReason: String = ""
        private set

    /**
     * Development runs (`./gradlew runClient`) carry neither the native image
     * nor the packed class bodies, so the protection chain cannot come up
     * there. Release jars always run the full chain; only a dev classpath
     * short-circuits it. Everything else in this object is untouched.
     */
    internal val DEV_MODE: Boolean by lazy {
        try {
            net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment
        } catch (ignored: Throwable) {
            false
        }
    }

    val isAuthenticated: Boolean
        get() = DEV_MODE || !token.isNullOrEmpty()

    private val securityStarted = AtomicBoolean(false)

    /** Boot grace: client has early loading screens during construction; skip bypass checks for first 30s */
    private val bootGraceMs = 30_000L
    private val startMs = System.currentTimeMillis()

    /** Server-synchronized timestamp in seconds */
    fun serverTimeSec(): Long = (System.currentTimeMillis() + serverOffsetMs) / 1000

    fun hmac(data: String): String {
        // Session key is stored in native memory; signing is handled entirely in DLL
        return NativeGuard.hmacHex(data)
    }

    // ---------------- Security thread initialization ----------------

    fun startSecurity() {
        if (DEV_MODE) return
        NativeGuard.ensureInit()
        if (!securityStarted.compareAndSet(false, true)) return

        // Launcher session injection
        adoptLauncherSession()

        thread(name = "Nocturne-Watchdog", isDaemon = true) {
            while (true) {
                try {
                    watchdogTick()
                } catch (ignored: Throwable) {
                }
                try {
                    StringVault.flushAll() // drop decoded plaintext caches
                    Thread.sleep(4000L)
                } catch (e: InterruptedException) {
                    return@thread
                }
            }
        }

        thread(name = "Nocturne-Verify", isDaemon = true) {
            while (true) {
                if (isAuthenticated) {
                    try {
                        val (code, body) = signedRequest("POST", "/api/verify")
                        if (code == 200) {
                            lastVerifyAt = System.currentTimeMillis()
                            val obj = gson.fromJson(body, Map::class.java)
                            val st = (obj?.get("server_time") as? Double)?.toLong()
                            if (st != null) serverOffsetMs = st * 1000 - System.currentTimeMillis()
                        } else {
                            val obj = try { gson.fromJson(body, Map::class.java) } catch (e: Throwable) { null }
                            if (obj?.get("kick") == true) {
                                onServerKick((obj["error"] as? String) ?: "session rejected")
                            }
                        }
                    } catch (ignored: Throwable) {
                        // Tolerate network jitter, backed up by 120s watchdog window
                    }
                }
                try {
                    Thread.sleep(15_000L)
                } catch (e: InterruptedException) {
                    return@thread
                }
            }
        }
    }

    private fun watchdogTick() {
        if (System.currentTimeMillis() - startMs < bootGraceMs) return
        if (!NativeGuard.watchdogOk()) {
            hardCrash("Native guard violation")
        }
        val mc = Minecraft.getInstance() ?: return
        val screen = mc.gui.screen()
        val inGame = mc.level != null
        val onLogin = screen is LoginScreen

        // Bypassing authentication: entering client outside login screen without auth triggers hard crash
        if (!isAuthenticated && !onLogin && (screen != null || inGame)) {
            hardCrash("Session violation: entered client without authentication")
        }

        // Authenticated but server heartbeat lost for > 120s while in-game triggers hard crash
        if (isAuthenticated && inGame && lastVerifyAt != 0L &&
            System.currentTimeMillis() - lastVerifyAt > 120_000L
        ) {
            hardCrash("Session violation: server heartbeat lost")
        }
    }

    fun onServerKick(reason: String) {
        lastKickReason = reason
        invalidate()
        hardCrash("Kicked by Nocturne server: $reason")
    }

    /** Hard crash: immediately halts process */
    fun hardCrash(reason: String) {
        try {
            System.err.println("[Nocturne] FATAL: $reason")
            File(File(Minecraft.getInstance().gameDirectory, "nocturne"), "security_violation.log")
                .writeText("${java.util.Date()} $reason\n")
        } catch (ignored: Throwable) {
        }
        Runtime.getRuntime().halt(-1)
    }

    fun invalidate() {
        token = null
        sessionKey = ""
        if (!DEV_MODE) NativeGuard.setSessionKey(ByteArray(0)) // Clear native key
        username = ""
        role = "user"
        lastVerifyAt = 0
        IrcManager.stop()
        PresenceManager.stop()
    }

    // ---------------- Credential persistence ----------------

    private data class Credentials(val username: String = "", val password: String = "", val remember: Boolean = false)

    private val credFile: File
        get() = File(File(Minecraft.getInstance().gameDirectory, "nocturne"), "auth.json")

    private fun loadCredentials(): Credentials {
        return try {
            if (credFile.exists()) {
                gson.fromJson(credFile.readText(), Credentials::class.java) ?: Credentials()
            } else Credentials()
        } catch (e: Throwable) {
            Credentials()
        }
    }

    private fun saveCredentials(c: Credentials) {
        try {
            credFile.parentFile.mkdirs()
            credFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(c))
        } catch (ignored: Throwable) {
        }
    }

    /** Read remembered credentials for prefilling login screen */
    fun rememberedCredentials(): Triple<String, String, Boolean> {
        val c = loadCredentials()
        val pass = if (c.remember && c.password.isNotEmpty()) {
            try {
                String(Base64.getDecoder().decode(c.password))
            } catch (e: Throwable) {
                ""
            }
        } else ""
        return Triple(c.username, pass, c.remember)
    }

    fun persistCredentials(username: String, password: String, remember: Boolean) {
        saveCredentials(
            Credentials(
                username = username,
                password = if (remember) Base64.getEncoder().encodeToString(password.toByteArray()) else "",
                remember = remember
            )
        )
    }

    fun logout() {
        invalidate()
    }

    // ---------------- Launcher session injection ----------------

    /** Session file (written by launcher before launching client, deleted after single use) */
    private val launcherSessionFile: File
        get() = File(File(Minecraft.getInstance().gameDirectory, "nocturne"), "session.json")

    /**
     * Adopts session from launcher. Precedence:
     *  1. JVM property -Dnocturne.session=<base64(json)>
     *  2. Session file <gameDir>/nocturne/session.json
     */
    fun adoptLauncherSession(): Boolean {
        try {
            // 1. JVM property
            System.getProperty("nocturne.session")?.takeIf { it.isNotBlank() }?.let { b64 ->
                val raw = String(Base64.getMimeDecoder().decode(b64))
                return adoptSessionJson(raw)
            }
            // 2. Session file
            val f = launcherSessionFile
            if (f.exists() && f.isFile) {
                val raw = f.readText()
                val consumed = adoptSessionJson(raw)
                try {
                    f.delete() // One-time token deleted immediately
                } catch (ignored: Throwable) {
                }
                return consumed
            }
        } catch (ignored: Throwable) {
        }
        return false
    }

    private fun adoptSessionJson(raw: String): Boolean {
        try {
            val obj = gson.fromJson(raw, Map::class.java) ?: return false
            val tok = obj["token"] as? String ?: return false
            val key = obj["session_key"] as? String ?: return false
            if (tok.isBlank() || key.isBlank()) return false
            token = tok
            sessionKey = key
            NativeGuard.setSessionKey(key.toByteArray())
            username = (obj["username"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            role = (obj["role"] as? String)?.takeIf { it.isNotBlank() } ?: "user"
            lastVerifyAt = System.currentTimeMillis()
            onAuthenticated()
            return true
        } catch (t: Throwable) {
            return false
        }
    }

    // ---------------- HTTP ----------------

    private fun plainPost(path: String, body: Map<String, String>): String? {
        return try {
            val payload = gson.toJson(body)
            val builder = HttpRequest.newBuilder(URI.create(API_BASE + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
            ClientAttest.headers(path, payload).forEach { (k, v) -> builder.header(k, v) }
            client.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                HttpResponse.BodyHandlers.ofString()
            ).body()
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Signed request: X-Token / X-Ts / X-Nonce / X-Sign = HMAC(sessionKey, "ts|nonce|body")
     * Canonical string formatting and HMAC are handled in native layer.
     */
    fun signedRequest(method: String, path: String, bodyObj: Any? = null): Pair<Int, String> {
        val bodyStr = if (bodyObj != null) gson.toJson(bodyObj) else ""
        val ts = serverTimeSec()
        val nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 32)
        val sign = NativeGuard.signRequest(ts.toString(), nonce, bodyStr)
        val builder = HttpRequest.newBuilder(URI.create(API_BASE + path))
            .header("Content-Type", "application/json")
            .header("X-Token", token ?: "")
            .header("X-Ts", ts.toString())
            .header("X-Nonce", nonce)
            .header("X-Sign", sign)
            .timeout(Duration.ofSeconds(10))
        ClientAttest.headers(path, bodyStr).forEach { (k, v) -> builder.header(k, v) }
        when (method.uppercase()) {
            "GET" -> builder.GET()
            else -> builder.POST(HttpRequest.BodyPublishers.ofString(bodyStr))
        }
        val resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return resp.statusCode() to resp.body()
    }

    /** Downloads authenticated binary resources (e.g. IRC private images) with session signature. */
    fun signedRequestBytes(path: String): Pair<Int, ByteArray> {
        val bodyStr = ""
        val ts = serverTimeSec()
        val nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 32)
        val sign = NativeGuard.signRequest(ts.toString(), nonce, bodyStr)
        val builder = HttpRequest.newBuilder(URI.create(API_BASE + path))
            .header("Content-Type", "application/json")
            .header("X-Token", token ?: "")
            .header("X-Ts", ts.toString())
            .header("X-Nonce", nonce)
            .header("X-Sign", sign)
            .timeout(Duration.ofSeconds(10))
        ClientAttest.headers(path, bodyStr).forEach { (k, v) -> builder.header(k, v) }
        val response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray())
        return response.statusCode() to response.body()
    }

    data class AuthResult(val ok: Boolean, val error: String = "")

    fun loginAsync(user: String, pass: String, cb: (AuthResult) -> Unit) {
        if (DEV_MODE) {
            username = user
            cb(AuthResult(true))
            return
        }
        Thread {
            val raw = plainPost(
                "/api/login",
                mapOf("username" to user, "password" to pass, "hwid" to HWID.value())
            )
            val result = try {
                val obj = gson.fromJson(raw, Map::class.java)
                if (obj?.get("ok") == true) {
                    token = obj["token"] as? String
                    sessionKey = obj["session_key"] as? String ?: ""
                    NativeGuard.setSessionKey(sessionKey.toByteArray()) // Store key in native memory
                    username = (obj["username"] as? String) ?: user
                    role = (obj["role"] as? String) ?: "user"
                    val st = (obj["server_time"] as? Double)?.toLong() ?: 0L
                    if (st > 0) serverOffsetMs = st * 1000 - System.currentTimeMillis()
                    val irc = obj["irc"] as? Map<*, *>
                    ircCertFp = (irc?.get("cert_fp") as? String) ?: ""
                    (irc?.get("host") as? String)?.takeIf { it.isNotBlank() }?.let { ircHost = it }
                    (irc?.get("port") as? Double)?.toInt()?.takeIf { it > 0 }?.let { ircPort = it }
                    (irc?.get("channel") as? String)?.takeIf { it.isNotBlank() }?.let { ircChannel = it }
                    lastVerifyAt = System.currentTimeMillis()
                    onAuthenticated()
                    AuthResult(true)
                } else {
                    AuthResult(false, (obj?.get("error") as? String) ?: "Login failed")
                }
            } catch (e: Throwable) {
                AuthResult(false, "Unable to connect to authentication server")
            }
            cb(result)
        }.apply { name = "Nocturne-Auth" }.start()
    }

    fun registerAsync(user: String, pass: String, inviteCode: String, cb: (AuthResult) -> Unit) {
        Thread {
            val hs = ClientAttest.handshake()
            if (!hs.ok) {
                cb(AuthResult(false, hs.error.ifBlank { "Unable to connect to authentication server" }))
                return@Thread
            }
            val pow = ClientAttest.solvePow(hs.challenge, hs.bits)
            if (pow == null) {
                cb(AuthResult(false, "Registration verification timed out, please try again"))
                return@Thread
            }
            val raw = plainPost(
                "/api/register",
                mapOf(
                    "username" to user, "password" to pass, "invite_code" to inviteCode,
                    "challenge" to hs.challenge, "pow" to pow, "hwid" to HWID.value()
                )
            )
            val result = try {
                val obj = gson.fromJson(raw, Map::class.java)
                if (obj?.get("ok") == true) AuthResult(true)
                else AuthResult(false, (obj?.get("error") as? String) ?: "Registration failed")
            } catch (e: Throwable) {
                AuthResult(false, "Unable to connect to authentication server")
            }
            cb(result)
        }.apply { name = "Nocturne-Reg" }.start()
    }

    private fun onAuthenticated() {
        val tok = token ?: return
        IrcManager.start(tok, username)
        PresenceManager.start(tok)
    }
}

package cn.reverie.app.account

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

object MicrosoftAuth {

    const val CLIENT_ID = "000000004c12ae6f"
    const val DEFAULT_VERIFICATION_URI = "https://www.microsoft.com/link"
    private const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
    private const val DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf"
    private const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"

    private fun describeXblError(body: String, httpStatus: Int): String {
        val errJson = try { JsonParser.parseString(body).asJsonObject } catch (_: Exception) { null }
        val xErr = errJson?.get("XErr")?.asLong ?: 0L
        return when (xErr) {
            2148916233L -> "This Microsoft account has no Xbox profile. Please visit xbox.com or the Xbox app to create an Xbox account, then try again."
            2148916235L -> "Xbox Live is not available in your country/region."
            2148916238L -> "This is a child account; parental permission for online gaming is required in family settings."
            2148916227L -> "This account has been banned from Xbox Live."
            else -> "Xbox Live authentication failed (HTTP $httpStatus, XErr: $xErr)"
        }
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    data class DeviceCodeResult(
        val userCode: String,
        val deviceCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int
    )

    class AuthSession(
        var deviceCodeResult: DeviceCodeResult,
        val future: CompletableFuture<Account>,
        private val cancelled: AtomicBoolean = AtomicBoolean(false)
    ) {
        fun cancel() {
            cancelled.set(true)
            future.cancel(true)
        }

        fun isCancelled(): Boolean = cancelled.get()
    }

    fun startDeviceCodeFlow(
        onDeviceCode: (DeviceCodeResult) -> Unit,
        onStatusUpdate: (String) -> Unit = {}
    ): AuthSession {
        val future = CompletableFuture<Account>()
        val cancelled = AtomicBoolean(false)
        val initialResult = DeviceCodeResult("", "", DEFAULT_VERIFICATION_URI, 900, 5)
        val session = AuthSession(initialResult, future, cancelled)

        Thread {
            try {
                onStatusUpdate("Requesting device code from Microsoft...")
                val deviceCode = requestDeviceCode()
                session.deviceCodeResult = deviceCode
                onDeviceCode(deviceCode)

                pollForToken(deviceCode, session, onStatusUpdate)
            } catch (e: Throwable) {
                if (!cancelled.get()) {
                    future.completeExceptionally(e)
                }
            }
        }.apply {
            isDaemon = true
            name = "Nocturne-MSA-AuthThread"
            start()
        }

        return session
    }

    private fun requestDeviceCode(): DeviceCodeResult {
        val form = "client_id=$CLIENT_ID&scope=${URLEncoder.encode(SCOPE, StandardCharsets.UTF_8)}&response_type=device_code"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(DEVICE_CODE_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .timeout(Duration.ofSeconds(20))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Failed to request device code (HTTP ${response.statusCode()}): ${response.body()}")
        }

        val json = JsonParser.parseString(response.body()).asJsonObject
        return DeviceCodeResult(
            userCode = json.get("user_code").asString,
            deviceCode = json.get("device_code").asString,
            verificationUri = json.get("verification_uri")?.asString ?: DEFAULT_VERIFICATION_URI,
            expiresIn = json.get("expires_in")?.asInt ?: 900,
            interval = (json.get("interval")?.asInt ?: 5).coerceAtLeast(3)
        )
    }

    private fun pollForToken(
        deviceCode: DeviceCodeResult,
        session: AuthSession,
        onStatusUpdate: (String) -> Unit
    ) {
        val expireTime = System.currentTimeMillis() + deviceCode.expiresIn * 1000L
        val intervalMs = (deviceCode.interval * 1000L).coerceAtLeast(3000L)

        while (System.currentTimeMillis() < expireTime) {
            if (session.isCancelled()) return

            Thread.sleep(intervalMs)
            if (session.isCancelled()) return

            val form = "client_id=$CLIENT_ID" +
                    "&grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                    "&device_code=${URLEncoder.encode(deviceCode.deviceCode, StandardCharsets.UTF_8)}"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .timeout(Duration.ofSeconds(20))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            val json = try {
                JsonParser.parseString(response.body()).asJsonObject
            } catch (_: Exception) {
                null
            }

            if (json != null) {
                if (json.has("access_token")) {
                    val msAccessToken = json.get("access_token").asString
                    val msRefreshToken = json.get("refresh_token")?.asString ?: ""

                    onStatusUpdate("Authenticating with Xbox Live...")
                    val account = completeMinecraftLogin(msAccessToken, msRefreshToken, onStatusUpdate)
                    session.future.complete(account)
                    return
                }

                if (json.has("error")) {
                    val error = json.get("error").asString
                    when (error) {
                        "authorization_pending" -> {
                            onStatusUpdate("Waiting for authorization in browser...")
                        }
                        "slow_down" -> {
                            onStatusUpdate("Request rate limit reached, retrying...")
                            Thread.sleep(3000L)
                        }
                        "expired_token" -> {
                            throw IllegalStateException("Device code expired, please try again")
                        }
                        "authorization_declined" -> {
                            throw IllegalStateException("Authorization declined in browser")
                        }
                        else -> {
                            val desc = json.get("error_description")?.asString ?: error
                            throw IllegalStateException("Microsoft authorization failed: $desc")
                        }
                    }
                }
            }
        }

        throw IllegalStateException("Authorization timed out, please try again")
    }

    fun refreshAccount(account: Account): CompletableFuture<Account> {
        val future = CompletableFuture<Account>()
        if (account.refreshToken.isBlank()) {
            future.completeExceptionally(IllegalStateException("No refresh token available for this account, please re-login"))
            return future
        }

        Thread {
            try {
                val clientId = account.msClientId.ifBlank { CLIENT_ID }
                val scope = account.msScope.ifBlank { SCOPE }
                val tokens = refreshMsTokens(account.refreshToken, clientId, scope)
                val newRefreshToken = tokens.second.ifBlank { account.refreshToken }

                val updatedAccount = completeMinecraftLogin(tokens.first, newRefreshToken, clientId, scope) { _ -> }
                account.accessToken = updatedAccount.accessToken
                account.refreshToken = updatedAccount.refreshToken
                account.name = updatedAccount.name
                account.uuid = updatedAccount.uuid
                account.xuid = updatedAccount.xuid
                account.msClientId = clientId
                account.msScope = scope
                account.lastUsedAt = System.currentTimeMillis()

                future.complete(account)
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }.apply {
            isDaemon = true
            name = "Nocturne-MSA-RefreshThread"
            start()
        }

        return future
    }

    fun completeMinecraftLogin(
        msAccessToken: String,
        msRefreshToken: String,
        onStatusUpdate: (String) -> Unit
    ): Account = completeMinecraftLogin(msAccessToken, msRefreshToken, CLIENT_ID, SCOPE, onStatusUpdate)

    /**
     * Microsoft access token -> Xbox Live -> XSTS -> Minecraft services -> profile.
     * [msClientId] / [msScope] describe the OAuth app the token came from; they are stored on the account so the
     * refresh token is later redeemed against the same app.
     */
    fun completeMinecraftLogin(
        msAccessToken: String,
        msRefreshToken: String,
        msClientId: String,
        msScope: String,
        onStatusUpdate: (String) -> Unit
    ): Account {

        onStatusUpdate("Connecting to Xbox Live...")
        // The legacy "MBI_SSL" app (refresh token import) sends the raw token; the other apps need the "d=" prefix.
        val ticket = when {
            msClientId == LEGACY_TOKEN_CLIENT_ID -> msAccessToken.removePrefix("d=")
            msAccessToken.startsWith("d=") -> msAccessToken
            else -> "d=$msAccessToken"
        }
        val xblPayload = JsonObject().apply {
            val props = JsonObject().apply {
                addProperty("AuthMethod", "RPS")
                addProperty("SiteName", "user.auth.xboxlive.com")
                addProperty("RpsTicket", ticket)
            }
            add("Properties", props)
            addProperty("RelyingParty", "http://auth.xboxlive.com")
            addProperty("TokenType", "JWT")
        }

        val xblReq = HttpRequest.newBuilder()
            .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(xblPayload.toString()))
            .timeout(Duration.ofSeconds(20))
            .build()

        val xblRes = httpClient.send(xblReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (xblRes.statusCode() !in 200..299) {
            throw IllegalStateException(describeXblError(xblRes.body(), xblRes.statusCode()))
        }

        val xblJson = JsonParser.parseString(xblRes.body()).asJsonObject
        val xblToken = xblJson.get("Token").asString
        val userHash = xblJson.getAsJsonObject("DisplayClaims")
            .getAsJsonArray("xui").get(0).asJsonObject.get("uhs").asString

        onStatusUpdate("Acquiring XSTS security token...")
        val xstsPayload = JsonObject().apply {
            val props = JsonObject().apply {
                addProperty("SandboxId", "RETAIL")
                val ut = JsonArray()
                ut.add(xblToken)
                add("UserTokens", ut)
            }
            add("Properties", props)
            addProperty("RelyingParty", "rp://api.minecraftservices.com/")
            addProperty("TokenType", "JWT")
        }

        val xstsReq = HttpRequest.newBuilder()
            .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(xstsPayload.toString()))
            .timeout(Duration.ofSeconds(20))
            .build()

        val xstsRes = httpClient.send(xstsReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (xstsRes.statusCode() !in 200..299) {
            val errJson = try { JsonParser.parseString(xstsRes.body()).asJsonObject } catch (_: Exception) { null }
            val xErr = errJson?.get("XErr")?.asLong ?: 0L
            val msg = when (xErr) {
                2148916233L -> "This Microsoft account has no Xbox profile. Please visit xbox.com or the Xbox app to create an Xbox account, then try again."
                2148916235L -> "Xbox Live is not available in your country/region."
                2148916238L -> "This is a child account; parental permission for online gaming is required in family settings."
                2148916227L -> "This account has been banned from Xbox Live."
                else -> "XSTS authorization failed (HTTP ${xstsRes.statusCode()}, XErr: $xErr)"
            }
            throw IllegalStateException(msg)
        }

        val xstsJson = JsonParser.parseString(xstsRes.body()).asJsonObject
        val xstsToken = xstsJson.get("Token").asString

        onStatusUpdate("Signing in to Minecraft services...")
        val mcPayload = JsonObject().apply {
            addProperty("identityToken", "XBL3.0 x=$userHash;$xstsToken")
        }

        val mcReq = HttpRequest.newBuilder()
            .uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mcPayload.toString()))
            .timeout(Duration.ofSeconds(20))
            .build()

        val mcRes = httpClient.send(mcReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (mcRes.statusCode() == 401 || mcRes.statusCode() == 403) {
            throw IllegalStateException("Minecraft service rejected this account, please verify you own Minecraft Java Edition")
        }
        if (mcRes.statusCode() !in 200..299) {
            throw IllegalStateException("Minecraft authentication failed (HTTP ${mcRes.statusCode()})")
        }

        val mcJson = JsonParser.parseString(mcRes.body()).asJsonObject
        val mcAccessToken = mcJson.get("access_token").asString

        onStatusUpdate("Fetching player profile...")
        val profileReq = HttpRequest.newBuilder()
            .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
            .header("Authorization", "Bearer $mcAccessToken")
            .header("Accept", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(20))
            .build()

        val profileRes = httpClient.send(profileReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (profileRes.statusCode() == 404) {
            throw IllegalStateException("This Microsoft account does not own Minecraft Java Edition")
        }
        if (profileRes.statusCode() == 401 || profileRes.statusCode() == 403) {
            throw IllegalStateException("Access denied fetching profile, please re-login")
        }
        if (profileRes.statusCode() !in 200..299) {
            throw IllegalStateException("Failed to fetch Minecraft profile (HTTP ${profileRes.statusCode()})")
        }

        val profileJson = JsonParser.parseString(profileRes.body()).asJsonObject
        val ign = profileJson.get("name").asString
        val undashedUuid = profileJson.get("id").asString
        val playerUuid = parseUndashedUuid(undashedUuid)

        onStatusUpdate("Successfully signed in: $ign")

        return Account(
            name = ign,
            uuid = playerUuid,
            type = AccountType.MICROSOFT,
            accessToken = mcAccessToken,
            refreshToken = msRefreshToken,
            xuid = userHash,
            lastUsedAt = System.currentTimeMillis(),
            msClientId = msClientId,
            msScope = msScope
        )
    }

    fun validateDirectToken(bearerToken: String): CompletableFuture<Account> {
        val future = CompletableFuture<Account>()
        var cleanToken = bearerToken.trim()
        while (cleanToken.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true)) {
            cleanToken = cleanToken.substring(7).trim()
        }

        if (cleanToken.isEmpty()) {
            future.completeExceptionally(IllegalStateException("Token cannot be empty"))
            return future
        }

        val clean = cleanToken
        Thread {
            try {
                val profileReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                    .header("Authorization", "Bearer $clean")
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .build()

                val profileRes = httpClient.send(profileReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                if (profileRes.statusCode() == 401 || profileRes.statusCode() == 403) {
                    throw IllegalStateException("Token invalid or expired (HTTP ${profileRes.statusCode()})")
                }
                if (profileRes.statusCode() !in 200..299) {
                    throw IllegalStateException("Failed to fetch Minecraft profile (HTTP ${profileRes.statusCode()})")
                }

                val profileJson = JsonParser.parseString(profileRes.body()).asJsonObject
                val ign = profileJson.get("name").asString
                val undashedUuid = profileJson.get("id").asString
                val playerUuid = parseUndashedUuid(undashedUuid)

                val account = Account(
                    name = ign,
                    uuid = playerUuid,
                    type = AccountType.SESSION,
                    accessToken = cleanToken,
                    lastUsedAt = System.currentTimeMillis()
                )
                future.complete(account)
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }.apply {
            isDaemon = true
            name = "Nocturne-Token-Validator"
            start()
        }

        return future
    }

    private fun describeRefreshError(body: String, httpStatus: Int): String {
        val json = try { JsonParser.parseString(body).asJsonObject } catch (_: Exception) { null }
        val error = json?.get("error")?.asString
        if (error == "invalid_grant") return "Login credentials expired, please re-add this account"
        val desc = json?.get("error_description")?.asString
        return when {
            !desc.isNullOrBlank() -> "Token refresh failed: $desc"
            !error.isNullOrBlank() -> "Token refresh failed: $error"
            else -> "Token refresh failed (HTTP $httpStatus)"
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Flows ported from OpenMyau-Plus (ksyz AccountManager)
    //
    //  * Browser sign-in: OAuth authorization-code flow, redirect to http://localhost:25575/callback
    //  * Token import:    Microsoft refresh token (legacy "MBI_SSL" app) or a Minecraft access token
    //  * Session import:  "ign:uuid:token" or a bare Minecraft access token
    //  * Cookie sign-in:  login.live.com cookies replayed against the authorize endpoint
    //  * Login:           try the stored Minecraft token first, refresh only when it no longer works
    // ---------------------------------------------------------------------------------------------

    /** The Azure app OpenMyau uses for browser and cookie sign-in. */
    const val AZURE_CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2"
    const val AZURE_SCOPE = "XboxLive.signin XboxLive.offline_access"

    /** The app a pasted Microsoft refresh token is redeemed against. */
    const val LEGACY_TOKEN_CLIENT_ID = "00000000402b5328"
    const val LEGACY_TOKEN_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"

    /** 25565 + 10, the same callback port OpenMyau listens on. */
    const val CALLBACK_PORT = 25575
    private const val CALLBACK_URI = "http://localhost:$CALLBACK_PORT/callback"
    private const val AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf"
    private const val PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"
    private const val BROWSER_TIMEOUT_MS = 5 * 60 * 1000L

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun startDaemon(threadName: String, body: () -> Unit) {
        Thread { body() }.apply {
            isDaemon = true
            name = threadName
            start()
        }
    }

    fun getMSAuthLink(state: String, clientId: String = AZURE_CLIENT_ID, scope: String = AZURE_SCOPE): String =
        "$AUTHORIZE_URL?client_id=${enc(clientId)}&response_type=code&redirect_uri=${enc(CALLBACK_URI)}" +
            "&scope=${enc(scope)}&state=${enc(state)}&prompt=select_account"

    /** POSTs a form to the Microsoft token endpoint and returns (access_token, refresh_token). */
    private fun tokenRequest(form: String): Pair<String, String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .timeout(Duration.ofSeconds(30))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        val json = try { JsonParser.parseString(response.body()).asJsonObject } catch (_: Exception) { null }
        val access = json?.get("access_token")?.takeIf { !it.isJsonNull }?.asString
        if (access.isNullOrBlank()) {
            throw IllegalStateException(describeRefreshError(response.body(), response.statusCode()))
        }
        val refresh = json?.get("refresh_token")?.takeIf { !it.isJsonNull }?.asString ?: ""
        return access to refresh
    }

    /** Authorization code -> tokens. The redirect URI has to match the one the code was issued for. */
    fun exchangeAuthCode(code: String, clientId: String = AZURE_CLIENT_ID): Pair<String, String> {
        val tokens = tokenRequest(
            "client_id=${enc(clientId)}&grant_type=authorization_code&code=${enc(code)}&redirect_uri=${enc(CALLBACK_URI)}"
        )
        if (tokens.second.isBlank()) throw IllegalStateException("There was no Microsoft refresh token present.")
        return tokens
    }

    /** Refresh token -> tokens. MBI_SSL apps take a scope, the others the redirect URI (as in OpenMyau). */
    fun refreshMsTokens(refreshToken: String, clientId: String, scope: String): Pair<String, String> {
        val tail = if (scope.contains("MBI_SSL")) "&scope=${enc(scope)}" else "&redirect_uri=${enc(CALLBACK_URI)}"
        return tokenRequest(
            "client_id=${enc(clientId)}&grant_type=refresh_token&refresh_token=${enc(refreshToken)}$tail"
        )
    }

    // ---- Browser sign-in ------------------------------------------------------------------------

    class BrowserSession(val link: String, val future: CompletableFuture<Account>) {
        private val cancelled = AtomicBoolean(false)
        @Volatile internal var server: java.net.ServerSocket? = null

        fun isCancelled(): Boolean = cancelled.get()

        fun cancel() {
            cancelled.set(true)
            try { server?.close() } catch (_: Exception) {}
            future.cancel(true)
        }
    }

    /**
     * Starts a local callback listener and returns the login [BrowserSession.link] to open in a browser.
     * The listener uses a plain [java.net.ServerSocket], so it does not depend on the jdk.httpserver module.
     */
    fun startBrowserFlow(onStatusUpdate: (String) -> Unit = {}): BrowserSession {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val rnd = java.security.SecureRandom()
        val state = String(CharArray(8) { chars[rnd.nextInt(chars.length)] })
        val session = BrowserSession(getMSAuthLink(state), CompletableFuture())

        startDaemon("Nocturne-MSA-Browser") {
            try {
                onStatusUpdate("Waiting for you to sign in in the browser...")
                val code = awaitCallbackCode(state, session)
                onStatusUpdate("Acquiring Microsoft access tokens...")
                val tokens = exchangeAuthCode(code)
                val account = completeMinecraftLogin(tokens.first, tokens.second, AZURE_CLIENT_ID, AZURE_SCOPE, onStatusUpdate)
                session.future.complete(account)
            } catch (e: Throwable) {
                if (!session.isCancelled()) session.future.completeExceptionally(e)
            }
        }
        return session
    }

    private fun awaitCallbackCode(state: String, session: BrowserSession): String {
        val server = try {
            java.net.ServerSocket(CALLBACK_PORT, 8, java.net.InetAddress.getLoopbackAddress())
        } catch (e: java.net.BindException) {
            throw IllegalStateException("Port $CALLBACK_PORT is already in use; close the other sign-in window and retry")
        }
        session.server = server
        server.soTimeout = 1000
        val deadline = System.currentTimeMillis() + BROWSER_TIMEOUT_MS
        try {
            while (!session.isCancelled()) {
                if (System.currentTimeMillis() > deadline) throw IllegalStateException("Sign-in timed out, please try again")
                val socket = try {
                    server.accept()
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (e: java.net.SocketException) {
                    if (session.isCancelled()) break
                    throw e
                }
                val outcome = socket.use { handleCallback(it, state) } ?: continue
                outcome.error?.let { throw IllegalStateException(it) }
                outcome.code?.let { return it }
            }
        } finally {
            try { server.close() } catch (_: Exception) {}
        }
        throw java.util.concurrent.CancellationException("Microsoft sign-in was cancelled")
    }

    private class CallbackOutcome(val code: String?, val error: String?)

    private fun handleCallback(socket: java.net.Socket, state: String): CallbackOutcome? {
        socket.soTimeout = 10_000
        val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
        val requestLine = reader.readLine() ?: return null
        // Drain the headers so the browser does not see a reset connection.
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
        val target = requestLine.split(' ').getOrNull(1) ?: return null
        val out = socket.getOutputStream()
        if (!target.startsWith("/callback")) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            return null
        }

        val query = parseQueryParams(target.substringAfter('?', ""))
        val outcome = when {
            query["state"] != state ->
                CallbackOutcome(null, "State mismatch! Expected '$state' but got '${query["state"]}'.")
            !query["code"].isNullOrBlank() -> CallbackOutcome(query["code"], null)
            query.containsKey("error") ->
                CallbackOutcome(null, "${query["error"]}: ${query["error_description"]}")
            else -> CallbackOutcome(null, "There was no auth code or error description present.")
        }

        val ok = outcome.code != null
        val body = """<!doctype html><html><head><meta charset="utf-8"><title>Nocturne</title>
            |<style>body{margin:0;height:100vh;display:grid;place-items:center;background:#0c0b11;color:#f4eef1;font-family:Segoe UI,Arial,sans-serif}
            |.c{text-align:center}h1{font-size:22px;font-weight:600;margin:0 0 8px}p{color:#b9aeb6;margin:0}</style></head>
            |<body><div class="c"><h1>${if (ok) "Signed in" else "Sign-in failed"}</h1>
            |<p>${if (ok) "You can close this tab and return to Nocturne." else "Return to Nocturne for details."}</p></div></body></html>""".trimMargin()
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                .toByteArray()
        )
        out.write(bytes)
        out.flush()
        return outcome
    }

    // ---- Profile ----------------------------------------------------------------------------------

    private fun fetchProfile(mcToken: String): Pair<String, UUID> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(PROFILE_URL))
            .header("Authorization", "Bearer $mcToken")
            .header("Accept", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(20))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw IllegalStateException("Token invalid or expired (HTTP ${response.statusCode()})")
        }
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Failed to fetch Minecraft profile (HTTP ${response.statusCode()})")
        }
        val json = JsonParser.parseString(response.body()).asJsonObject
        val id = json.get("id")?.asString
        val name = json.get("name")?.asString
        if (id.isNullOrBlank() || name.isNullOrBlank()) throw IllegalStateException("There was no profile present")
        return name to parseUndashedUuid(id)
    }

    /**
     * True when the account's stored Minecraft token still gets a profile. Never throws; on success the
     * account's name and uuid are refreshed from the profile (OpenMyau updates the username the same way).
     */
    fun validateStoredToken(account: Account): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        if (account.accessToken.isBlank() || account.accessToken == "0") {
            future.complete(false)
            return future
        }
        startDaemon("Nocturne-MSA-Validate") {
            try {
                val profile = fetchProfile(account.accessToken)
                account.name = profile.first
                account.uuid = profile.second
                future.complete(true)
            } catch (_: Throwable) {
                future.complete(false)
            }
        }
        return future
    }

    // ---- Token / session import -------------------------------------------------------------------

    private fun cleanBearer(raw: String): String {
        var token = raw.trim()
        while (token.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true)) token = token.substring(7).trim()
        return token
    }

    /**
     * "Add Token": tries the text as a Minecraft access token first; when that is rejected it is treated as a
     * Microsoft refresh token and walked through the full chain (like OpenMyau's GuiAddToken).
     */
    fun importToken(raw: String, onStatusUpdate: (String) -> Unit = {}): CompletableFuture<Account> {
        val future = CompletableFuture<Account>()
        val token = cleanBearer(raw)
        if (token.isEmpty()) {
            future.completeExceptionally(IllegalStateException("Token cannot be empty"))
            return future
        }
        startDaemon("Nocturne-Token-Import") {
            try {
                val direct = try { fetchProfile(token) } catch (_: Throwable) { null }
                if (direct != null) {
                    future.complete(
                        Account(name = direct.first, uuid = direct.second, type = AccountType.SESSION, accessToken = token)
                    )
                    return@startDaemon
                }
                onStatusUpdate("Refreshing Microsoft access tokens...")
                val tokens = refreshMsTokens(token, LEGACY_TOKEN_CLIENT_ID, LEGACY_TOKEN_SCOPE)
                val account = completeMinecraftLogin(
                    tokens.first, tokens.second.ifBlank { token }, LEGACY_TOKEN_CLIENT_ID, LEGACY_TOKEN_SCOPE, onStatusUpdate
                )
                future.complete(account)
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /** "Session": `ign:uuid:token` is used as is (no request), anything else is looked up as a bearer token. */
    fun importSession(raw: String): CompletableFuture<Account> {
        val parts = raw.trim().split(':', limit = 3)
        if (parts.size == 3 && parts.all { it.isNotBlank() }) {
            val account = Account(
                name = parts[0].trim(),
                uuid = parseUndashedUuid(parts[1].trim()),
                type = AccountType.SESSION,
                accessToken = parts[2].trim()
            )
            return CompletableFuture.completedFuture(account)
        }
        return validateDirectToken(raw)
    }

    // ---- Cookie sign-in ---------------------------------------------------------------------------

    /**
     * Cookie login: the cookies (Netscape cookies.txt, JSON export or a Cookie header) are replayed against the
     * authorize endpoint; if the Microsoft session is still valid it redirects to the callback URI with a code,
     * which is then redeemed through the regular chain.
     */
    fun loginWithCookies(
        cookieContent: String,
        onStatusUpdate: (String) -> Unit = {}
    ): CompletableFuture<Account> {
        val future = CompletableFuture<Account>()
        val cookies = CookieParser.parse(cookieContent)
        if (cookies.isEmpty()) {
            future.completeExceptionally(IllegalStateException("No cookies were found in that input"))
            return future
        }

        startDaemon("Nocturne-Cookie-AuthThread") {
            try {
                onStatusUpdate("Authenticating with cookies...")
                val code = acquireMSAuthCodeFromCookies(cookies)
                onStatusUpdate("Acquiring Microsoft access tokens...")
                val tokens = exchangeAuthCode(code, AZURE_CLIENT_ID)
                val account = completeMinecraftLogin(tokens.first, tokens.second, AZURE_CLIENT_ID, AZURE_SCOPE, onStatusUpdate)
                future.complete(account.copy(type = AccountType.COOKIE))
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    private fun acquireMSAuthCodeFromCookies(cookies: Map<String, String>): String {
        var currentUrl = "$AUTHORIZE_URL?client_id=${enc(AZURE_CLIENT_ID)}&response_type=code" +
            "&redirect_uri=${enc(CALLBACK_URI)}&scope=${enc(AZURE_SCOPE)}&state=cookielogin"

        // Carry (and accumulate) cookies ourselves across the redirect hops.
        val jar = LinkedHashMap(cookies)
        val noRedirectClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

        fun codeFrom(params: Map<String, String>): String? {
            val code = params["code"]
            if (!code.isNullOrBlank()) return code
            if (params.containsKey("error")) {
                throw IllegalStateException("${params["error"]}: ${params["error_description"] ?: ""}")
            }
            return null
        }

        for (hop in 0 until 15) {
            val req = HttpRequest.newBuilder()
                .uri(URI.create(currentUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml")
                .header("Cookie", jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build()

            val res = noRedirectClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            val status = res.statusCode()

            res.headers().allValues("Set-Cookie").forEach { header ->
                val kv = header.substringBefore(';').split('=', limit = 2)
                if (kv.size == 2 && kv[0].isNotBlank() && kv[1].isNotBlank()) jar[kv[0].trim()] = kv[1].trim()
            }

            if (status in 300..399) {
                val loc = res.headers().firstValue("Location").orElse(null)
                if (loc.isNullOrBlank()) throw IllegalStateException("Received a redirect without a location")
                val resolved = URI.create(currentUrl).resolve(loc.trim())
                if (resolved.toString().startsWith(CALLBACK_URI)) {
                    return codeFrom(parseQueryParams(resolved.rawQuery ?: ""))
                        ?: throw IllegalStateException("The callback did not contain an auth code")
                }
                currentUrl = resolved.toString()
                continue
            }

            // Not a redirect: usually the login page (cookies missing or expired), but some SSO responses embed the
            // callback in an auto-submitting form on a 200 page, so look for it in the body before giving up.
            val body = res.body()
            val idx = body.indexOf(CALLBACK_URI)
            if (idx >= 0) {
                var end = idx
                while (end < body.length && "\"'< >\\\r\n\t".indexOf(body[end]) < 0) end++
                val candidate = body.substring(idx, end).replace("&amp;", "&")
                try {
                    codeFrom(parseQueryParams(URI.create(candidate).rawQuery ?: ""))?.let { return it }
                } catch (_: IllegalArgumentException) {
                }
            }
            throw IllegalStateException("Cookies are invalid or expired (interaction required, HTTP $status)")
        }

        throw IllegalStateException("Too many redirects while authenticating with cookies")
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (pair in rawQuery.split('&')) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = java.net.URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
                val value = java.net.URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                map[key] = value
            }
        }
        return map
    }

    private fun parseUndashedUuid(raw: String): UUID {        val s = raw.replace("-", "")
        return if (s.length == 32) {
            val formatted = s.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})".toRegex(),
                "$1-$2-$3-$4-$5"
            )
            try {
                UUID.fromString(formatted)
            } catch (_: Exception) {
                UUID.randomUUID()
            }
        } else {
            UUID.randomUUID()
        }
    }
}

package cn.reverie.nocturne

import com.google.gson.Gson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

/**
 * Client attestation: proves requests originate from official client.
 *
 * APP_SECRET is baked into native image alongside APP_ID at build time
 * (obf.json native.appId / native.appSecret -> CMake NC_APP_ID/NC_APP_SECRET).
 * Canonical string formatting and HMAC are handled in DLL.
 *
 * APP_ID is sent in plaintext with each request in the X-App-Id header.
 */
object ClientAttest {

    private const val APP_ID = "nc-1"

    private val gson = Gson()
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build()

    data class Handshake(val ok: Boolean, val challenge: String = "", val bits: Int = 0, val error: String = "")

    fun headers(path: String, body: String): Map<String, String> {
        val ts = NocturneAuth.serverTimeSec()
        val nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 32)
        return mapOf(
            "X-App-Id" to APP_ID,
            "X-App-Ts" to ts.toString(),
            "X-App-Nonce" to nonce,
            "X-App-Sign" to NativeGuard.appSign(ts.toString(), nonce, path, body)
        )
    }

    fun handshake(): Handshake {
        return try {
            val payload = gson.toJson(mapOf("app_id" to APP_ID))
            val req = HttpRequest.newBuilder(URI.create(NocturneAuth.API_BASE + "/api/handshake"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
            val obj = gson.fromJson(
                client.send(req, HttpResponse.BodyHandlers.ofString()).body(), Map::class.java
            )
            if (obj?.get("ok") == true) {
                Handshake(
                    true,
                    obj["challenge"] as? String ?: "",
                    (obj["bits"] as? Double)?.toInt() ?: 0
                )
            } else {
                Handshake(false, error = "Server rejected handshake")
            }
        } catch (e: Throwable) {
            Handshake(false, error = "Unable to connect to authentication server")
        }
    }

    private fun leadingZeroBits(digest: ByteArray): Int {
        var bits = 0
        for (b in digest) {
            val v = b.toInt() and 0xFF
            if (v == 0) {
                bits += 8
                continue
            }
            for (shift in 7 downTo 0) {
                if (v and (1 shl shift) != 0) return bits
                bits++
            }
            break
        }
        return bits
    }

    fun solvePow(challenge: String, bits: Int, timeoutMs: Long = 25_000L): String? {
        if (challenge.isEmpty() || bits <= 0) return null
        val md = MessageDigest.getInstance("SHA-256")
        val deadline = System.currentTimeMillis() + timeoutMs
        var i = 0L
        while (System.currentTimeMillis() < deadline) {
            val nonce = String.format("%08x", i)
            md.reset()
            md.update("$challenge:$nonce".toByteArray())
            if (leadingZeroBits(md.digest()) >= bits) return nonce
            i++
        }
        return null
    }
}

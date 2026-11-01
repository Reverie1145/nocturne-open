package cn.reverie.app.agent.auth

import cn.reverie.app.agent.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import java.net.URI
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

internal data class OpenAiIdentity(val subject: String, val email: String, val name: String)

/** Signature, issuer, audience, expiry and nonce are checked before accepting a login. */
internal class OpenAiIdentityVerifier(
    private val endpoints: OpenAiEndpoints,
    private val http: OpenAiOAuthHttp,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 }
) {
    private var discovery: JsonObject? = null
    private var jwks: JsonObject? = null
    private var fetchedAt = 0L
    @Synchronized fun metadata(cancellation: AgentCancellation): JsonObject {
        if (discovery == null) {
            val data = http.json(endpoints.discovery, cancellation)
            require(data.string("issuer") == endpoints.issuer) { "OpenAI 身份服务 issuer 不匹配" }
            trustedEndpoint(data.string("jwks_uri"))
            for ((field, expected) in listOf("authorization_endpoint" to endpoints.authorize, "token_endpoint" to endpoints.token)) {
                require(data.string(field) == expected) { "OpenAI 身份服务端点不匹配" }
            }
            discovery = data
        }
        return discovery!!.deepCopy()
    }
    fun trustedEndpoint(raw: String): String {
        val value = URI(raw); val issuer = URI(endpoints.issuer)
        require(value.scheme == issuer.scheme && value.rawAuthority == issuer.rawAuthority &&
            value.userInfo == null && value.query == null && value.fragment == null && value.path.startsWith("/")) { "OpenAI 身份服务返回了不可信端点" }
        return raw
    }
    @Synchronized fun verify(token: String, clientId: String, nonce: String?, cancellation: AgentCancellation): OpenAiIdentity {
        require(token.length in 1..32768) { "OpenAI ID Token 长度无效" }
        val parts = token.split('.')
        require(parts.size == 3) { "OpenAI ID Token 格式无效" }
        val decoder = Base64.getUrlDecoder()
        val header = JsonParser.parseString(String(decoder.decode(parts[0]), Charsets.UTF_8)).asJsonObject
        require(header.string("alg") == "RS256" && !header.has("crit") && !header.has("b64")) { "OpenAI ID Token 签名算法无效" }
        val kid = header.string("kid")
        require(kid.isNotBlank() && kid.length <= 200) { "OpenAI ID Token 缺少签名标识" }
        val metadata = metadata(cancellation)
        fun refreshKeys() {
            jwks = http.json(metadata.string("jwks_uri"), cancellation)
            fetchedAt = now()
        }
        if (jwks == null || now() - fetchedAt > 300) refreshKeys()
        fun key() = jwks!!.getAsJsonArray("keys")?.filter { it.isJsonObject && it.asJsonObject.string("kid") == kid }
            ?.singleOrNull()?.asJsonObject
        var jwk = key()
        if (jwk == null) { refreshKeys(); jwk = key() }
        val signingKey = jwk ?: error("OpenAI ID Token 的签名标识无法验证")
        require(signingKey.string("kty") == "RSA" && signingKey.string("use", "sig") == "sig" &&
            signingKey.string("alg", "RS256") == "RS256") { "OpenAI 签名公钥无效" }
        val modulus = BigInteger(1, decoder.decode(signingKey.string("n")))
        val exponent = BigInteger(1, decoder.decode(signingKey.string("e")))
        require(modulus.bitLength() in 2048..8192 && exponent >= BigInteger.valueOf(3)) { "OpenAI 签名公钥强度无效" }
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey); verifier.update((parts[0] + "." + parts[1]).toByteArray(Charsets.US_ASCII))
        require(verifier.verify(decoder.decode(parts[2]))) { "OpenAI ID Token 签名无法验证" }
        val claims = JsonParser.parseString(String(decoder.decode(parts[1]), Charsets.UTF_8)).asJsonObject
        require(claims.string("iss") == endpoints.issuer) { "OpenAI ID Token issuer 不匹配" }
        val audience = claims.get("aud")
        val audiences = when { audience?.isJsonArray == true -> audience.asJsonArray.map { it.asString }
            audience?.isJsonPrimitive == true -> listOf(audience.asString); else -> emptyList() }
        require(clientId in audiences && (audiences.size <= 1 || claims.string("azp") == clientId)) { "OpenAI ID Token audience 不匹配" }
        val time = now()
        require((claims.get("exp")?.asLong ?: 0) > time - 30) { "OpenAI ID Token 已过期" }
        require((claims.get("iat")?.asLong ?: time) <= time + 60 && (claims.get("nbf")?.asLong ?: time) <= time + 60) { "OpenAI ID Token 尚未生效" }
        if (nonce != null) require(MessageDigest.isEqual(nonce.toByteArray(), claims.string("nonce").toByteArray())) { "OpenAI ID Token nonce 不匹配" }
        val subject = claims.string("sub")
        require(subject.isNotBlank() && subject.length <= 500) { "OpenAI ID Token 缺少账户标识" }
        cancellation.check()
        return OpenAiIdentity(subject, claims.string("email").take(254), claims.string("name").take(100))
    }
}

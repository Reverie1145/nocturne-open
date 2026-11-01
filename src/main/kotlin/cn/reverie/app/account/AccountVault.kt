package cn.reverie.app.account

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object AccountVault {

    private const val FORMAT = "nocturne-accounts"
    private const val PEPPER = "nocturne:account-vault:v2:c41f9e7b"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val VERSION = 2
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12

    private val RANDOM = SecureRandom()

    fun write(file: File, plain: String) {
        val salt = ByteArray(SALT_LENGTH).also { RANDOM.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { RANDOM.nextBytes(it) }

        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))

        val encoder = Base64.getEncoder()
        val root = JsonObject()
        root.addProperty("format", FORMAT)
        root.addProperty("version", VERSION)
        root.addProperty("kdf", KDF)
        root.addProperty("cipher", CIPHER)
        root.addProperty("iterations", ITERATIONS)
        root.addProperty("salt", encoder.encodeToString(salt))
        root.addProperty("iv", encoder.encodeToString(iv))
        root.addProperty("data", encoder.encodeToString(cipherText))

        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()

        val temp = File(parent, file.name + ".tmp")
        Files.write(temp.toPath(), root.toString().toByteArray(StandardCharsets.UTF_8))

        try {
            Files.move(temp.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun read(file: File): String? {
        val raw = String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim()
        if (raw.isEmpty()) return null

        val root = JsonParser.parseString(raw).asJsonObject
        if (!root.has("salt") || !root.has("iv") || !root.has("data")) return null

        val iterations = if (root.has("iterations")) root.get("iterations").asInt else ITERATIONS

        val decoder = Base64.getDecoder()
        val salt = decoder.decode(root.get("salt").asString)
        val iv = decoder.decode(root.get("iv").asString)
        val cipherText = decoder.decode(root.get("data").asString)

        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
    }

    private fun deriveKey(salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(machineSecret().toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun machineSecret(): String = buildString(160) {
        append(System.getProperty("user.name", "")).append('|')
        append(System.getenv("COMPUTERNAME") ?: "").append('|')
        append(System.getenv("USERDOMAIN") ?: "").append('|')
        append(System.getProperty("os.name", "")).append('|')
        append(System.getProperty("os.arch", "")).append('|')
        append(Runtime.getRuntime().availableProcessors()).append('|')
        append(PEPPER)
    }
}

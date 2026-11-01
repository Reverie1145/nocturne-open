package cn.reverie.nocturne

import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Loads and decrypts the native payloads bundled by the protection pipeline:
 *  - n0.dat : core DLL (mapped manually into the process by NBootstrap, never
 *             touching disk and never registered in the PEB module list)
 *  - n1.dat : tiny loader DLL (decrypted to a randomized temp file, loaded
 *             via System.load, file deleted afterwards)
 *
 * On-disk layout, as produced by Pipeline + Util.aesEncrypt:
 *
 *   [ 16 IV ][ AES-CTR ciphertext ]
 *
 * and the ciphertext decrypts to
 *
 *   [ 16 random prefix ][ raw DLL image ]
 *
 * The random prefix exists so the encrypted payload has no recognizable PE
 * header at rest; it is NOT part of the AES header. The AES key is derived
 * from a build-time secret shared with the obfuscator config
 * (`native.bundleKey`).
 */
internal object NRes {

    private const val SECRET = "nocturne-nc-bundle-v1"
    private const val IV_LEN = 16
    private const val PREFIX_LEN = 16

    fun read(which: String): ByteArray? {
        val path = when (which) {
            "core" -> "/assets/nocturne/n0.dat"
            "loader" -> "/assets/nocturne/n1.dat"
            else -> return null
        }
        return try {
            val raw = NRes::class.java.getResourceAsStream(path)?.use { input ->
                val bo = ByteArrayOutputStream()
                val buf = ByteArray(1 shl 15)
                var r: Int
                while (input.read(buf).also { r = it } > 0) bo.write(buf, 0, r)
                bo.toByteArray()
            } ?: return null
            if (raw.size <= IV_LEN + PREFIX_LEN) return null
            val key = keyBytes()
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(raw, 0, IV_LEN)
            )
            val plain = cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN)
            plain.copyOfRange(PREFIX_LEN, plain.size)
        } catch (t: Throwable) {
            null
        }
    }

    private fun keyBytes(): ByteArray {
        // mirrors Pipeline.bundleKey: aesKey(material) = sha256(material || "nc-aes-kdf")[0..16)
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(SECRET.toByteArray(Charsets.UTF_8))
        md.update("nc-aes-kdf".toByteArray(Charsets.UTF_8))
        return md.digest().copyOf(16)
    }
}
package cn.reverie.nocturne

import cn.reverie.gui.splash.ProtectionSplashWindow
import net.fabricmc.api.ClientModInitializer
import java.io.DataInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Packer (加壳) bootstrap. Invoked first from the client entrypoint. It loads
 * the native DLL (ensuring NativeGuard.packKey is ready), then reads the packed
 * class manifest and defines every packed class body — decrypted with keys
 * derived only inside the native image — into a child DecryptingClassLoader.
 *
 * Everything packed is by construction UNREACHABLE from the plaintext surface,
 * so defining them here is safe and gives dumpers ciphertext-only at rest.
 *
 * Kept unrenamed by the obfuscation config.
 */
object PackBoot {

    private val launched = AtomicBoolean(false)

    /** retain the loader so packed classes survive the whole process lifetime */
    private var retained: ClassLoader? = null

    fun launch() {
        if (!launched.compareAndSet(false, true)) return
        // A source run has neither the native image nor the packed class bodies
        // (those are produced by the release pipeline), so there is nothing to
        // apply. Release jars never take this branch.
        if (NocturneAuth.DEV_MODE) {
            System.out.println("[Nocturne] development run: class protection is a release-only step")
            ProtectionSplashWindow.complete()
            return
        }
        var loaded = 0
        try {
            NativeGuard.ensureInit()
            val loader = DecryptingClassLoader(ClientModInitializer::class.java.classLoader)
            retained = loader
            val stream = DecryptingClassLoader::class.java.getResourceAsStream("/assets/nocturne/c/index.bin")
                ?: run {
                    System.err.println("[Nocturne] packed class manifest missing")
                    return
                }
            DataInputStream(stream).use { di ->
                val count = di.readInt()
                for (idx in 0 until count) {
                    val nameLen = di.readInt()
                    val nb = ByteArray(nameLen)
                    di.readFully(nb)
                    val name = String(nb, Charsets.UTF_8)
                    val key = NativeGuard.packKey(idx)
                    val blob = DecryptingClassLoader::class.java
                        .getResourceAsStream("/assets/nocturne/c/$idx.dat")?.readBytes()
                    if (blob == null) {
                        System.err.println("[Nocturne] packed body missing: $name (index $idx)")
                        continue
                    }
                    loader.define(name, aesDecrypt(key, blob))
                    loaded++
                    // report real progress across the pack slice of the bar
                    if (idx % 4 == 0 || idx == count - 1) {
                        val done = (idx + 1).toFloat() / count.toFloat()
                        ProtectionSplashWindow.report(
                            ProtectionSplashWindow.PACK_FROM +
                                (ProtectionSplashWindow.PACK_TO - ProtectionSplashWindow.PACK_FROM) * done,
                            "Applying class protection ${idx + 1}/$count"
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            // The native layer itself is fatal-on-failure; a problem here means a
            // packed body could not be defined, which surfaces later as a
            // NoClassDefFoundError. Record it so it is diagnosable from latest.log.
            System.err.println("[Nocturne] pack boot incomplete after $loaded classes: $t")
        } finally {
            ProtectionSplashWindow.complete()
        }
    }

    private fun aesDecrypt(key: ByteArray, blob: ByteArray): ByteArray {
        val c = javax.crypto.Cipher.getInstance(a0())
        c.init(javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, a1()),
            javax.crypto.spec.IvParameterSpec(blob, 0, 16))
        val out = ByteArray(blob.size - 16)
        System.arraycopy(blob, 16, out, 0, out.size)
        return c.doFinal(out)
    }

    private fun a0(): String = String(charArrayOf('A', 'E', 'S', '/', 'C', 'T', 'R', '/', 'N', 'o', 'P', 'a', 'd', 'd', 'i', 'n', 'g'))
    private fun a1(): String = String(charArrayOf('A', 'E', 'S'))
}
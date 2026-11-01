package cn.reverie.nocturne

import cn.reverie.gui.splash.ProtectionSplashWindow
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native guard bridge. On first use it decrypts the bundled loader + core
 * payloads, loads the loader from a randomized temp file, and asks it to
 * manually map the core DLL into the process (invisible to module walks and
 * LoadLibrary-based dumpers). The core registers its JNI natives against the
 * live (possibly renamed) NativeGuard class at load time, so renaming is
 * transparent.
 *
 * ALL-IN-NATIVE: there is deliberately no Java fallback. The build seed lives
 * only inside the manually mapped image, so every string-pool key, class-pack
 * key, request signature, integrity verdict and watchdog decision is produced
 * by native code. If the image cannot be loaded and bound, the process is
 * rejected outright — a jar with the native layer stripped cannot run.
 */
object NativeGuard {

    private val started = AtomicBoolean(false)

    /** true once the core image is mapped and its natives are bound. */
    @Volatile
    var ready = false
        private set

    @JvmStatic
    fun ensureInit() {
        if (!started.compareAndSet(false, true)) return
        val ok = try {
            loadNative()
        } catch (t: Throwable) {
            false
        }
        if (!ok) {
            reject(
                "native guard image unavailable. A release jar must bundle it " +
                    "(assets/nocturne/n0.dat); a source run needs " +
                    "-Dnocturne.native.dir=<build/native/Release> — the Gradle " +
                    "runClient task sets this automatically"
            )
        }
        ready = true
    }

    /** Decrypt both payloads, load the loader, map the core, bind the natives. */
    private fun loadNative(): Boolean {
        step(0.32f, "Decrypting protection core")
        val core = NRes.read("core")
        if (core != null) {
            try {
                return loadBundled(core)
            } finally {
                core.fill(0)
            }
        }
        // No bundled payload: this is an unprotected source run, not a release.
        diag("no bundled core payload (assets/nocturne/n0.dat) — trying the dev native dir")
        return loadFromDisk()
    }

    /** Release path: loader from a decrypted temp file, core mapped from memory. */
    private fun loadBundled(core: ByteArray): Boolean {
        step(0.36f, "Decrypting protection loader")
        val loader = NRes.read("loader")
        if (loader == null) {
            diag("loader payload missing or undecryptable (assets/nocturne/n1.dat)")
            return false
        }
        step(0.42f, "Loading protection module")
        val tmp = writeTemp(loader)
        loader.fill(0)
        if (tmp == null) {
            diag("no writable directory available for the loader image")
            return false
        }
        if (!systemLoad(tmp)) return false
        step(0.50f, "Mapping core image")
        if (!NBootstrap.bind(core, NativeGuard::class.java)) {
            diag("NBootstrap.bind failed (manual map or RegisterNatives rejected the image)")
            return false
        }
        step(0.58f, "Arming native watchdog")
        return true
    }

    /**
     * Source-run path. An unprotected dev run carries no payloads, so it loads the
     * DLLs the build just produced instead (-Dnocturne.native.dir=<build/native/Release>,
     * wired into the Gradle runClient task). Dev therefore exercises the exact same
     * native code path as a release; no Java fallback is reintroduced.
     */
    private fun loadFromDisk(): Boolean {
        val dir = System.getProperty("nocturne.native.dir")?.takeIf { it.isNotBlank() } ?: return false
        return try {
            val d = Path.of(dir)
            val corePath = d.resolve("nocturne_core.dll")
            val loaderPath = d.resolve("nc_loader.dll")
            if (!Files.isReadable(corePath) || !Files.isReadable(loaderPath)) {
                diag("nocturne.native.dir is set but the DLLs are missing: $d")
                return false
            }
            step(0.42f, "Loading protection module")
            if (!systemLoad(loaderPath)) return false
            step(0.50f, "Mapping core image")
            val core = Files.readAllBytes(corePath)
            val bound = try {
                NBootstrap.bind(core, NativeGuard::class.java)
            } finally {
                core.fill(0)
            }
            if (!bound) {
                diag("NBootstrap.bind failed on $corePath")
                return false
            }
            step(0.58f, "Arming native watchdog")
            true
        } catch (t: Throwable) {
            diag("dev native load failed: $t")
            false
        }
    }

    private fun systemLoad(path: Path): Boolean = try {
        System.load(path.toAbsolutePath().toString())
        true
    } catch (t: Throwable) {
        diag("System.load failed on $path: $t")
        false
    }

    /** Report real progress to the startup splash (best effort, never fatal). */
    private fun step(progress: Float, operation: String) {
        try {
            ProtectionSplashWindow.report(progress, operation)
        } catch (ignored: Throwable) {
        }
    }

    /** One line to stderr so a failure is diagnosable from latest.log. */
    private fun diag(msg: String) {
        try {
            System.err.println("[Nocturne] native: $msg")
        } catch (ignored: Throwable) {
        }
    }

    /**
     * The loader must be a real file on disk for System.load. Try the usual
     * temp location first, then a per-user directory, so a locked-down or
     * cleaned %TEMP% cannot stop the client from starting.
     */
    private fun writeTemp(bytes: ByteArray): Path? {
        for (dir in candidateDirs()) {
            try {
                Files.createDirectories(dir)
                val f = Files.createTempFile(dir, "ncr", ".dll")
                f.toFile().deleteOnExit()
                Files.write(f, bytes)
                return f
            } catch (ignored: Throwable) {
                // try the next candidate
            }
        }
        return null
    }

    private fun candidateDirs(): List<Path> {
        val out = ArrayList<Path>(3)
        try {
            System.getProperty("java.io.tmpdir")?.let { out.add(Path.of(it)) }
        } catch (ignored: Throwable) {
        }
        try {
            System.getProperty("user.home")?.let { out.add(Path.of(it, ".nocturne", "bin")) }
        } catch (ignored: Throwable) {
        }
        try {
            System.getProperty("user.dir")?.let { out.add(Path.of(it)) }
        } catch (ignored: Throwable) {
        }
        return out
    }

    /** Refuse to run: the protected surface is unavailable, so nothing works. */
    private fun reject(reason: String) {
        try {
            System.err.println("[Nocturne] FATAL: $reason")
        } catch (ignored: Throwable) {
        }
        Runtime.getRuntime().halt(0x4E430001)
    }

    // ---- all-in-native services (no Java counterpart by design) ----

    /** Session-key HMAC over the UTF-8 bytes of [data], lowercase hex. */
    fun hmacHex(data: String): String {
        ensureInit()
        return nHmac(data)
    }

    /** Push the session key into native memory (the Java copy can be dropped). */
    fun setSessionKey(key: ByteArray) {
        ensureInit()
        nSetKey(if (key.isEmpty()) ByteArray(1) else key)
    }

    /** Watchdog tick: false means the environment failed a native integrity check. */
    fun watchdogOk(): Boolean {
        ensureInit()
        return nWatchdog()
    }

    /**
     * String-pool key for (poolId, blobId). Derived only inside the native
     * image, so pool ciphertext in the jar is undecryptable without the DLL.
     */
    fun poolKey(poolId: Int, blobId: Int): ByteArray {
        ensureInit()
        return nPoolKey(poolId, blobId)
    }

    /**
     * Class-pack key for the packer (加壳). Derived only inside the native
     * image, so packed class bodies in the jar are undecryptable without the DLL.
     */
    fun packKey(classIndex: Int): ByteArray {
        ensureInit()
        return nPackKey(classIndex)
    }

    /** Full request signing: canonical "ts|nonce|body", HMAC'd in native. */
    fun signRequest(ts: String, nonce: String, body: String): String {
        ensureInit()
        return nSign(ts, nonce, body)
    }

    /**
     * Client attestation signature. The app id and app secret are baked into
     * the native image at build time, so neither exists anywhere in the jar.
     */
    fun appSign(ts: String, nonce: String, path: String, body: String): String {
        ensureInit()
        return nAppSign(ts, nonce, path, body)
    }

    /** Native SHA-256 equality check for the jar integrity sampler. */
    fun verifyChunk(data: ByteArray, expect: ByteArray): Boolean {
        if (!ready) return false
        return nVerify(data, expect)
    }

    /** Native panic path — far harder to hook than Runtime.halt. */
    fun panic(code: Int) {
        if (ready) {
            try {
                nPanic(code)
            } catch (ignored: Throwable) {
            }
        }
        Runtime.getRuntime().halt(code)
    }

    // Registered via RegisterNatives at native load — member names must stay
    // stable (keepMembers rule in obf.json) but the class name may be renamed.
    private external fun nSetKey(key: ByteArray)
    private external fun nHmac(data: String): String
    private external fun nPoolKey(poolId: Int, blobId: Int): ByteArray
    private external fun nPackKey(classIndex: Int): ByteArray
    private external fun nSign(ts: String, nonce: String, body: String): String
    private external fun nAppSign(ts: String, nonce: String, path: String, body: String): String
    private external fun nVerify(data: ByteArray, expect: ByteArray): Boolean
    private external fun nWatchdog(): Boolean
    private external fun nPanic(code: Int)
}
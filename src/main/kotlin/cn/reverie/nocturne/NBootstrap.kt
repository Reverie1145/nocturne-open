package cn.reverie.nocturne

/**
 * Name-based JNI bridge to the loader DLL — this class must NOT be renamed
 * (its name is baked into the loader's JNI export) and it is the entire
 * on-disk native surface. It receives the decrypted core image, maps it
 * manually, and the core registers NativeGuard's natives against the live
 * class object.
 */
object NBootstrap {
    external fun bind(core: ByteArray, guardClass: Class<*>): Boolean
}

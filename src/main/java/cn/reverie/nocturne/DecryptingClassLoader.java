package cn.reverie.nocturne;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * Packer loader. A child of the Fabric/root loader that defines the
 * decrypted packed classes. Once defined, the plaintext byte[] is wiped so no
 * copy of the class bytes lingers on the heap. Packed classes resolve their
 * parents (Minecraft/Fabric/kotlin-stdlib, kept glue) through parent delegation.
 *
 * Kept unrenamed by the obfuscation config so PackBoot can reference it.
 */
public final class DecryptingClassLoader extends URLClassLoader {

    public DecryptingClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    public Class<?> define(String name, byte[] bytes) {
        try {
            return defineClass(name, bytes, 0, bytes.length);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
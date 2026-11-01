package cn.reverie.nocturne;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for string-pool caches. Obfuscation-generated pool classes register
 * their wipe action here at clinit; flushAll drops every decoded plaintext so
 * live-memory dumps only contain ciphertext.
 *
 * Implemented in pure Java with ZERO string literals or compiler-generated null
 * checks to guarantee clean bootstrap without string-decryption deadlocks.
 */
public final class StringVault {

    public static final StringVault INSTANCE = new StringVault();

    private static final CopyOnWriteArrayList<Runnable> pools = new CopyOnWriteArrayList<>();

    private StringVault() {
    }

    public static void register(Runnable r) {
        if (r != null) {
            pools.add(r);
        }
    }

    public static void flushAll() {
        for (Runnable r : pools) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }
}

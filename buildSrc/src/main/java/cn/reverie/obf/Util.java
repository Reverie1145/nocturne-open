package cn.reverie.obf;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public final class Util {

    private Util() {
    }

    /** xorshift128+ seeded PRNG — small, deterministic, good enough for name/codec generation. */
    public static final class Rng {
        private long s0, s1;

        public Rng(long seed) {
            long z = seed + 0x9E3779B97F4A7C15L;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            s0 = z ^ (z >>> 31);
            s1 = z * 0x2545F4914F6CDD1DL + 1;
        }

        public long nextLong() {
            long x = s0, y = s1;
            s0 = y;
            x ^= x << 23;
            s1 = x ^ y ^ (x >>> 17) ^ (y >>> 26);
            return s1 + y;
        }

        public int nextInt() {
            return (int) (nextLong() >>> 32);
        }

        public int nextInt(int bound) {
            return (int) ((nextLong() >>> 33) % bound);
        }

        public boolean nextBool(double p) {
            return ((nextLong() >>> 11) * 0x1.0p-53) < p;
        }

        public int pick(int[] arr) {
            return arr[nextInt(arr.length)];
        }
    }

    public static byte[] sha256(byte[]... chunks) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] c : chunks) md.update(c);
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hex(byte[] b) {
        char[] cs = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            cs[i * 2] = "0123456789abcdef".charAt(v >>> 4);
            cs[i * 2 + 1] = "0123456789abcdef".charAt(v & 0xF);
        }
        return new String(cs);
    }

    /** 128-bit AES key derived from any material. */
    public static byte[] aesKey(byte[] material) {
        return slice(sha256(material, "nc-aes-kdf".getBytes(StandardCharsets.UTF_8)), 0, 16);
    }

    public static byte[] aesEncrypt(byte[] key, byte[] plain) {
        try {
            byte[] iv = new byte[16];
            new Rng(System.nanoTime()).nextInt(); // cheap entropy for the IV
            new java.security.SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[16 + ct.length];
            System.arraycopy(iv, 0, out, 0, 16);
            System.arraycopy(ct, 0, out, 16, ct.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] aesDecrypt(byte[] key, byte[] blob) {
        try {
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(blob, 0, 16));
            byte[] out = new byte[blob.length - 16];
            System.arraycopy(blob, 16, out, 0, out.length);
            return c.doFinal(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] slice(byte[] b, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(b, off, out, 0, len);
        return out;
    }

    public static int[] toInts(byte[] b) {
        int n = (b.length + 3) / 4;
        int[] out = new int[n];
        for (int i = 0; i < b.length; i++) {
            out[i >> 2] |= (b[i] & 0xFF) << ((i & 3) * 8);
        }
        return out;
    }

    public static byte[] fromInts(int[] a) {
        byte[] out = new byte[a.length * 4];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < 4; j++) {
                out[i * 4 + j] = (byte) (a[i] >>> (j * 8));
            }
        }
        return out;
    }

    public static List<String> splitLines(String s) {
        List<String> out = new ArrayList<>();
        for (String l : s.split("\n")) out.add(l);
        return out;
    }
}

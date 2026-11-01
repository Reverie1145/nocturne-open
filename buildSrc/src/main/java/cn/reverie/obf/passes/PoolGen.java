package cn.reverie.obf.passes;

import cn.reverie.obf.Ctx;
import cn.reverie.obf.JarSet;
import cn.reverie.obf.MemCompile;
import cn.reverie.obf.Util;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Emits the runtime string-pool classes (compiled from generated source with
 * the build JDK — javac produces the frames, we keep the code hostile).
 *
 * Runtime key bytes reproduce little-endian spread of key[] (the stored K =
 * key ^ mask; kb() re-XORs with mask so the mask cancels — it only obfuscates
 * the constants). Blob layout: [iv(16)][AES-CTR ct], plaintext
 * [int n][int len][utf8]... No plaintext string literals (algorithm names are
 * char arrays).
 */
final class PoolGen {

    /**
     * Int-array -> byte[] unpacker emitted verbatim into every pool class.
     * MUST be the exact inverse of Util.toInts (4 little-endian bytes per int);
     * verifyBlobs compiles this same snippet and round-trips every blob so a
     * wrong unpacker fails the build instead of the client at runtime.
     */
    private static final String TB =
        "    private static byte[] tb(int[] a) {\n" +
        "        byte[] o = new byte[a.length * 4];\n" +
        "        for (int i = 0; i < a.length; i++) {\n" +
        "            for (int j = 0; j < 4; j++) o[i * 4 + j] = (byte) (a[i] >>> (j * 8));\n" +
        "        }\n" +
        "        return o;\n" +
        "    }\n\n";

    private PoolGen() {
    }

    static ClassNode emit(String internal, List<List<String>> blobs, boolean deep, int version,
                          String vaultInternal, cn.reverie.obf.Ctx ctx, int poolId, String guardInternal) {
        // all-in-native: blobs are encrypted with keys derived from the build seed;
        // the runtime fetches them from NativeGuard (the seed lives ONLY in the DLL)
        byte[][] aesKeys = new byte[blobs.size()][];
        for (int i = 0; i < blobs.size(); i++) {
            aesKeys[i] = nativePoolKey(ctx.opt.seed, poolId, i);
        }

        int[][] blobInts = new int[blobs.size()][];
        for (int i = 0; i < blobs.size(); i++) blobInts[i] = packBlob(blobs.get(i), aesKeys[i]);

        String pkg = internal.substring(0, internal.lastIndexOf('/'));
        String cls = internal.substring(internal.lastIndexOf('/') + 1);
        java.util.List<java.nio.file.Path> cp = new java.util.ArrayList<>();
        if (ctx.inJar != null) cp.add(ctx.inJar);
        if (ctx.classDumpDir != null) cp.add(ctx.classDumpDir);
        verifyBlobs(pkg, cls, blobInts, aesKeys, blobs, cp, poolId);
        byte[] bytes = MemCompile.compile(pkg, cls,
                source(pkg, cls, blobInts, deep, vaultInternal, poolId, guardInternal), cp);

        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.EXPAND_FRAMES);
        return cn;
    }

    /**
     * Pool key derivation, mirrored 1:1 in nc_core.cpp (nPoolKey). The build
     * seed never appears in the jar — only inside the native image.
     */
    static byte[] nativePoolKey(long seed, int poolId, int blobId) {
        long st = seed ^ 0x4E4B4559L
                ^ (long) poolId * 0x9E3779B97F4A7C15L
                ^ ((long) blobId << 32);
        byte[] out = new byte[16];
        for (int i = 0; i < 4; i++) {
            st = st * 6364136223846793005L + 1442695040888963407L;
            int v = (int) (st >>> 32);
            out[i * 4] = (byte) v;
            out[i * 4 + 1] = (byte) (v >>> 8);
            out[i * 4 + 2] = (byte) (v >>> 16);
            out[i * 4 + 3] = (byte) (v >>> 24);
        }
        return out;
    }

    static byte[] nativePackKey(long seed, int classIndex) {
        long st = seed ^ 0x504B4543L ^ ((long) classIndex) * 0x9E3779B97F4A7C15L;
        byte[] out = new byte[16];
        for (int i = 0; i < 4; i++) {
            st = st * 6364136223846793005L + 1442695040888963407L;
            int v = (int) (st >>> 32);
            out[i * 4] = (byte) v;
            out[i * 4 + 1] = (byte) (v >>> 8);
            out[i * 4 + 2] = (byte) (v >>> 16);
            out[i * 4 + 3] = (byte) (v >>> 24);
        }
        return out;
    }

    /**
     * Compiles the emitted tb() snippet into a throwaway class and decodes every
     * blob with it, so an unpacker that does not invert Util.toInts (or a key
     * that does not match the build seed) breaks the build right here instead of
     * surfacing as NegativeArraySizeException in the client.
     */
    private static void verifyBlobs(String pkg, String cls, int[][] blobInts, byte[][] aesKeys,
                                    List<List<String>> blobs, java.util.List<java.nio.file.Path> cp,
                                    int poolId) {
        if (rt == null) {
            String name = cls + "_rt";
            StringBuilder sb = new StringBuilder();
            sb.append("package ").append(pkg).append(";\n\n");
            sb.append("public final class ").append(name).append(" {\n\n");
            sb.append(TB.replace("private static byte[] tb(", "static byte[] tb("));
            sb.append("    public static String run(int[][] a, byte[][] keys, int[] counts) {\n")
              .append("        for (int i = 0; i < a.length; i++) {\n")
              .append("            byte[] blob = tb(a[i]);\n")
              .append("            try {\n")
              .append("                javax.crypto.Cipher c = javax.crypto.Cipher.getInstance(\"AES/CTR/NoPadding\");\n")
              .append("                c.init(javax.crypto.Cipher.DECRYPT_MODE,\n")
              .append("                        new javax.crypto.spec.SecretKeySpec(keys[i], \"AES\"),\n")
              .append("                        new javax.crypto.spec.IvParameterSpec(blob, 0, 16));\n")
              .append("                byte[] ct = java.util.Arrays.copyOfRange(blob, 16, blob.length);\n")
              .append("                byte[] raw = c.doFinal(ct);\n")
              .append("                java.io.DataInputStream in = new java.io.DataInputStream(\n")
              .append("                        new java.io.ByteArrayInputStream(raw));\n")
              .append("                int n = in.readInt();\n")
              .append("                if (n != counts[i]) return \"blob \" + i + \" decoded count \" + n + \" != \" + counts[i];\n")
              .append("                for (int j = 0; j < n; j++) {\n")
              .append("                    int L = in.readInt();\n")
              .append("                    if (L < 0) return \"blob \" + i + \" negative entry length\";\n")
              .append("                    for (int k = 0; k < L; k++) in.readChar();\n")
              .append("                }\n")
              .append("            } catch (Throwable t) {\n")
              .append("                return \"blob \" + i + \" threw \" + t;\n")
              .append("            }\n")
              .append("        }\n")
              .append("        return null;\n")
              .append("    }\n}\n");
            byte[] bytes = MemCompile.compile(pkg, name, sb.toString(), cp);
            rt = new ByteLoader(PoolGen.class.getClassLoader()).define(pkg + "." + name, bytes);
        }
        int[] counts = new int[blobs.size()];
        for (int i = 0; i < counts.length; i++) counts[i] = blobs.get(i).size();
        try {
            Object r = rt.getMethod("run", int[][].class, byte[][].class, int[].class)
                    .invoke(null, (Object) blobInts, (Object) aesKeys, (Object) counts);
            if (r != null) {
                throw new IllegalStateException("[pool] round-trip check failed (poolId=" + poolId + "): " + r);
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IllegalStateException("[pool] round-trip check failed (poolId=" + poolId + "): " + e.getCause(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("[pool] round-trip harness error: " + e, e);
        }
    }

    private static final class ByteLoader extends ClassLoader {
        ByteLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String n, byte[] b) {
            return defineClass(n, b, 0, b.length);
        }
    }

    private static Class<?> rt;

    static byte[] write(ClassNode cn, JarSet jar) {
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    static void pushInt(org.objectweb.asm.tree.InsnList il, int v) {
        if (v >= -1 && v <= 5) il.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ICONST_0 + v));
        else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) il.add(new org.objectweb.asm.tree.IntInsnNode(org.objectweb.asm.Opcodes.BIPUSH, v));
        else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) il.add(new org.objectweb.asm.tree.IntInsnNode(org.objectweb.asm.Opcodes.SIPUSH, v));
        else il.add(new org.objectweb.asm.tree.LdcInsnNode(v));
    }

    private static int[] packBlob(List<String> strings, byte[] aesKey) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bo)) {
            dos.writeInt(strings.size());
            for (String s : strings) {
                // UTF-16 code units, not UTF-8: call-site masks can legitimately
                // produce unpaired surrogates and UTF-8 would silently corrupt them.
                dos.writeInt(s.length());
                for (int i = 0; i < s.length(); i++) dos.writeChar(s.charAt(i));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return Util.toInts(Util.aesEncrypt(aesKey, bo.toByteArray()));
    }

    private static String source(String pkg, String cls, int[][] blobInts,
                                 boolean deep, String vaultInternal, int poolId, String guardInternal) {
        StringBuilder sb = new StringBuilder(1 << 20);
        sb.append("package ").append(pkg).append(";\n\n")
          .append("public final class ").append(cls).append(" {\n\n")
          .append("    private static byte[][] P;\n")
          .append("    private static String[][] C;\n\n")
          .append("    static {\n")
          .append("        P = new byte[").append(blobInts.length).append("][];\n");
        for (int b = 0; b < blobInts.length; b++) {
            sb.append("        P[").append(b).append("] = tb(p").append(b).append("());\n");
        }
        sb.append("        C = new String[").append(blobInts.length).append("][];\n");
        if (vaultInternal != null) {
            sb.append("        ").append(vaultInternal.replace('/', '.'))
              .append(".INSTANCE.register(").append(cls).append("::f);\n");
        }
        sb.append("    }\n\n");

        // decode entry: g(blob, idx, siteKey) -> String
        sb.append("    public static String g(int b, int i, int k) {\n")
          .append("        try {\n")
          .append("            return g0(b, i, k);\n")
          .append("        } catch (RuntimeException e) {\n")
          .append("            throw e;\n")
          .append("        } catch (Exception e) {\n")
          .append("            throw new RuntimeException(e);\n")
          .append("        }\n")
          .append("    }\n\n")
          .append("    private static String g0(int b, int i, int k) throws Exception {\n")
          .append("        String[] arr = C[b];\n")
          .append("        if (arr == null) { arr = dec(P[b], b); C[b] = arr; }\n")
          .append("        char[] ch = arr[i].toCharArray();\n")
          .append("        for (int j = 0; j < ch.length; j++) {\n")
          .append("            int m = Integer.rotateLeft(k, j & 7) & 0xFFFF;\n");
        if (deep) {
            sb.append("            m = (m - 1 & 0xFFFF) + 1 & 0xFFFF;\n");
        }
        sb.append("            ch[j] ^= (char) m;\n")
          .append("        }\n")
          .append("        return new String(ch);\n")
          .append("    }\n\n")
          .append("    public static void f() { C = new String[").append(blobInts.length).append("][]; }\n\n");

        // blob literal methods
        for (int b = 0; b < blobInts.length; b++) {
            sb.append("    private static int[] p").append(b).append("() {\n        return new int[]{");
            int[] d = blobInts[b];
            for (int i = 0; i < d.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(d[i]);
            }
            sb.append("};\n    }\n\n");
        }

        sb.append(TB)
          // all-in-native: the AES key exists only inside the manually-mapped native image
          .append("    private static byte[] kb(int b) {\n")
          .append("        byte[] k = ").append(guardInternal.replace('/', '.'))
          .append(".INSTANCE.poolKey(").append(poolId).append(", b);\n")
          .append("        if (k == null || k.length != 16) throw new IllegalStateException();\n")
          .append("        return k;\n    }\n\n")
          .append("    private static String a0() { return new String(new char[]{'A','E','S','/','C','T','R','/','N','o','P','a','d','d','i','n','g'}); }\n")
          .append("    private static String a1() { return new String(new char[]{'A','E','S'}); }\n\n")
          .append("    private static String[] dec(byte[] blob, int b) throws Exception {\n")
          .append("        byte[] iv = java.util.Arrays.copyOfRange(blob, 0, 16);\n")
          .append("        byte[] ct = java.util.Arrays.copyOfRange(blob, 16, blob.length);\n")
          .append("        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance(a0());\n")
          .append("        c.init(javax.crypto.Cipher.DECRYPT_MODE,\n")
          .append("                new javax.crypto.spec.SecretKeySpec(kb(b), a1()),\n")
          .append("                new javax.crypto.spec.IvParameterSpec(iv));\n")
          .append("        byte[] raw = c.doFinal(ct);\n")
          .append("        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(raw));\n")
          .append("        int n = in.readInt();\n")
          .append("        String[] out = new String[n];\n")
          .append("        for (int i = 0; i < n; i++) {\n")
          .append("            char[] cs = new char[in.readInt()];\n")
          .append("            for (int j = 0; j < cs.length; j++) cs[j] = in.readChar();\n")
          .append("            out[i] = new String(cs);\n")
          .append("        }\n")
          .append("        return out;\n")
          .append("    }\n")
          .append("}\n");
        return sb.toString();
    }
}

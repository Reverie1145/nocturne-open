package cn.reverie.obf.passes;

import cn.reverie.obf.Ctx;
import cn.reverie.obf.MemCompile;
import cn.reverie.obf.Util;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Pass 8 (final): jar integrity self-check + native guard bootstrap.
 *
 * Hashes a deterministic sample of the final class entries, embeds the
 * AES-encrypted manifest in a generated checker class, and hooks the checker
 * into the client entrypoint so it (and NativeGuard.ensureInit) runs before
 * any other mod code. Any tampered jar fails fast via Runtime.halt and the
 * native panic path.
 */
public final class IntegrityPass {

    public void apply(Ctx ctx, boolean serialized) {
        if (!ctx.opt.integrity.enabled) return;
        // ---- hook entrypoint FIRST (before sampling, so all class bytes in jar.entries are final) ----
        String hookRenamed = ctx.map.classMap.get(ctx.opt.integrity.hookClass);
        if (hookRenamed == null) {
            ctx.log("[integrity] WARNING: hook class not found: " + ctx.opt.integrity.hookClass);
            return;
        }

        // ---- generate checker class name ----
        String pkg = ctx.names.freshPackage();
        String cls = ctx.names.freshName(5, 12);
        String internal = pkg + "/" + cls;

        ClassNode hook = ctx.jar.classes.get(hookRenamed);
        String mname = ctx.opt.integrity.hookMethod;
        String mdesc = mname.substring(mname.indexOf('('));
        mname = mname.substring(0, mname.indexOf('('));
        MethodNode target = null;
        String params = mdesc.substring(0, mdesc.indexOf(')') + 1);
        String mappedName = ctx.map.methodMap.get(mname + "|" + params);
        for (MethodNode mn : hook.methods) {
            if (mn.desc.equals(mdesc) && (mn.name.equals(mname) || mn.name.equals(mappedName))) target = mn;
        }
        if (target == null) {
            ctx.log("[integrity] WARNING: hook method not found: " + mname + mdesc);
            return;
        }

        // Insert at head: push Class parameter first, then invoke static verify(Class)
        org.objectweb.asm.tree.InsnList hookInsns = new org.objectweb.asm.tree.InsnList();
        hookInsns.add(new LdcInsnNode(org.objectweb.asm.Type.getObjectType(hookRenamed)));
        hookInsns.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                internal, "verify", "(Ljava/lang/Class;)V", false));
        target.instructions.insert(hookInsns);

        // the hook is flow-flattened / has try-catch; re-serialize with full frame computation
        try {
            org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(
                    org.objectweb.asm.ClassWriter.COMPUTE_FRAMES | org.objectweb.asm.ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String a, String b) {
                    return ctx.jar.commonSuper(a, b);
                }
            };
            hook.accept(cw);
            ctx.jar.entries.put(hookRenamed + ".class", cw.toByteArray());
        } catch (Throwable t) {
            ctx.log("[integrity] hook frame recompute failed: " + hookRenamed + " (" + t + ")");
            ctx.jar.entries.put(hookRenamed + ".class", RenamePass.serialize(hook));
        }
        ctx.log("[integrity] hooked " + hookRenamed + "#" + mname);

        // ---- manifest over final class bytes (excluding hook class and generated checker) ----
        String hookEntry = hookRenamed + ".class";
        String checkerEntry = internal + ".class";
        List<String> names = new ArrayList<>();
        for (String entry : ctx.jar.entries.keySet()) {
            if (entry.endsWith(".class") && !entry.equals(hookEntry) && !entry.equals(checkerEntry)) {
                names.add(entry);
            }
        }
        names.sort(String::compareTo);
        int sample = Math.max(8, ctx.opt.integrity.sample);
        int stride = Math.max(1, names.size() / sample);
        List<String> chosen = new ArrayList<>();
        for (int i = 0; i < names.size() && chosen.size() < sample; i += stride) chosen.add(names.get(i));

        java.io.ByteArrayOutputStream mo = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream dos = new java.io.DataOutputStream(mo)) {
            dos.writeInt(chosen.size());
            for (String n : chosen) {
                byte[] nb = n.getBytes(StandardCharsets.UTF_8);
                dos.writeInt(nb.length);
                dos.write(nb);
                byte[] h = Util.sha256(ctx.jar.entries.get(n));
                dos.write(h);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        byte[] key = Util.aesKey(("nc-integrity-" + ctx.opt.seed).getBytes(StandardCharsets.UTF_8));
        byte[] enc = Util.aesEncrypt(key, mo.toByteArray());
        int[] keyInts = Util.toInts(key);
        int[] manInts = Util.toInts(enc);

        // ---- compile checker ----
        String guard = renamed(ctx, "cn/reverie/nocturne/NativeGuard");
        String guardDotted = guard.replace('/', '.');

        StringBuilder man = new StringBuilder();
        for (int i = 0; i < manInts.length; i++) {
            if (i > 0) man.append(',');
            man.append(manInts[i]);
        }
        StringBuilder kk = new StringBuilder();
        for (int i = 0; i < keyInts.length; i++) {
            if (i > 0) kk.append(',');
            kk.append(keyInts[i]);
        }

        String src = SRC
                .replace("%PKG%", pkg)
                .replace("%CLS%", cls)
                .replace("%GUARD%", guardDotted)
                .replace("%KEY%", kk)
                .replace("%MAN%", man);

        java.util.List<java.nio.file.Path> cp = new java.util.ArrayList<>();
        if (ctx.inJar != null) cp.add(ctx.inJar);
        if (ctx.classDumpDir != null) cp.add(ctx.classDumpDir);
        byte[] bytes = MemCompile.compile(pkg, cls, src, cp);
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.EXPAND_FRAMES);
        ctx.jar.classes.put(internal, cn);
        ctx.jar.entries.put(internal + ".class", bytes);
        ctx.generated.add(internal);
        ctx.log("[integrity] checker " + internal + " covers " + chosen.size() + " entries");
    }

    private static String renamed(Ctx ctx, String original) {
        String m = ctx.map.classMap.get(original);
        return m != null ? m : original;
    }

    private static final String SRC = """
            package %PKG%;

            import java.io.File;
            import java.io.InputStream;
            import java.nio.charset.StandardCharsets;
            import java.security.CodeSource;
            import java.util.jar.JarEntry;
            import java.util.jar.JarFile;

            public final class %CLS% {

                private static final int[] KEY = {%KEY%};
                private static final int[] MAN = {%MAN%};

                public static void verify(Class<?> anchor) {
                    %GUARD%.INSTANCE.ensureInit();
                    boolean located = false;
                    try {
                        CodeSource cs = anchor.getProtectionDomain().getCodeSource();
                        if (cs == null || cs.getLocation() == null) return;
                        JarFile jf = new JarFile(new File(cs.getLocation().toURI()), false);
                        located = true;
                        try {
                            byte[] blob = dec(bytes(MAN), bytes(KEY));
                            java.io.DataInputStream in = new java.io.DataInputStream(
                                    new java.io.ByteArrayInputStream(blob));
                            int n = in.readInt();
                            for (int i = 0; i < n; i++) {
                                int len = in.readInt();
                                byte[] nb = new byte[len];
                                in.readFully(nb);
                                byte[] exp = new byte[32];
                                in.readFully(exp);
                                JarEntry je = jf.getJarEntry(new String(nb, StandardCharsets.UTF_8));
                                if (je == null) { fail(); return; }
                                InputStream is = jf.getInputStream(je);
                                byte[] data = is.readAllBytes();
                                is.close();
                                // hashing, comparison and the verdict are all native
                                if (!%GUARD%.INSTANCE.verifyChunk(data, exp)) { fail(); return; }
                            }
                        } finally {
                            jf.close();
                        }
                    } catch (Throwable t) {
                        // Once the jar was located, a check that cannot complete is
                        // treated as tampering — it must never silently pass.
                        if (located) fail();
                    }
                }

                private static void fail() {
                    try {
                        System.err.println("[Nocturne-Integrity] FATAL: Jar integrity check failed! Code: 0xDEC0DE");
                    } catch (Throwable ignored) {
                    }
                    try {
                        %GUARD%.INSTANCE.panic(0xDEC0DE);
                    } catch (Throwable ignored) {
                    }
                    Runtime.getRuntime().halt(0xDEC0DE);
                }

                private static byte[] bytes(int[] a) {
                    byte[] out = new byte[a.length * 4];
                    for (int i = 0; i < a.length; i++) {
                        for (int j = 0; j < 4; j++) out[i * 4 + j] = (byte) (a[i] >>> (j * 8));
                    }
                    return out;
                }

                private static byte[] dec(byte[] blob, byte[] key) throws Exception {
                    javax.crypto.Cipher c = javax.crypto.Cipher.getInstance(
                            ch(new char[]{'A','E','S','/','C','T','R','/','N','o','P','a','d','d','i','n','g'}));
                    c.init(javax.crypto.Cipher.DECRYPT_MODE,
                            new javax.crypto.spec.SecretKeySpec(key, ch(new char[]{'A','E','S'})),
                            new javax.crypto.spec.IvParameterSpec(blob, 0, 16));
                    byte[] ct = new byte[blob.length - 16];
                    System.arraycopy(blob, 16, ct, 0, ct.length);
                    return c.doFinal(ct);
                }

                private static String ch(char[] a) {
                    return new String(a);
                }
            }
            """;
}

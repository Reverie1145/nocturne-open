package cn.reverie.obf.passes;

import cn.reverie.obf.Ctx;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass 3: string encryption.
 *
 * Every LDC string in scope becomes pool.g(blobId, stringIdx, siteKey).
 * Blobs are AES-CTR encrypted with per-build keys stored as masked int
 * constants; each call site adds a rotating char-level XOR mask. Decoding is
 * lazy per blob and the plaintext cache is wipeable at runtime via
 * cn.reverie.nocturne.StringVault, so live-memory dumps hold ciphertext.
 * Generated pool classes contain no readable string literals (algorithm names
 * are assembled from char constants at runtime).
 */
public final class StringPass {

    private static final int BLOB_STRINGS = 48;

    private static final class PoolSpec {
        String internal;
        final List<List<String>> blobs = new ArrayList<>();
        final boolean deep;

        PoolSpec(boolean deep) {
            this.deep = deep;
        }

        List<String> nextBlob() {
            if (blobs.isEmpty() || blobs.get(blobs.size() - 1).size() >= BLOB_STRINGS) {
                blobs.add(new ArrayList<>());
            }
            return blobs.get(blobs.size() - 1);
        }
    }

    private static final class Site {
        final MethodNode mn;
        final LdcInsnNode ldc;
        final String value;
        PoolSpec pool;
        int blob = -1;
        int idx = -1;
        final int key;

        Site(MethodNode mn, LdcInsnNode ldc, String value, int key) {
            this.mn = mn;
            this.ldc = ldc;
            this.value = value;
            this.key = key;
        }
    }

    private static final java.util.Set<String> STRING_EXCLUDE = java.util.Set.of(
            "cn/reverie/nocturne/StringVault",
            "cn/reverie/nocturne/NativeGuard",
            "cn/reverie/nocturne/NBootstrap",
            "cn/reverie/nocturne/NRes",
            "cn/reverie/nocturne/PackBoot",
            "cn/reverie/nocturne/DecryptingClassLoader"
    );

    public void apply(Ctx ctx) {
        List<PoolSpec> poolsA = new ArrayList<>();
        List<PoolSpec> poolsB = new ArrayList<>();
        List<Site> allSites = new ArrayList<>();
        Map<String, String> newToOld = new HashMap<>();
        for (Map.Entry<String, String> e : ctx.map.classMap.entrySet()) {
            newToOld.put(e.getValue(), e.getKey());
        }

        // ---- collect + assign pools ----
        for (Map.Entry<String, ClassNode> e : ctx.jar.classes.entrySet()) {
            if (ctx.generated.contains(e.getKey())) continue;
            String old = newToOld.getOrDefault(e.getKey(), e.getKey());
            if (STRING_EXCLUDE.contains(old) || STRING_EXCLUDE.contains(e.getKey())) continue;
            List<PoolSpec> family = ctx.opt.isDeep(old) ? poolsB : poolsA;
            int base = Math.floorMod(old.hashCode(), Math.max(1, ctx.opt.stringPools));
            PoolSpec current = ensurePool(family, base);
            for (MethodNode mn : e.getValue().methods) {
                if (mn.instructions == null) continue;
                for (org.objectweb.asm.tree.AbstractInsnNode i = mn.instructions.getFirst();
                     i != null; i = i.getNext()) {
                    if (!(i instanceof LdcInsnNode l) || !(l.cst instanceof String s) || s.isEmpty()) continue;
                    Site site = new Site(mn, l, s, ctx.rng.nextInt());
                    List<String> blob = current.nextBlob();
                    site.pool = current;
                    site.blob = current.blobs.size() - 1;
                    site.idx = blob.size();
                    // store masked: PoolGen.g0 XORs with the same site key at runtime
                    blob.add(mask(s, site.key));
                    allSites.add(site);
                    if (current.blobs.size() >= 8) {
                        current = ensurePool(family, family.size()); // spill forward
                    }
                }
            }
        }

        // ---- emit pools ----
        int poolId = 0;
        for (List<PoolSpec> fam : List.of(poolsA, poolsB)) {
            for (PoolSpec p : fam) {
                if (p.blobs.isEmpty()) continue;
                String pkg = ctx.names.freshPackage();
                p.internal = pkg + "/" + ctx.names.freshName(5, 12);
                ctx.generated.add(p.internal);
                ClassNode cn = PoolGen.emit(p.internal, p.blobs, p.deep, version(ctx), vaultInternal(ctx),
                        ctx, poolId, guardInternal(ctx));
                poolId++;
                byte[] bytes = PoolGen.write(cn, ctx.jar);
                ctx.jar.classes.put(p.internal, cn);
                ctx.jar.entries.put(p.internal + ".class", bytes);
                ctx.jar.libClasses.add(p.internal);
            }
        }

        // ---- rewrite sites ----
        int n = 0;
        for (Site s : allSites) {
            InsnList il = new InsnList();
            PoolGen.pushInt(il, s.blob);
            PoolGen.pushInt(il, s.idx);
            il.add(new LdcInsnNode(s.key));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, s.pool.internal, "g",
                    "(III)Ljava/lang/String;", false));
            s.mn.instructions.insert(s.ldc, il);
            s.mn.instructions.remove(s.ldc);
            s.mn.maxStack = Math.max(s.mn.maxStack == -1 ? 0 : s.mn.maxStack,
                    (s.mn.maxStack == -1 ? 0 : s.mn.maxStack) + 2); // 3 pushed ints vs 1 pushed ref
            n++;
        }
        ctx.log("[strings] encrypted " + n + " strings into " + (poolsA.size() + poolsB.size()) + " pools");
    }

    private static PoolSpec ensurePool(List<PoolSpec> family, int index) {
        while (family.size() <= index) family.add(new PoolSpec(false));
        PoolSpec p = family.get(index);
        return p;
    }

    private static int version(Ctx ctx) {
        for (ClassNode cn : ctx.jar.classes.values()) {
            if (cn.version >= Opcodes.V1_8) return cn.version;
        }
        return Opcodes.V17;
    }

    private static String vaultInternal(Ctx ctx) {
        String m = ctx.map.classMap.get("cn/reverie/nocturne/StringVault");
        return m != null ? m : "cn/reverie/nocturne/StringVault";
    }

    private static String guardInternal(Ctx ctx) {
        String m = ctx.map.classMap.get("cn/reverie/nocturne/NativeGuard");
        return m != null ? m : "cn/reverie/nocturne/NativeGuard";
    }

    /** Build-time twin of the runtime mask — see PoolGen.g. */
    public static String mask(String s, int k) {
        char[] c = s.toCharArray();
        for (int j = 0; j < c.length; j++) {
            c[j] ^= (char) (Integer.rotateLeft(k, j & 7) & 0xFFFF);
        }
        return new String(c);
    }

    @SuppressWarnings("unused")
    private static void touch(Label l) {
    }

    @SuppressWarnings("unused")
    private static byte[] utf(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}

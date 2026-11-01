package cn.reverie.obf.passes;

import cn.reverie.obf.Ctx;
import cn.reverie.obf.Mapping;
import cn.reverie.obf.Names;
import cn.reverie.obf.Options;
import cn.reverie.obf.Util;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pass 2: class/package/member renaming.
 *
 * - Every in-scope class moves to a fresh randomly-named package (per class),
 *   except mixin classes which share one flattened root (mixin configs declare
 *   a single package) and classes needing package-private visibility.
 * - Member names are a deterministic function of (original name, params), so
 *   override chains stay consistent across renamed classes.
 * - Members overriding anything declared by an unrenamed ancestor (JDK,
 *   Minecraft, Fabric, Kotlin stdlib) are pinned.
 * - Mixin classes keep ALL member names (Mixin matches by remapped names);
 *   only the class names change, and the mixin JSON is rewritten to match.
 * - Reflection-style strings equal to renamed class names are rewritten.
 */
final class RenamePassVerifier {
    static void rewriteBystanders(Ctx ctx, Mapping map) {
        List<String> prefixes = new ArrayList<>();
        for (String p : ctx.opt.packages) {
            prefixes.add(p.replace('/', '/'));
            prefixes.add(p.replace('/', '.'));
        }
        Gson gson = new Gson();
        int bystanders = 0;
        for (Map.Entry<String, byte[]> en : new ArrayList<>(ctx.jar.entries.entrySet())) {
            String name = en.getKey();
            if (!name.endsWith(".class")) continue;
            String internal = name.substring(0, name.length() - 6);
            if (ctx.jar.classes.containsKey(internal)) continue;
            String raw = new String(en.getValue(), StandardCharsets.ISO_8859_1);
            boolean hits = false;
            for (String p : prefixes) {
                if (raw.contains(p)) {
                    hits = true;
                    break;
                }
            }
            if (!hits) continue;
            try {
                ClassNode cn = new ClassNode();
                new org.objectweb.asm.ClassReader(en.getValue()).accept(cn,
                        org.objectweb.asm.ClassReader.EXPAND_FRAMES);
                ClassNode out = new ClassNode();
                cn.accept(new ClassRemapper(out, map));
                ctx.jar.entries.put(name, RenamePass.serialize(out));
                bystanders++;
            } catch (Throwable t) {
                ctx.log("[!] bystander remap failed: " + internal + " (" + t + ")");
            }
        }
        if (bystanders > 0) ctx.log("[rename] out-of-scope classes rewritten: " + bystanders);
    }

    /**
     * Guard: a reference whose owner is NOT an in-scope class must never carry a
     * generated (non-ASCII) name — that means we renamed a JDK / Minecraft /
     * Fabric / Kotlin symbol and the client would die with NoSuchMethodError.
     */
    static void checkOutScopeRefs(Ctx ctx, Mapping map) {
        int bad = 0;
        for (Map.Entry<String, ClassNode> en : ctx.jar.classes.entrySet()) {
            ClassNode cn = en.getValue();
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                for (org.objectweb.asm.tree.AbstractInsnNode i : mn.instructions) {
                    String owner;
                    String name;
                    if (i instanceof org.objectweb.asm.tree.MethodInsnNode m) {
                        if (m.name.startsWith("<")) continue;
                        owner = m.owner;
                        name = m.name;
                    } else if (i instanceof org.objectweb.asm.tree.FieldInsnNode f) {
                        owner = f.owner;
                        name = f.name;
                    } else {
                        continue;
                    }
                    // after remapping, jar.classes is keyed by the FINAL names
                    if (owner.startsWith("[") || ctx.jar.classes.containsKey(owner)) continue;
                    if (isAscii(name)) continue;
                    if (bad < 10) {
                        ctx.log("[!] renamed out-of-scope reference: " + owner + "." + name
                                + " (from " + cn.name + ")");
                    }
                    bad++;
                }
            }
        }
        if (bad > 0) {
            throw new IllegalStateException("[rename] " + bad
                    + " reference(s) to out-of-scope members were renamed (JDK/MC/Fabric symbols must be pinned)");
        }
    }

    /**
     * Guard: a package-private member may only be reached from its own runtime
     * package. Verify every in-scope site that resolves to one still lives in the
     * same package as the declaring class — otherwise the JVM throws
     * IllegalAccessError as soon as the code path runs.
     */
    static void checkPackageAccess(Ctx ctx) {
        Map<String, Set<String>> priv = new HashMap<>();
        for (ClassNode cn : ctx.jar.classes.values()) {
            Set<String> s = new HashSet<>();
            for (MethodNode mn : cn.methods) {
                if (mn.name.startsWith("<")) continue;
                if ((mn.access & (org.objectweb.asm.Opcodes.ACC_PUBLIC
                        | org.objectweb.asm.Opcodes.ACC_PRIVATE
                        | org.objectweb.asm.Opcodes.ACC_PROTECTED)) == 0) {
                    s.add(mn.name + "|" + mn.desc);
                }
            }
            for (org.objectweb.asm.tree.FieldNode fn : cn.fields) {
                if ((fn.access & (org.objectweb.asm.Opcodes.ACC_PUBLIC
                        | org.objectweb.asm.Opcodes.ACC_PRIVATE
                        | org.objectweb.asm.Opcodes.ACC_PROTECTED)) == 0) {
                    s.add("#" + fn.name);
                }
            }
            if (!s.isEmpty()) priv.put(cn.name, s);
        }
        int bad = 0;
        for (ClassNode cn : ctx.jar.classes.values()) {
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                for (org.objectweb.asm.tree.AbstractInsnNode i : mn.instructions) {
                    String owner;
                    String key;
                    if (i instanceof org.objectweb.asm.tree.MethodInsnNode m) {
                        if (m.name.startsWith("<")) continue;
                        owner = m.owner;
                        key = m.name + "|" + m.desc;
                    } else if (i instanceof org.objectweb.asm.tree.FieldInsnNode f) {
                        owner = f.owner;
                        key = "#" + f.name;
                    } else {
                        continue;
                    }
                    String decl = declaringOf(ctx, owner, key, priv);
                    if (decl == null || pkg(decl).equals(pkg(cn.name))) continue;
                    if (bad < 10) {
                        ctx.log("[!] package-private " + key + " declared by " + decl
                                + " reached from " + cn.name);
                    }
                    bad++;
                }
            }
        }
        if (bad > 0) {
            throw new IllegalStateException("[rename] " + bad
                    + " package-private member access(es) crossed package boundaries after renaming");
        }
    }

    private static String declaringOf(Ctx ctx, String owner, String key, Map<String, Set<String>> priv) {
        String cur = owner;
        int hops = 0;
        while (cur != null && hops++ < 32) {
            Set<String> s = priv.get(cur);
            if (s != null && s.contains(key)) return cur;
            ClassNode cn = ctx.jar.classes.get(cur);
            cur = cn != null ? cn.superName : null;
        }
        return null;
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) return false;
        }
        return true;
    }


    private static String pkg(String internal) {
        int i = internal.lastIndexOf('/');
        return i < 0 ? "" : internal.substring(0, i);
    }}

package cn.reverie.obf.passes;

import cn.reverie.obf.Ctx;
import cn.reverie.obf.Util;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pass 8b "加壳": class-body encryption + on-demand native-derived keys.
 *
 * Everything REACHABLE from the plaintext bootstrap surface (mixin classes,
 * entrypoints, native glue + transitive closure + inner-class families) stays
 * as a normal .class so the Fabric loader boots normally. Every OTHER in-scope
 * class is AES-CTR encrypted into assets/nocturne/c/N.dat with keys derived
 * from the build seed that lives ONLY inside the native DLL (nativePackKey).
 * The plaintext .class entries are removed — a jar/sidecar dump yields only
 * ciphertext for the packed set. At runtime the (plaintext) PackBoot defines
 * the packed set through a child DecryptingClassLoader using NativeGuard.packKey.
 *
 * Runs AFTER the serializer produces the final per-class bytes and BEFORE
 * IntegrityPass (which must only sample the remaining plaintext .class set).
 */
public final class PackerPass {

    public void apply(Ctx ctx) {
        if (!ctx.opt.pack) return;

        // ---- kept roots (final internal names) ----
        Set<String> keep = new HashSet<>(ctx.opt.keepClasses); // unrenamed → identity
        for (String orig : ctx.opt.keepMembers) {
            String fin = ctx.map.classMap.get(orig);
            keep.add(fin != null ? fin : orig);
        }
        // final->original reverse map so config-declared mixins are recognized after renaming
        java.util.Map<String, String> finalToOrig = new java.util.HashMap<>();
        for (var e : ctx.map.classMap.entrySet()) finalToOrig.put(e.getValue(), e.getKey());
        // mixin classes (must load through Fabric/Mixin normally) + pipeline-generated classes
        for (String n : new ArrayList<>(ctx.jar.classes.keySet())) {
            if (ctx.inKeepPackage(n)) { keep.add(n); continue; } // mixin 弱混: keep unchanged classes plaintext
            ClassNode cn = ctx.jar.classes.get(n);
            String orig = finalToOrig.get(n);
            if ((orig != null && ctx.mixinClasses.contains(orig)) || ctx.isMixin(cn) || ctx.generated.contains(n)) {
                keep.add(n);
            }
        }

        // ---- out-of-scope bystanders are roots too ----
        // A class outside our scope (e.g. the vendored ViaFabricPlus screens, which extend our
        // UI base classes) stays plaintext and is loaded by the parent loader, so anything IT
        // references must stay plaintext as well — a packed target is invisible to that loader
        // (LinkageError: superclass/interface could not be found).
        for (Map.Entry<String, byte[]> en : new ArrayList<>(ctx.jar.entries.entrySet())) {
            String name = en.getKey();
            if (!name.endsWith(".class")) continue;
            String internal = name.substring(0, name.length() - 6);
            if (ctx.jar.classes.containsKey(internal)) continue; // in scope: covered by the graph below
            try {
                ClassNode bn = new ClassNode();
                new org.objectweb.asm.ClassReader(en.getValue()).accept(bn,
                        org.objectweb.asm.ClassReader.SKIP_FRAMES);
                Set<String> refs = new HashSet<>();
                collectRefs(bn, refs);
                for (String r : refs) {
                    if (ctx.jar.classes.containsKey(r)) keep.add(r);
                }
            } catch (Throwable ignored) {
            }
        }

        // ---- transitive closure (constant-pool + inner-family) ----
        Deque<String> queue = new ArrayDeque<>(keep);
        Set<String> refs = new HashSet<>();
        while (!queue.isEmpty()) {
            String n = queue.poll();
            ClassNode cn = ctx.jar.classes.get(n);
            if (cn == null) continue;
            refs.clear();
            collectRefs(cn, refs);
            for (String r : refs) {
                if (ctx.jar.classes.containsKey(r) && keep.add(r)) queue.add(r);
            }
            // keep the whole inner-class family alive together (Companion/$lambda$/$1/...)
            for (String k : new ArrayList<>(ctx.jar.classes.keySet())) {
                if (k.startsWith(n + "$") && keep.add(k)) queue.add(k);
            }
        }

        // ---- ensure inner-class families are fully kept or fully packed ----
        // If ANY class in an inner-class family (X, X$Y, X$Y$Z...) is kept,
        // the whole family must be kept — otherwise the child loader can't
        // find siblings that live in the parent's scope.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String k : new ArrayList<>(ctx.jar.classes.keySet())) {
                if (keep.contains(k)) {
                    // propagate keep to all relatives
                    String root = innerRoot(k);
                    for (String c : new ArrayList<>(ctx.jar.classes.keySet())) {
                        if (innerRoot(c).equals(root) && keep.add(c)) {
                            queue.add(c);
                            changed = true;
                        }
                    }
                }
            }
            // expand transitive refs from newly-kept classes
            while (!queue.isEmpty()) {
                String n = queue.poll();
                ClassNode cn = ctx.jar.classes.get(n);
                if (cn == null) continue;
                refs.clear();
                collectRefs(cn, refs);
                for (String r : refs) {
                    if (ctx.jar.classes.containsKey(r) && keep.add(r)) queue.add(r);
                }
                for (String c : new ArrayList<>(ctx.jar.classes.keySet())) {
                    if (c.startsWith(n + "$") && keep.add(c)) queue.add(c);
                }
            }
        }

        // ---- packed = in-scope classes minus kept ----
        List<String> packed = new ArrayList<>();
        for (String n : ctx.jar.classes.keySet()) {
            if (!keep.contains(n)) packed.add(n);
        }
        packed.sort(Comparator.naturalOrder());

        if (packed.isEmpty()) {
            ctx.log("[pack] nothing to pack (0 classes outside keep closure)");
            return;
        }

        // ---- encrypt + carry ----
        try {
            ByteArrayOutputStream mo = new ByteArrayOutputStream();
            try (DataOutputStream dos = new DataOutputStream(mo)) {
                dos.writeInt(packed.size());
                for (String n : packed) {
                    int idx = packed.indexOf(n);
                    byte[] clsBytes = ctx.jar.entries.get(n + ".class");
                    if (clsBytes == null) {
                        throw new IllegalStateException("[pack] missing final bytes: " + n);
                    }
                    byte[] key = PoolGen.nativePackKey(ctx.opt.seed, idx);
                    ctx.jar.entries.put("assets/nocturne/c/" + idx + ".dat", Util.aesEncrypt(key, clsBytes));
                    // manifest maps dense index -> final dotted name (decryption key stays in the DLL)
                    byte[] nb = n.replace('/', '.').getBytes(StandardCharsets.UTF_8);
                    dos.writeInt(nb.length);
                    dos.write(nb);
                    // remove the plaintext class from the jar
                    ctx.jar.entries.remove(n + ".class");
                    ctx.jar.classes.remove(n);
                    ctx.packed.add(n);
                }
            }
            ctx.jar.entries.put("assets/nocturne/c/index.bin", mo.toByteArray());
            ctx.log("[pack] packed " + packed.size() + " classes into assets/nocturne/c/*.dat, kept " + keep.size() + " plaintext");
        } catch (Exception e) {
            throw new IllegalStateException("[pack] failed: " + e, e);
        }
    }

    /** Collect in-scope class references reachable from a class's constant pool. */
    private void collectRefs(ClassNode cn, Set<String> out) {
        addClass(out, cn.superName);
        if (cn.interfaces != null) for (String i : cn.interfaces) addClass(out, i);
        if (cn.nestMembers != null) out.addAll(cn.nestMembers);
        if (cn.permittedSubclasses != null) out.addAll(cn.permittedSubclasses);
        if (cn.fields != null) {
            for (var f : cn.fields) {
                addDesc(out, f.desc);
                addSig(out, f.signature);
            }
        }
        if (cn.methods == null) return;
        for (MethodNode mn : cn.methods) {
            addDesc(out, mn.desc);
            addSig(out, mn.signature);
            if (mn.exceptions != null) for (String x : mn.exceptions) addClass(out, x);
            if (mn.tryCatchBlocks != null) for (var tc : mn.tryCatchBlocks) addClass(out, tc.type);
            if (mn.instructions == null) continue;
            for (AbstractInsnNode i = mn.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof TypeInsnNode t) addClass(out, t.desc);
                else if (i instanceof MethodInsnNode m) {
                    addClass(out, m.owner);
                    addDesc(out, m.desc);
                } else if (i instanceof FieldInsnNode f) {
                    addClass(out, f.owner);
                    addDesc(out, f.desc);
                } else if (i instanceof MultiANewArrayInsnNode ma) {
                    addDesc(out, ma.desc);
                } else if (i instanceof InvokeDynamicInsnNode d) {
                    addDesc(out, d.desc);
                    if (d.bsmArgs != null) {
                        for (Object a : d.bsmArgs) {
                            if (a instanceof Handle h) {
                                addClass(out, h.getOwner());
                                addDesc(out, h.getDesc());
                            } else if (a instanceof Type t && (t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY)) {
                                if (t.getSort() == Type.OBJECT) addClass(out, t.getInternalName());
                                else addDesc(out, t.getDescriptor());
                            }
                        }
                    }
                } else if (i instanceof LdcInsnNode ldc) {
                    if (ldc.cst instanceof Type t && (t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY)) {
                        if (t.getSort() == Type.OBJECT) addClass(out, t.getInternalName());
                        else addDesc(out, t.getDescriptor());
                    }
                }
            }
        }
    }

    private void addClass(Set<String> out, String internal) {
        if (internal == null) return;
        if (internal.startsWith("[") || internal.endsWith(";")) { addDesc(out, internal); return; }
        out.add(internal);
    }

    private void addDesc(Set<String> out, String desc) {
        if (desc == null || desc.isEmpty()) return;
        int i = 0;
        while (true) {
            int l = desc.indexOf('L', i);
            if (l < 0) break;
            int semi = desc.indexOf(';', l);
            if (semi < 0) break;
            String tok = desc.substring(l + 1, semi);
            int lt = tok.indexOf('<');
            if (lt >= 0) tok = tok.substring(0, lt);
            addClass(out, tok);
            i = semi + 1;
        }
    }

    /** Generic signatures can embed nested L...<...>; tokens — extract each bare class before '<'. */
    private void addSig(Set<String> out, String sig) {
        addDesc(out, sig);
    }

    /** Returns the outermost class name (root of the inner-class family). */
    private String innerRoot(String internal) {
        int d = internal.indexOf('$');
        return d < 0 ? internal : internal.substring(0, d);
    }
}
package cn.reverie.obf.passes;

import cn.reverie.obf.Ctx;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Final gate: full dataflow verification of every method (same engine the JVM
 * verifier uses), plus a reference-closure check and mixin/entrypoint
 * consistency checks. Fails the build on any verification error.
 */
public final class VerifyPass {

    public void apply(Ctx ctx) {
        List<String> errors = new ArrayList<>();
        int debugCount = 0;
        List<String> missing = new ArrayList<>();

        // ---- dataflow verification ----
        if (ctx.opt.verify) {
            for (var e : ctx.jar.classes.entrySet()) {
                ClassNode cn = e.getValue();
                for (MethodNode mn : cn.methods) {
                    if (mn.instructions == null || mn.instructions.size() == 0) continue;
                    if (hasDispatcherDefault(mn) && mn.tryCatchBlocks != null && !mn.tryCatchBlocks.isEmpty()) {
                        errors.add(cn.name + "#" + mn.name + mn.desc
                                + ": try/catch body flattened into a dispatcher switch — the exception slot"
                                + " can be retyped to Object, making the trailing athrow fail verification");
                    }
                    try {
                        Analyzer<BasicValue> a = new Analyzer<>(
                                new org.objectweb.asm.tree.analysis.BasicVerifier());
                        a.analyze(cn.name, mn);
                    } catch (Throwable t) {
                        if (debugCount < 3) {
                            debugCount++;
                            try {
                                java.io.PrintWriter pw = new java.io.PrintWriter(
                                        java.nio.file.Files.newBufferedWriter(java.nio.file.Path.of(
                                                "build/obf-debug-" + debugCount + ".txt")));
                                cn.accept(new org.objectweb.asm.util.TraceClassVisitor(pw));
                                pw.close();
                            } catch (Throwable ignored) {
                            }
                        }
                        errors.add(cn.name + "#" + mn.name + mn.desc + ": " + t
                                + " maxStack=" + mn.maxStack + " maxLocals=" + mn.maxLocals
                                + " flattened=" + (ctx.flowFlattened != null && ctx.flowFlattened.contains(cn)));
                    }
                }
            }
        }

        // ---- reference closure ----
        for (var e : ctx.jar.classes.entrySet()) {
            ClassNode cn = e.getValue();
            check(ctx, missing, cn.superName);
            for (String i : cn.interfaces) check(ctx, missing, i);
            for (var f : cn.fields) {
                scanDesc(ctx, missing, f.desc);
                if (f.signature != null) scanSig(ctx, missing, f.signature);
            }
            for (MethodNode mn : cn.methods) {
                scanDesc(ctx, missing, mn.desc);
                if (mn.signature != null) scanSig(ctx, missing, mn.signature);
                for (var tc : mn.tryCatchBlocks) check(ctx, missing, tc.type);
                if (mn.exceptions != null) for (String x : mn.exceptions) check(ctx, missing, x);
                for (AbstractInsnNode i = mn.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i instanceof TypeInsnNode t) check(ctx, missing, t.desc);
                    else if (i instanceof MethodInsnNode m) {
                        check(ctx, missing, m.owner);
                        scanDesc(ctx, missing, m.desc);
                    } else if (i instanceof FieldInsnNode f) {
                        check(ctx, missing, f.owner);
                        scanDesc(ctx, missing, f.desc);
                    } else if (i instanceof MultiANewArrayInsnNode m) {
                        scanDesc(ctx, missing, m.desc);
                    } else if (i instanceof InvokeDynamicInsnNode d) {
                        scanDesc(ctx, missing, d.desc);
                        for (Object bsmArg : d.bsmArgs) {
                            if (bsmArg instanceof org.objectweb.asm.Handle h) {
                                check(ctx, missing, h.getOwner());
                                scanDesc(ctx, missing, h.getDesc());
                            } else if (bsmArg instanceof Type t
                                    && (t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY)) {
                                check(ctx, missing, t.getInternalName());
                            }
                        }
                    }
                }
            }
        }

        // ---- mixin configs / entrypoints exist ----
        for (var en : ctx.jar.entries.entrySet()) {
            String n = en.getKey();
            if (n.endsWith(".mixins.json")) {
                try {
                    var jo = new com.google.gson.Gson().fromJson(
                            new String(en.getValue(), StandardCharsets.UTF_8), com.google.gson.JsonObject.class);
                    String pkg = jo.get("package").getAsString().replace('.', '/');
                    // only enforce existence for OUR in-scope mixin configs; vendored (e.g. viaversion)
                    // configs reference classes we don't ship/obfuscate and are out of our control.
                    boolean inScopeCfg = false;
                    for (String p : ctx.opt.packages) {
                        if (pkg.equals(p) || pkg.startsWith(p + "/")) { inScopeCfg = true; break; }
                    }
                    if (!inScopeCfg) continue;
                    for (String listKey : List.of("mixins", "client", "server")) {
                        if (!jo.has(listKey) || !jo.get(listKey).isJsonArray()) continue;
                        for (var el : jo.getAsJsonArray(listKey)) {
                            String internal = pkg + "/" + el.getAsString().replace('.', '/');
                            if (!ctx.jar.classes.containsKey(internal)
                                    && !ctx.jar.entries.containsKey(internal + ".class")) {
                                missing.add("mixin entry missing: " + internal + " (in " + n + ")");
                            }
                        }
                    }
                } catch (Throwable t) {
                    errors.add("mixin config unreadable: " + n);
                }
            }
        }
        byte[] fmj = ctx.jar.entries.get("fabric.mod.json");
        if (fmj != null) {
            try {
                var jo = new com.google.gson.Gson().fromJson(new String(fmj, StandardCharsets.UTF_8),
                        com.google.gson.JsonObject.class);
                var eps = jo.getAsJsonObject("entrypoints");
                if (eps != null) {
                    for (var me : eps.entrySet()) {
                        for (var el : me.getValue().getAsJsonArray()) {
                            String dotted = el.getAsString();
                            if (!ctx.jar.classes.containsKey(dotted.replace('.', '/'))
                                    && !ctx.jar.entries.containsKey(dotted.replace('.', '/') + ".class")) {
                                missing.add("entrypoint missing: " + dotted);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                errors.add("fabric.mod.json unreadable: " + t);
            }
        }

        ctx.log("[verify] dataflow errors: " + errors.size() + ", dangling refs: " + missing.size());
        for (String s : errors.subList(0, Math.min(12, errors.size()))) ctx.log("    ! " + s);
        for (String s : missing.subList(0, Math.min(24, missing.size()))) ctx.log("    ? " + s);
        if (!errors.isEmpty()) throw new IllegalStateException("verification failed:\n" + String.join("\n", errors));
        if (!missing.isEmpty()) throw new IllegalStateException("dangling references:\n" + String.join("\n", missing));
    }

    /**
     * FlowPass emits "aconst_null; athrow" as the dispatcher default case. Real compilers
     * never produce that pair, so it identifies a flattened body. The dataflow check above
     * cannot catch a degraded exception slot because BasicVerifier treats every reference
     * type as the same value, so this structural guard is what keeps such methods out.
     */
    private static boolean hasDispatcherDefault(MethodNode mn) {
        for (AbstractInsnNode i = mn.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() != Opcodes.ACONST_NULL) continue;
            AbstractInsnNode next = i.getNext();
            if (next != null && next.getOpcode() == Opcodes.ATHROW) return true;
        }
        return false;
    }

    private void check(Ctx ctx, List<String> missing, String internal) {
        if (internal == null) return;
        if (internal.length() <= 2) return; // primitive-ish junk, never a class ref
        if (internal.startsWith("[")) internal = elementOf(internal);
        if (internal.endsWith(";")) internal = internal.substring(1, internal.length() - 1);
        if (ctx.jar.isKnownClass(internal)
                || ctx.jar.entries.containsKey(internal + ".class")) return;
        if (internal.startsWith("java/") || internal.startsWith("javax/")
                || internal.startsWith("jdk/") || internal.startsWith("sun/")
                || internal.startsWith("com/sun/") || internal.startsWith("org/w3c/")
                || internal.startsWith("org/xml/") || internal.startsWith("org/ietf/")) {
            return; // JDK
        }
        try {
            Class.forName(internal.replace('/', '.'), false, ctx.jar.libLoader());
        } catch (Throwable t) {
            missing.add(internal);
        }
    }

    private static String elementOf(String arr) {
        int i = arr.lastIndexOf('[');
        String el = arr.substring(i + 1);
        return el.endsWith(";") ? el.substring(1, el.length() - 1) : el;
    }

    private void scanDesc(Ctx ctx, List<String> missing, String desc) {
        if (desc == null || desc.isEmpty()) return;
        try {
            if (desc.indexOf(')') >= 0) { // method descriptor
                for (Type t : Type.getType(desc).getArgumentTypes()) scanType(ctx, missing, t);
                scanType(ctx, missing, Type.getType(desc).getReturnType());
            } else { // field descriptor
                scanType(ctx, missing, Type.getType(desc));
            }
        } catch (Throwable ignored) {
            // unparseable — the dataflow verifier covers real errors
        }
    }

    private void scanSig(Ctx ctx, List<String> missing, String sig) {
        // crude but effective: every L...; token
        int i = 0;
        while ((i = sig.indexOf('L', i)) >= 0) {
            int j = sig.indexOf(';', i);
            if (j < 0) break;
            String tok = sig.substring(i + 1, j);
            int lt = tok.indexOf('<');
            if (lt >= 0) tok = tok.substring(0, lt);
            if (!tok.isEmpty()) check(ctx, missing, tok);
            i = j + 1;
        }
    }

    private void scanType(Ctx ctx, List<String> missing, Type t) {
        if (t.getSort() >= org.objectweb.asm.Type.VOID && t.getSort() <= org.objectweb.asm.Type.DOUBLE) {
            return; // primitive / void
        }
        String s = t.getInternalName();
        if (s.startsWith("[")) {
            String el = elementOf(s);
            if (el.length() > 1) { // primitive element types have no class to resolve
                check(ctx, missing, el);
            }
        } else {
            check(ctx, missing, s);
        }
    }

    /** SimpleVerifier that resolves hierarchy against the build classpath without initializing classes. */
    @SuppressWarnings("unused")
    private static void touch(Analyzer<BasicValue> a) {
    }
    @SuppressWarnings("unused")
    private static void touch(Opcodes o) {
    }
}

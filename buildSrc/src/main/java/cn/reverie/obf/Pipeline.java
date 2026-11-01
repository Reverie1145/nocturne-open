package cn.reverie.obf;

import cn.reverie.obf.passes.FlowPass;
import cn.reverie.obf.passes.IntegrityPass;
import cn.reverie.obf.passes.NumberPass;
import cn.reverie.obf.passes.PackerPass;
import cn.reverie.obf.passes.RefPass;
import cn.reverie.obf.passes.RenamePass;
import cn.reverie.obf.passes.StringPass;
import cn.reverie.obf.passes.StripPass;
import cn.reverie.obf.passes.TrapPass;
import cn.reverie.obf.passes.VerifyPass;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Full protection pipeline: strip → rename → strings → numbers → flow → refs → traps → integrity → verify. */
public final class Pipeline {

    private Pipeline() {
    }

    public static void run(Path inJar, Path outJar, List<Path> libraries, Path config,
                           Path nativeCore, Path nativeLoader, String mcVersion, Path reportOut) throws Exception {
        long t0 = System.currentTimeMillis();
        Options opt = Options.load(config);
        JarSet jar = new JarSet();
        jar.readJar(inJar, opt);
        jar.addLibraries(libraries);

        Ctx ctx = new Ctx(opt, jar, libraries);
        ctx.inJar = inJar;
        // Exact-version Minecraft jar only: the member table built from it decides
        // which overrides are pinned, so a wrong version silently breaks overrides.
        List<Path> mcJars = JarSet.discoverMinecraftJars(mcVersion);
        if (!mcJars.isEmpty()) {
            jar.addLibraries(mcJars);
            ctx.log("[libs] + " + mcJars.size() + " minecraft jar(s) for hierarchy resolution: " + mcJars);
        } else {
            ctx.log("[libs] WARNING: no minecraft jar found for version '" + mcVersion
                    + "' — override pinning will be incomplete");
        }

        new StripPass().apply(ctx);
        checkLists(jar, "after strip", ctx);
        new RenamePass().apply(ctx);
        checkLists(jar, "after rename", ctx);

        // dump renamed classes for generated-class compilation (StringVault is
        // referenced by generated pools under its renamed name)
        try {
            Path dump = outJar.getParent().resolve("obf-cp");
            if (java.nio.file.Files.exists(dump)) {
                try (var walk = java.nio.file.Files.walk(dump)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try {
                            java.nio.file.Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
            java.nio.file.Files.createDirectories(dump);
            for (var e : jar.classes.entrySet()) {
                Path f = dump.resolve(e.getKey() + ".class");
                java.nio.file.Files.createDirectories(f.getParent());
                java.nio.file.Files.write(f, RenamePass.serialize(e.getValue()));
            }
            ctx.classDumpDir = dump;
        } catch (Exception e) {
            ctx.log("[!] class dump for compile classpath failed: " + e);
        }

        new StringPass().apply(ctx);
        new NumberPass().apply(ctx);
        checkLists(jar, "after numbers", ctx);
        FlowPass flow = new FlowPass();
        flow.apply(ctx);
        ctx.flowFlattened = flow.flattened;
        checkLists(jar, "after flow", ctx);
        new RefPass().apply(ctx);
        checkLists(jar, "after refs", ctx);
        new TrapPass().apply(ctx);
        checkLists(jar, "after traps", ctx);

        // ---- serialize final class bytes ----
        for (var e : jar.classes.entrySet()) {
            ClassNode cn = e.getValue();
            byte[] bytes;
            try {
                if (flow.flattened.contains(cn)) {
                    // pre-check dataflow so frame-conflicting classes fall back
                    String err = dataflowError(cn);
                    if (err != null) {
                        throw new IllegalStateException("dataflow: " + err);
                    }
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                        @Override
                        protected String getCommonSuperClass(String a, String b) {
                            return jar.commonSuper(a, b);
                        }
                    };
                    cn.accept(cw);
                    bytes = cw.toByteArray();
                } else {
                    bytes = RenamePass.serialize(cn);
                }
            } catch (Throwable t) {
                // frame conflict somewhere in this flattened class — bisect to
                // per-method granularity: a flattened body is kept only if the
                // class with just that body flattened passes the real writer
                byte[] fallback = jar.entries.get(e.getKey() + ".class");
                if (fallback == null) {
                    throw new IllegalStateException("serialize failed: " + e.getKey() + ": " + t, t);
                }
                ClassNode base = new ClassNode();
                new org.objectweb.asm.ClassReader(fallback).accept(base,
                        org.objectweb.asm.ClassReader.EXPAND_FRAMES);
                java.util.Map<String, org.objectweb.asm.tree.MethodNode> flatById = new java.util.HashMap<>();
                for (org.objectweb.asm.tree.MethodNode bm : cn.methods) {
                    if (flow.flattenedMethods.contains(bm)) {
                        flatById.put(bm.name + bm.desc, bm);
                    }
                }
                int kept = 0, reverted = 0;
                int debugDumps = 0;
                StringBuilder culpritNames = new StringBuilder();
                for (org.objectweb.asm.tree.MethodNode fm : new java.util.ArrayList<>(base.methods)) {
                    org.objectweb.asm.tree.MethodNode bm = flatById.get(fm.name + fm.desc);
                    if (bm == null) continue;
                    // isolation test: base + only this flattened body
                    base.methods.remove(fm);
                    base.methods.add(bm);
                    if (writeWithFrames(base, jar) != null && dataflowError(base) == null) {
                        kept++;
                        flow.flattenedMethods.add(bm);
                    } else {
                        if (debugDumps < 4) {
                            debugDumps++;
                            try {
                                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.OutputStreamWriter(
                                        java.nio.file.Files.newOutputStream(java.nio.file.Path.of(
                                                "C:/Users/24033/Desktop/Nocturne/apps/client/build/flat-fail-" + debugDumps + ".txt")),
                                        java.nio.charset.StandardCharsets.UTF_8));
                                ClassNode mini = new ClassNode();
                                mini.name = base.name;
                                mini.superName = base.superName;
                                mini.version = base.version;
                                mini.methods.add(bm);
                                mini.accept(new org.objectweb.asm.util.TraceClassVisitor(pw));
                                pw.close();
                            } catch (Throwable ignored) {
                            }
                        }
                        base.methods.remove(bm);
                        base.methods.add(fm);
                        reverted++;
                        flow.flattenedMethods.remove(bm);
                        if (culpritNames.length() < 60) {
                            culpritNames.append(culpritNames.length() == 0 ? "" : ",").append(fm.name);
                        }
                        try {
                            java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.OutputStreamWriter(
                                    java.nio.file.Files.newOutputStream(java.nio.file.Path.of(
                                            "C:/Users/24033/Desktop/Nocturne/apps/client/build/flow-debug-" + Integer.toHexString(e.getKey().hashCode()) + "-" + Integer.toHexString(fm.name.hashCode())
                                                    + ".txt")),
                                    java.nio.charset.StandardCharsets.UTF_8));
                            base.accept(new org.objectweb.asm.util.TraceClassVisitor(pw));
                            pw.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
                flow.flattened.remove(cn);
                byte[] result = writeWithFrames(base, jar);
                if (result == null || dataflowError(base) != null) {
                    result = fallback; // interaction conflict — full revert for this class
                } else {
                    flow.flattened.add(base);
                    jar.classes.put(e.getKey(), base);
                }
                if (reverted > 0) {
                    ctx.log("[flow] reverted " + reverted + "/" + (kept + reverted)
                            + " flattened methods in " + e.getKey() + " (" + culpritNames + ")");
                }
                bytes = result;
            }
            jar.entries.put(e.getKey() + ".class", bytes);
        }

        // ---- class-body packing (加壳): encrypt unreachable-in-scope classes ----
        new PackerPass().apply(ctx);

        // ---- native payload bundling ----
        if (opt.native_.bundle) {
            if (nativeCore != null && Files.exists(nativeCore)) {
                jar.entries.put(opt.native_.resCore,
                        Util.aesEncrypt(bundleKey(opt), nativeHeader(nativeCore)));
                ctx.log("[native] bundled core -> " + opt.native_.resCore);
            } else {
                ctx.log("[native] WARNING: native core payload missing, bundling skipped");
            }
            if (nativeLoader != null && Files.exists(nativeLoader)) {
                jar.entries.put(opt.native_.resLoader,
                        Util.aesEncrypt(bundleKey(opt), nativeHeader(nativeLoader)));
                ctx.log("[native] bundled loader -> " + opt.native_.resLoader);
            } else {
                ctx.log("[native] WARNING: native loader payload missing, bundling skipped");
            }
        }

        // ---- integrity (needs final bytes) ----
        new IntegrityPass().apply(ctx, true);

        // ---- verification gate ----
        new VerifyPass().apply(ctx);

        jar.writeJar(outJar, null);

        String report = jar.log + "\n" + ctx.report + "\n[done] "
                + (System.currentTimeMillis() - t0) + " ms, out=" + outJar + "\n";
        if (reportOut != null) {
            Files.createDirectories(reportOut.getParent());
            Files.writeString(reportOut, report, StandardCharsets.UTF_8);
        }
        System.out.print(report);
    }

    /** Full dataflow check; returns the error message or null. */
    private static String dataflowError(ClassNode cn) {
        for (var mn : cn.methods) {
            if (mn.instructions == null || mn.instructions.size() == 0) continue;
            try {
                new org.objectweb.asm.tree.analysis.Analyzer<>(
                        new org.objectweb.asm.tree.analysis.BasicVerifier()).analyze(cn.name, mn);
            } catch (Throwable t) {
                return mn.name + mn.desc + ": " + t;
            }
        }
        return null;
    }

    /** Real COMPUTE_FRAMES writer; returns bytes or null on failure. */
    private static byte[] writeWithFrames(ClassNode cn, JarSet jar) {
        try {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String a, String b) {
                    return jar.commonSuper(a, b);
                }
            };
            cn.accept(cw);
            return cw.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** debug probe: detect instruction-list corruption (size field vs chain length) */
    private static void checkLists(JarSet jar, String where, Ctx ctx) {
        int bad = 0;
        for (var e : jar.classes.entrySet()) {
            for (var mn : e.getValue().methods) {
                if (mn.instructions == null) continue;
                int chain = 0;
                org.objectweb.asm.tree.AbstractInsnNode n = mn.instructions.getFirst();
                while (n != null && chain <= mn.instructions.size() + 1) {
                    chain++;
                    n = n.getNext();
                }
                if (chain != mn.instructions.size()) {
                    throw new IllegalStateException("[probe] " + where + " CORRUPT: " + e.getKey()
                            + "#" + mn.name + " chain=" + chain + " size=" + mn.instructions.size());
                }
            }
        }
        if (bad > 0) ctx.log("[probe] " + where + ": " + bad + " corrupt methods");
    }

    public static byte[] bundleKey(Options opt) {
        // NOTE: NRes.kt mirrors this derivation exactly (bundleKey only — the
        // per-build obfuscation seed must not enter it, NRes is compiled ahead of time)
        String material = opt.native_.bundleKey;
        return Util.aesKey(material.getBytes(StandardCharsets.UTF_8));
    }

    /** random prefix so encrypted native payloads have no recognizable header at rest */
    private static byte[] nativeHeader(Path file) throws Exception {
        byte[] data = Files.readAllBytes(file);
        byte[] prefix = new byte[16];
        new Util.Rng(data.length ^ System.nanoTime()).nextInt();
        new java.security.SecureRandom().nextBytes(prefix);
        byte[] out = new byte[16 + data.length];
        System.arraycopy(prefix, 0, out, 0, 16);
        System.arraycopy(data, 0, out, 16, data.length);
        return out;
    }

    @SuppressWarnings("unused")
    private static void touch(List<ClassNode> l) {
    }
}

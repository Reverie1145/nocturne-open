package cn.reverie.obf;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Holds every entry of the jar being protected, the parsed in-scope classes,
 * and a class-hierarchy index that spans the jar itself, the build classpath
 * (Fabric/Minecraft/Kotlin) and the JDK.
 */
public final class JarSet {

    /** entry name -> raw bytes (everything, classes included until replaced) */
    public final Map<String, byte[]> entries = new LinkedHashMap<>();
    /** internal name -> parsed node (in scope only) */
    public final Map<String, ClassNode> classes = new LinkedHashMap<>();
    /** classes reachable on the build classpath (super-hierarchy + closure checks) */
    public final Set<String> libClasses = new HashSet<>();
    /** class -> superclass (jar classes first, then libs, then JDK via reflection) */
    private final Map<String, String> parentOf = new HashMap<>();
    private final Map<String, String[]> itfOf = new HashMap<>();
    private final Set<String> resolved = new HashSet<>();

    private URLClassLoader libLoader;
    private final List<Path> libJars = new ArrayList<>();

    public String log = "";

    private void log(String s) {
        log += s + "\n";
    }

    public void readJar(Path jar, Options opt) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile(), false)) {
            var en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                if (je.isDirectory()) continue;
                String name = je.getName();
                byte[] data = readAll(jf.getInputStream(je));
                entries.put(name, data);
                if (name.endsWith(".class")) {
                    String internal = name.substring(0, name.length() - 6);
                    if (opt.inScope(internal)) {
                        try {
                            ClassNode cn = new ClassNode();
                            new ClassReader(data).accept(cn, ClassReader.EXPAND_FRAMES);
                            classes.put(internal, cn);
                        } catch (Throwable t) {
                            log("[!] unparsable class kept raw: " + internal + " (" + t + ")");
                        }
                    }
                }
            }
        }
        log("[+] jar: " + jar.getFileName() + " entries=" + entries.size() + " inScope=" + classes.size());
    }

    public void addLibraries(List<Path> jars) throws IOException {
        libJars.addAll(jars);
        List<URL> urls = new ArrayList<>();
        for (Path p : jars) {
            if (!Files.exists(p)) continue;
            urls.add(p.toUri().toURL());
            try (JarFile jf = new JarFile(p.toFile(), false)) {
                var en = jf.entries();
                while (en.hasMoreElements()) {
                    JarEntry je = en.nextElement();
                    String n = je.getName();
                    if (!n.endsWith(".class") || je.getSize() > 4_000_000) continue;
                    String internal = n.substring(0, n.length() - 6);
                    if (!libClasses.add(internal)) continue;
                    try {
                        ClassNode cn = new ClassNode();
                        new ClassReader(readAll(jf.getInputStream(je)))
                                .accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                        if (cn.superName != null && parentOf.putIfAbsent(internal, cn.superName) == null) {
                            resolved.add(internal);
                        }
                        if (cn.interfaces != null) itfOf.put(internal, cn.interfaces.toArray(new String[0]));
                    } catch (Throwable ignored) {
                        // library class we cannot parse — closure checks will still see the name
                    }
                }
            } catch (Throwable t) {
                log("[!] lib unreadable: " + p + " (" + t + ")");
            }
        }
        libLoader = new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
        log("[+] libraries: " + libJars.size() + " jars, " + libClasses.size() + " classes indexed");
    }

    /** Loader over the build classpath, used for hierarchy queries and verification. */
    public URLClassLoader libLoader() {
        if (libLoader == null) {
            libLoader = new URLClassLoader(new URL[0], getClass().getClassLoader());
        }
        return libLoader;
    }

    public boolean isKnownClass(String internal) {
        return classes.containsKey(internal) || libClasses.contains(internal);
    }

    /**
     * Class bytes for a class we do not ship, read from the library/JDK path.
     * Reading bytes (instead of reflection) matters: Class.getDeclaredMethods() resolves every
     * parameter/return type, so a Minecraft class whose signature mentions a type that is not on
     * the build classpath throws NoClassDefFoundError and looks like it declares nothing — which
     * silently unpins every override of it.
     */
    private final Map<String, byte[]> libBytes = new HashMap<>();

    private byte[] bytesOf(String internal) {
        if (libBytes.containsKey(internal)) return libBytes.get(internal);
        byte[] out = null;
        try (java.io.InputStream in = libLoader().getResourceAsStream(internal + ".class")) {
            if (in != null) out = in.readAllBytes();
        } catch (Throwable ignored) {
        }
        libBytes.put(internal, out);
        return out;
    }

    private ClassNode parse(byte[] bytes) {
        if (bytes == null) return null;
        try {
            ClassNode cn = new ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(cn,
                    org.objectweb.asm.ClassReader.SKIP_CODE
                            | org.objectweb.asm.ClassReader.SKIP_DEBUG
                            | org.objectweb.asm.ClassReader.SKIP_FRAMES);
            return cn;
        } catch (Throwable t) {
            return null;
        }
    }

    private void ensureResolved(String internal) {
        if (resolved.contains(internal) || internal.startsWith("[")) return;
        if (!classes.containsKey(internal) && !libClasses.contains(internal)) {
            ClassNode cn = parse(bytesOf(internal));
            if (cn != null) {
                parentOf.put(internal, cn.superName);
                itfOf.put(internal, cn.interfaces == null
                        ? new String[0] : cn.interfaces.toArray(new String[0]));
            } else {
                // fall back to the JDK / classpath via reflection (no initialization)
                try {
                    Class<?> c = Class.forName(internal.replace('/', '.'), false, libLoader());
                    Class<?> s = c.getSuperclass();
                    parentOf.put(internal, s != null ? s.getName().replace('.', '/') : null);
                    Class<?>[] is = c.getInterfaces();
                    String[] arr = new String[is.length];
                    for (int i = 0; i < is.length; i++) arr[i] = is[i].getName().replace('.', '/');
                    itfOf.put(internal, arr);
                } catch (Throwable ignored) {
                    parentOf.put(internal, null); // unknown — stop here
                }
            }
        }
        resolved.add(internal);
    }

    public String superClass(String internal) {
        if (classes.containsKey(internal)) return classes.get(internal).superName;
        ensureResolved(internal);
        return parentOf.get(internal);
    }

    public String[] interfaces(String internal) {
        if (classes.containsKey(internal)) {
            List<String> l = classes.get(internal).interfaces;
            return l.toArray(new String[0]);
        }
        ensureResolved(internal);
        String[] r = itfOf.get(internal);
        return r != null ? r : new String[0];
    }

    private final Map<String, Set<String>> memberCache = new HashMap<>();

    /**
     * True when {@link #memberNamesOf} can return a COMPLETE member table for this
     * class (we hold its bytes, or the JDK/classpath can be reflected).
     *
     * Callers that use the table to decide whether a member is an override must
     * treat "false" as "assume it is an override": an unreadable ancestor means we
     * do not know what it declares, and renaming on a guess silently cuts the
     * override link. Pinning too much only costs a little naming chaos.
     */
    public boolean hasMemberTable(String internal) {
        if (internal == null) return false;
        if (classes.containsKey(internal)) return true;
        if (parse(bytesOf(internal)) != null) return true;
        try {
            Class.forName(internal.replace('/', '.'), false, libLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Declared members ("name|params") of any class, in scope or on the classpath/JDK. */
    public Set<String> memberNamesOf(String internal) {
        Set<String> cached = memberCache.get(internal);
        if (cached != null) return cached;
        Set<String> out = new HashSet<>();
        ClassNode cn = classes.get(internal);
        if (cn != null) {
            for (var m : cn.methods) out.add(m.name + "|" + paramsOf(m.desc));
        } else {
            // parse the class file so a signature type missing from the build classpath cannot
            // hide the whole member list (see bytesOf)
            ClassNode lib = parse(bytesOf(internal));
            if (lib != null) {
                for (var m : lib.methods) out.add(m.name + "|" + paramsOf(m.desc));
            } else {
                try {
                    Class<?> c = Class.forName(internal.replace('/', '.'), false, libLoader());
                    for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                        StringBuilder sb = new StringBuilder("(");
                        for (Class<?> p : m.getParameterTypes()) sb.append(descOf(p));
                        sb.append(")");
                        out.add(m.getName() + "|" + sb);
                    }
                } catch (Throwable ignored) {
                    // unknown class: no member info — treat as declaring nothing
                }
            }
        }
        memberCache.put(internal, out);
        return out;
    }

    public Set<String> fieldNamesOf(String internal) {
        Set<String> out = new HashSet<>();
        ClassNode cn = classes.get(internal);
        if (cn != null) {
            for (var f : cn.fields) out.add(f.name);
        } else {
            try {
                Class<?> c = Class.forName(internal.replace('/', '.'), false, libLoader());
                for (java.lang.reflect.Field f : c.getDeclaredFields()) out.add(f.getName());
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static String paramsOf(String desc) {
        int i = desc.indexOf(')');
        return i < 0 ? desc : desc.substring(0, i + 1);
    }

    private static String descOf(Class<?> c) {
        while (c.isArray()) {
            return "[" + descOf(c.getComponentType());
        }
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == short.class) return "S";
        if (c == char.class) return "C";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c == void.class) return "V";
        return "L" + c.getName().replace('.', '/') + ";";
    }

    /** Class-only lineage: [type, parent, grandparent, ..., java/lang/Object] (deduped). */
    private List<String> lineage(String type) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String cur = type;
        while (cur != null && seen.add(cur)) {
            out.add(cur);
            if (cur.equals("java/lang/Object")) break;
            cur = superClass(cur);
        }
        return out;
    }

    /** JVMS-style merge: deepest common superclass along class chains. */
    public String commonSuper(String a, String b) {
        if (a == null || b == null) return "java/lang/Object";
        if (a.equals(b)) return a;
        if (a.startsWith("[") || b.startsWith("[")) {
            if (a.startsWith("[") && b.startsWith("[") && a.equals(b)) return a;
            return "java/lang/Object";
        }
        List<String> la = lineage(a), lb = lineage(b);
        int i = la.size() - 1, j = lb.size() - 1;
        String best = "java/lang/Object";
        while (i >= 0 && j >= 0 && la.get(i).equals(lb.get(j))) {
            best = la.get(i);
            i--;
            j--;
        }
        return best;
    }

    public void renameEntryKey(String oldInternal, String newInternal) {
        ClassNode cn = classes.remove(oldInternal);
        if (cn != null) classes.put(newInternal, cn);
        byte[] raw = entries.remove(oldInternal + ".class");
        if (raw != null) entries.put(newInternal + ".class", raw);
    }

    public void writeJar(Path out, List<String> manifestOrder) throws IOException {
        Files.createDirectories(out.getParent());
        try (OutputStream fos = Files.newOutputStream(out);
             JarOutputStream jos = new JarOutputStream(fos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                JarEntry je = new JarEntry(e.getKey());
                je.setTime(0L);
                jos.putNextEntry(je);
                jos.write(e.getValue());
                jos.closeEntry();
            }
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 15);
        byte[] buf = new byte[1 << 15];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        in.close();
        return bo.toByteArray();
    }

    /**
     * The one Minecraft jar whose hierarchy we must resolve against.
     *
     * Selecting by version is essential, not cosmetic: this table decides which
     * members are "declared by an unrenamed ancestor", i.e. which overrides get
     * pinned. Resolving against a different Minecraft version yields a member
     * table that does not contain the real overridden method, so the override is
     * renamed and silently stops being called (a dead entrance animation, a
     * screen that no longer reacts) while the build still passes verification.
     */
    public static List<Path> discoverMinecraftJars(String mcVersion) {
        Path cache = Paths.get(System.getProperty("user.home"), ".gradle", "caches", "fabric-loom");
        if (mcVersion != null && !mcVersion.isBlank()) {
            Path exact = cache.resolve(mcVersion).resolve("minecraft-merged.jar");
            if (Files.exists(exact)) {
                return List.of(exact);
            }
        }
        List<Path> out = new ArrayList<>();
        try (var walk = Files.walk(cache, 6)) {
            walk.filter(p -> {
                        String n = p.getFileName().toString();
                        if (!n.endsWith(".jar") || !n.contains("minecraft-merged")) return false;
                        // only pick a jar whose own directory names the version
                        Path parent = p.getParent();
                        return parent != null && parent.getFileName() != null;
                    })
                    .limit(24)
                    .forEach(out::add);
        } catch (Throwable ignored) {
        }
        return out;
    }
}

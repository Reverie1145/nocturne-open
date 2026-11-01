package cn.reverie.obf.passes;

import cn.reverie.obf.JarSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits invokedynamic bootstrap classes by compiling their source in memory
 * with the build JDK's compiler (more reliable than hand-rolled codegen for
 * MethodHandles-heavy logic). Two variants use different decode algebra.
 */
final class BsmGen {

    private BsmGen() {
    }

    static String encode(String owner, String name, String desc, String siteName, int mode, int variant) {
        String raw = owner + "\u0001" + name + "\u0001" + desc;
        int key = siteName.hashCode() ^ (mode * (variant == 0 ? (int) 0x9E3779B9L : (int) 0x85EBCA6BL));
        char[] c = raw.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (variant == 0) {
                c[i] ^= (char) ((key >>> ((i & 3) * 8)) & 0xFFFF);
            } else {
                c[i] ^= (char) ((key + i * 0x27220A95) >>> 16);
            }
        }
        return new String(c);
    }

    static ClassNode emit(String internal, int variant, cn.reverie.obf.Ctx ctx) {
        String pkg = internal.substring(0, internal.lastIndexOf('/'));
        String cls = internal.substring(internal.lastIndexOf('/') + 1);
        String src = SRC
                .replace("%KEYMIX%", variant == 0 ? "0x9E3779B9" : "0x85EBCA6B")
                .replace("%CHARMIX%", variant == 0 ? CHAR_MIX_0 : CHAR_MIX_1)
                .replace("%PKG%", pkg)
                .replace("%CLS%", cls);
        byte[] bytes = cn.reverie.obf.MemCompile.compile(pkg, cls, src);
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.EXPAND_FRAMES);
        return cn;
    }

    private static final String CHAR_MIX_0 = "c[i] ^= (char)((key >>> ((i & 3) * 8)) & 0xFFFF);";
    private static final String CHAR_MIX_1 = "c[i] ^= (char)(((key + i * 0x27220A95) >>> 16) & 0xFFFF);";

    private static final String SRC = """
            package %PKG%;

            import java.lang.invoke.CallSite;
            import java.lang.invoke.ConstantCallSite;
            import java.lang.invoke.MethodHandle;
            import java.lang.invoke.MethodHandles;
            import java.lang.invoke.MethodType;
            import java.lang.reflect.Field;
            import java.lang.reflect.Method;

            public final class %CLS% {

                public static CallSite boot(MethodHandles.Lookup lookup, String name, MethodType type,
                                            int mode, String ref) throws Throwable {
                    String[] p = dec(ref, name, mode);
                    Class<?> owner = Class.forName(p[0].replace('/', '.'), false,
                            lookup.lookupClass().getClassLoader());
                    MethodHandles.Lookup l = lookup;
                    try {
                        l = MethodHandles.privateLookupIn(owner, lookup);
                    } catch (Throwable ignored) {
                        l = lookup;
                    }
                    try {
                        switch (mode) {
                            case 0: return new ConstantCallSite(l.findVirtual(owner, p[1], type.dropParameterTypes(0, 1)));
                            case 1: return new ConstantCallSite(l.findStatic(owner, p[1], type));
                            case 2: return new ConstantCallSite(l.findGetter(owner, p[1], type.returnType()));
                            case 3: return new ConstantCallSite(l.findStaticGetter(owner, p[1], type.returnType()));
                            case 4: return new ConstantCallSite(l.findSetter(owner, p[1], type.parameterType(1)));
                            case 5: return new ConstantCallSite(l.findStaticSetter(owner, p[1], type.parameterType(0)));
                            case 6: {
                                // indy type carries the receiver; findSpecial wants the (args)ret form
                                MethodType t2 = type.dropParameterTypes(0, 1);
                                return new ConstantCallSite(l.findSpecial(owner, p[1], t2, l.lookupClass()));
                                }
                            default: throw new IllegalStateException();
                        }
                    } catch (IllegalAccessException e) {
                        if (mode == 0 || mode == 6) {
                            Method m = find(owner, p[1], type, true);
                            m.setAccessible(true);
                            MethodHandle h = (mode == 6) ? l.unreflectSpecial(m, owner) : l.unreflect(m);
                            return new ConstantCallSite(h.asType(type));
                        }
                        if (mode == 1) {
                            Method m = find(owner, p[1], type, false);
                            m.setAccessible(true);
                            return new ConstantCallSite(l.unreflect(m).asType(type));
                        }
                        Field f = owner.getDeclaredField(p[1]);
                        f.setAccessible(true);
                        MethodHandle h = (mode == 2 || mode == 3) ? l.unreflectGetter(f) : l.unreflectSetter(f);
                        return new ConstantCallSite(h.asType(type));
                    }
                }

                private static Method find(Class<?> owner, String name, MethodType type, boolean skipFirst) {
                    Class<?>[] ps = type.parameterArray();
                    int off = skipFirst ? 1 : 0;
                    for (Method m : owner.getDeclaredMethods()) {
                        if (!m.getName().equals(name)) continue;
                        Class<?>[] mp = m.getParameterTypes();
                        if (mp.length != ps.length - off) continue;
                        boolean ok = true;
                        for (int i = 0; i < mp.length; i++) {
                            if (mp[i] != ps[i + off]) { ok = false; break; }
                        }
                        if (ok) return m;
                    }
                    throw new NoSuchMethodError(name);
                }

                private static String[] dec(String ref, String name, int mode) {
                    int key = name.hashCode() ^ (mode * %KEYMIX%);
                    char[] c = ref.toCharArray();
                    for (int i = 0; i < c.length; i++) {
                        %CHARMIX%
                    }
                    String s = new String(c);
                    java.util.List<String> out = new java.util.ArrayList<>(3);
                    int prev = 0;
                    for (int i = 0; i < s.length(); i++) {
                        if (s.charAt(i) == '\\u0001') { out.add(s.substring(prev, i)); prev = i + 1; }
                    }
                    out.add(s.substring(prev));
                    return out.toArray(new String[0]);
                }
            }
            """;


    static byte[] write(ClassNode cn, JarSet jar) {
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }
}

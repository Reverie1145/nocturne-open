package cn.reverie.obf;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rename maps + ASM remapper.
 *
 * Method renaming is a pure function of (name, parameterTypes) so every
 * override of the same original signature lands on the same new name
 * regardless of which renamed class defines it. Names that override a
 * symbol declared by an *unrenamed* ancestor (JDK/Minecraft/Fabric) are
 * pinned to the original name, and mixin classes keep all members.
 */
public final class Mapping extends Remapper {

    /** (name|params) -> new name for renamed members */
    public final Map<String, String> methodMap = new HashMap<>();
    /** owner|(name|params) -> collapsed pool name for private/static members */
    public final Map<String, String> privateMethodMap = new HashMap<>();
    /** (name|params) pinned to original (overridden from unrenamed ancestor / enum plumbing) */
    public final Set<String> methodKeep = new HashSet<>();
    /** owner -> declared method keys ("name|params") */
    public final Map<String, Set<String>> declaredMethods = new HashMap<>();
    /** owner -> declared + in-scope-inherited method keys; only these may be renamed on `owner` */
    private final Map<String, Set<String>> virtualMembers = new HashMap<>();
    /** hierarchy snapshot taken before remapping mutates JarSet.classes */
    private final Map<String, String> parentSnap = new HashMap<>();
    private final Map<String, String[]> itfSnap = new HashMap<>();
    /** "owner|name" -> new field name */
    public final Map<String, String> fieldMap = new HashMap<>();
    /** per-class declared field names, for owner resolution */
    public final Map<String, Set<String>> declaredFields = new HashMap<>();
    /** internal -> internal */
    public final Map<String, String> classMap = new HashMap<>();
    /** final -> original internal name (filled by seal) */
    private final Map<String, String> reverseClassMap = new HashMap<>();
    /** classes whose members must not be touched (mixin/accessor interfaces) */
    public final Set<String> mixinClasses = new HashSet<>();

    /** keepPackages + exact keepClasses: their OWNERS must never have members renamed */
    public java.util.List<String> keepPackages = new java.util.ArrayList<>();
    public java.util.Set<String> keepClasses = new java.util.HashSet<>();

    /** true if members declared by this owner must keep their names (mixin 弱混 / JNI glue). */
    private boolean keepsMembersOwner(String owner) {
        if (owner == null) return false;
        if (keepClasses.contains(owner)) return true;
        for (String p : keepPackages) {
            if (owner.equals(p) || owner.startsWith(p + "/")) return true;
        }
        return false;
    }

    /** true if this owner is an in-scope class whose members we are allowed to rename. */
    private boolean renamesMembersOwner(String owner) {
        if (owner == null || keepsMembersOwner(owner)) return false;
        String mapped = classMap.get(owner);
        return mapped != null && !mapped.equals(owner);
    }

    public void registerClass(String owner, String superName, java.util.List<String> itfs) {
        parentSnap.put(owner, superName);
        itfSnap.put(owner, itfs == null ? new String[0] : itfs.toArray(new String[0]));
    }

    /**
     * Freeze the hierarchy and precompute, per in-scope owner, the set of method
     * keys it (or an in-scope ancestor) actually declares. A reference is only
     * renamed when the key is in that set — otherwise the call resolves to a
     * JDK/Minecraft/Fabric/Kotlin declaration and must keep its original name.
     */
    public void seal() {
        for (String owner : declaredMethods.keySet()) {
            if (!renamesMembersOwner(owner)) continue;
            Set<String> acc = new HashSet<>(declaredMethods.get(owner));
            java.util.Deque<String> q = new java.util.ArrayDeque<>();
            addParents(owner, q);
            Set<String> seen = new HashSet<>();
            while (!q.isEmpty()) {
                String c = q.poll();
                if (c == null || !seen.add(c)) continue;
                if (!renamesMembersOwner(c)) continue;
                Set<String> d = declaredMethods.get(c);
                if (d != null) acc.addAll(d);
                addParents(c, q);
            }
            virtualMembers.put(owner, acc);
        }
        // a pinned key must stay pinned everywhere: drop any rename decided for it
        for (String k : methodKeep) methodMap.remove(k);
        for (var e : classMap.entrySet()) reverseClassMap.put(e.getValue(), e.getKey());
    }

    /** original internal name of a (possibly renamed) class; the input when unknown. */
    public String originalOf(String internal) {
        String o = reverseClassMap.get(internal);
        return o != null ? o : internal;
    }

    private void addParents(String owner, java.util.Deque<String> q) {
        String sup = parentSnap.get(owner);
        if (sup != null) q.add(sup);
        String[] is = itfSnap.get(owner);
        if (is != null) q.addAll(java.util.Arrays.asList(is));
    }

    private final JarSet jar;

    public Mapping(JarSet jar) {
        this.jar = jar;
    }

    public static String params(String desc) {
        int i = desc.indexOf(')');
        return i < 0 ? desc : desc.substring(0, i + 1);
    }

    public String methodKey(String name, String desc) {
        return name + "|" + params(desc);
    }

    // ---------------- Remapper ----------------

    @Override
    public String map(String internalName) {
        String m = classMap.get(internalName);
        return m != null ? m : internalName;
    }

    /** resolve the class that declares this private/static member, walking renamed ancestors */
    public String resolveDeclOwner(String owner, String name, String params) {
        String cur = owner;
        int hops = 0;
        while (cur != null && hops++ < 64) {
            if (privateMethodMap.containsKey(cur + "|" + name + "|" + params)) return cur;
            cur = parentSnap.containsKey(cur) ? parentSnap.get(cur) : jar.superClass(cur);
        }
        return null;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (name.startsWith("<")) return name; // <init>/<clinit>
        if (owner == null) return name;
        String key = methodKey(name, descriptor);
        if (methodKeep.contains(key)) return name; // pinned for at least one declaring class
        String params = params(descriptor);

        // collapsed private/static names resolve through the declaring owner — this also
        // covers a name-pinned class calling a static/inherited member of a renamed ancestor
        String pdecl = resolveDeclOwner(owner, name, params);
        if (pdecl != null && !keepsMembersOwner(pdecl)) {
            String p = privateMethodMap.get(pdecl + "|" + name + "|" + params);
            if (p != null) return p;
        }

        if (keepsMembersOwner(owner)) {
            // `owner` is name-pinned (mixin 弱混 / JNI glue) so the members IT declares must
            // survive. A member it merely INHERITS from a renamed ancestor was renamed on that
            // ancestor, and the JVM resolves this reference up the hierarchy — keeping the old
            // name here yields NoSuchMethodError at runtime.
            String decl = declaringMethodOwner(owner, name, params);
            if (decl == null || keepsMembersOwner(decl)) return name;
        } else {
            // only rename when this owner (or an in-scope ancestor) really declares the
            // member; otherwise the symbol resolves to JDK/Minecraft/Fabric/Kotlin
            Set<String> vm = virtualMembers.get(owner);
            if (vm == null || !vm.contains(key)) return name;
        }
        String r = methodMap.get(key);
        return r != null ? r : name;
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        if (owner == null) return name;
        if (keepsMembersOwner(owner)) {
            // same rule as mapMethodName: a pinned owner keeps only the fields it (or a
            // pinned ancestor) declares; an inherited one was renamed on its declarer
            String decl = declaringFieldOwner(owner, name);
            if (decl == null || keepsMembersOwner(decl)) return name;
        }
        // resolve declaring class up the renamed chain (parentSnap is the pre-rename
        // snapshot; JarSet.classes is already being mutated by this remap loop)
        String cur = owner;
        int hops = 0;
        while (cur != null && hops++ < 32) {
            String direct = fieldMap.get(cur + "|" + name);
            if (direct != null) return direct;
            String parent = parentSnap.get(cur);
            if (parent == null || !classMap.containsKey(parent)) break;
            cur = parent;
        }
        // fields with the same old name in different classes were mapped per-owner;
        // fall back to the global name->name map computed at build time
        String g = fieldMap.get("*" + name);
        return g != null ? g : name;
    }

    /**
     * Resolve the class that actually declares (name|params), starting at `owner` and
     * walking the pre-rename hierarchy snapshot (superclass chain first, then interfaces).
     * Returns null when the declaration is not one of our classes (JDK/Minecraft/Fabric/
     * Kotlin) or cannot be located.
     */
    private String declaringMethodOwner(String owner, String name, String params) {
        String key = name + "|" + params;
        String cur = owner;
        Set<String> seen = new HashSet<>();
        while (cur != null && seen.add(cur)) {
            Set<String> d = declaredMethods.get(cur);
            if (d != null && d.contains(key)) return cur;
            cur = parentSnap.get(cur);
        }
        return firstInterfaceOf(owner, o -> {
            Set<String> d = declaredMethods.get(o);
            return d != null && d.contains(key);
        });
    }

    /** Same as {@link #declaringMethodOwner} for fields. */
    private String declaringFieldOwner(String owner, String name) {
        String cur = owner;
        Set<String> seen = new HashSet<>();
        while (cur != null && seen.add(cur)) {
            Set<String> d = declaredFields.get(cur);
            if (d != null && d.contains(name)) return cur;
            cur = parentSnap.get(cur);
        }
        return firstInterfaceOf(owner, o -> {
            Set<String> d = declaredFields.get(o);
            return d != null && d.contains(name);
        });
    }

    private interface OwnerTest {
        boolean test(String owner);
    }

    /** BFS the (snapshot) interface closure of `from`, returning the first owner matching `test`. */
    private String firstInterfaceOf(String from, OwnerTest test) {
        java.util.Deque<String> q = new java.util.ArrayDeque<>();
        String[] is = itfSnap.get(from);
        if (is != null) q.addAll(java.util.Arrays.asList(is));
        Set<String> seen = new HashSet<>();
        while (!q.isEmpty()) {
            String itf = q.poll();
            if (itf == null || !seen.add(itf)) continue;
            if (test.test(itf)) return itf;
            String[] sups = itfSnap.get(itf);
            if (sups != null) q.addAll(java.util.Arrays.asList(sups));
            String sup = parentSnap.get(itf);
            if (sup != null) q.add(sup);
        }
        return null;
    }

    @Override
    public Object mapValue(Object value) {
        if (value instanceof String s) {
            // reflection-ish strings that name our own classes get rewritten in place
            String mapped = classMap.get(s);
            if (mapped != null) return mapped;
            String dotted = s.replace('.', '/');
            String mappedDotted = classMap.get(dotted);
            if (mappedDotted != null) return Names.dotted(mappedDotted);
        }
        return super.mapValue(value);
    }

    // ---------------- helpers ----------------

    public void registerDeclaredFields(String owner, List<org.objectweb.asm.tree.FieldNode> fields) {
        Set<String> set = declaredFields.computeIfAbsent(owner, k -> new HashSet<>());
        for (org.objectweb.asm.tree.FieldNode f : fields) set.add(f.name);
    }

    public void registerDeclaredMethods(String owner, List<org.objectweb.asm.tree.MethodNode> methods) {
        Set<String> set = declaredMethods.computeIfAbsent(owner, k -> new HashSet<>());
        for (org.objectweb.asm.tree.MethodNode m : methods) set.add(m.name + "|" + params(m.desc));
    }

    /** Does any ancestor above `owner` (following renamed links only) declare this field? */
    public String resolveFieldOwner(String owner, String name) {
        String cur = owner;
        int hops = 0;
        while (cur != null && hops++ < 64) {
            Set<String> df = declaredFields.get(cur);
            if (df != null && df.contains(name)) return cur;
            cur = jar.superClass(cur);
        }
        return null;
    }

    public boolean isMixin(String owner) {
        return mixinClasses.contains(owner);
    }

    @SuppressWarnings("unused")
    private static boolean acc(int a, int flag) {
        return (a & flag) != 0 || flag == Opcodes.ACC_PUBLIC;
    }
}

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
public final class RenamePass {

    private static final Set<String> ENUM_KEEP = Set.of("values", "valueOf");

    public void apply(Ctx ctx) {
        Options opt = ctx.opt;
        if (!opt.rename) return;

        Names names = new Names(opt, new HashSet<>(ctx.jar.classes.keySet()));
        ctx.names = names;
        Mapping map = new Mapping(ctx.jar);
        ctx.map = map;
        map.keepPackages = opt.keepPackages;
        map.keepClasses = new HashSet<>(opt.keepClasses);

        // ---- classify in-scope classes ----
        collectMixinClassesFromConfigs(ctx); // authoritative mixin set from *.mixins.json
        List<ClassNode> mixins = new ArrayList<>();
        List<ClassNode> shared = new ArrayList<>();
        List<ClassNode> scattered = new ArrayList<>();
        Set<String> keepClasses = new HashSet<>(opt.keepClasses);

        // Package-private access is legal only inside one runtime package, so a
        // whole ORIGINAL package has to be relocated as a unit the moment any of
        // its classes declares a package-private member — otherwise the classes
        // that used to reach that member land in a different package.
        Set<String> sharedPackages = new HashSet<>();
        for (ClassNode cn : ctx.jar.classes.values()) {
            if (keepClasses.contains(cn.name) || inKeepPackages(opt, cn.name)) continue;
            if (ctx.hasPackagePrivateMembers(cn)) sharedPackages.add(pkg(cn.name));
        }

        for (Map.Entry<String, ClassNode> e : ctx.jar.classes.entrySet()) {
            ClassNode cn = e.getValue();
            if (keepClasses.contains(e.getKey()) || inKeepPackages(opt, e.getKey())) continue;
            if (isMixinClass(ctx, e.getKey())) mixins.add(cn);
            else if (sharedPackages.contains(pkg(e.getKey()))) shared.add(cn);
            else scattered.add(cn);
        }

        // shared root package for mixin classes (+ package-private users)
        String mixinRoot = names.freshPackage();
        Map<String, String> subPackageMap = new HashMap<>(); // old subpackage -> new subpackage

        // ---- phase 1: class renames (complete map before member mapping) ----
        int renamed = 0;
        for (ClassNode cn : ctx.jar.classes.values()) {
            String old = cn.name;
            if (keepClasses.contains(old) || inKeepPackages(opt, old)) {
                map.classMap.put(old, old);
                continue;
            }
            // a package-private (or Kotlin `private` nested) class is only reachable
            // from its own original package; scattering would break that forever, and
            // widening the class itself is safe (nothing depends on it being hidden)
            if ((cn.access & org.objectweb.asm.Opcodes.ACC_PUBLIC) == 0) {
                cn.access |= org.objectweb.asm.Opcodes.ACC_PUBLIC;
            }
            String newName;
            if (mixins.contains(cn) || shared.contains(cn)) {
                String oldPkg = pkg(old);
                String sub = subPackageMap.computeIfAbsent(oldPkg, k -> names.freshPackage());
                newName = mixinRoot + "/" + sub + "/" + names.freshName(4, 14);
            } else {
                newName = names.freshPackage() + "/" + names.freshName(4, 14);
            }
            map.classMap.put(old, newName);
            renamed++;
        }

        // ---- phase 2: member mapping (needs the complete class map for override analysis) ----
        // Mixin classes go first: their member names are pinned by the framework, and an
        // interface that a mixin implements has to learn those pinned names before it is
        // mapped itself. Otherwise the interface method gets renamed while the injected
        // implementation keeps the original name, and the target class ends up not
        // implementing the interface (AbstractMethodError at call time).
        for (ClassNode cn : mixins) {
            registerMembers(ctx, map, cn, true);
        }
        for (ClassNode cn : ctx.jar.classes.values()) {
            if (mixins.contains(cn)) continue;
            registerMembers(ctx, map, cn, !keepClasses.contains(cn.name) && !inKeepPackages(opt, cn.name));
        }
        // A name-pinned class (mixin 弱混 / JNI glue) keeps EVERY member name it declares, so
        // any in-scope ancestor it overrides from must keep that name as well — otherwise the
        // override link is cut and the ancestor's renamed method silently wins at call time
        // (e.g. ClickGUIModule.onEnable() would never run).
        for (ClassNode cn : ctx.jar.classes.values()) {
            if (!keepClasses.contains(cn.name) && !inKeepPackages(opt, cn.name)) continue;
            for (MethodNode mn : cn.methods) {
                if (mn.name.startsWith("<")) continue;
                String key = mn.name + "|" + Mapping.params(mn.desc);
                if (map.methodKeep.contains(key)) continue;
                if (overridesRenamedAncestor(ctx, map, cn.name, key)) map.methodKeep.add(key);
            }
        }

        ctx.log("[rename] classes renamed: " + renamed
                + " (mixins " + mixins.size() + " flattened under /" + mixinRoot + ")");
        map.seal();

        // ---- apply remapping ----
        for (Map.Entry<String, String> e : new ArrayList<>(map.classMap.entrySet())) {
            ClassNode cn = ctx.jar.classes.get(e.getKey());
            if (cn == null) continue;
            ClassNode out = new ClassNode();
            cn.accept(new ClassRemapper(out, map));
            byte[] bytes = serialize(out);
            ctx.jar.renameEntryKey(e.getKey(), e.getValue());
            ctx.jar.entries.put(e.getValue() + ".class", bytes);
            ctx.jar.classes.remove(e.getKey());
            ctx.jar.classes.put(e.getValue(), out);
        }

        // ---- out-of-scope classes that happen to reference renamed names ----
        RenamePassVerifier.rewriteBystanders(ctx, map);
        RenamePassVerifier.checkOutScopeRefs(ctx, map);
        RenamePassVerifier.checkPackageAccess(ctx);

        // ---- resources ----
        rewriteMixinConfigs(ctx, map, mixinRoot, subPackageMap);
        rewriteFabricModJson(ctx, map);

        ctx.log("[rename] members renamed: " + map.methodMap.size() + " methods, "
                + map.fieldMap.size() + " fields; pinned: " + map.methodKeep.size());
    }

    private void registerMembers(Ctx ctx, Mapping map, ClassNode cn, boolean rename) {
        map.registerDeclaredFields(cn.name, cn.fields);
        map.registerDeclaredMethods(cn.name, cn.methods);
        map.registerClass(cn.name, cn.superName, cn.interfaces);
        if (!rename) return;
        boolean mixin = isMixinClass(ctx, cn.name);
        if (mixin && ctx.opt.keepMembers.contains(cn.name)) return;
        if (!mixin && ctx.opt.keepMembers.contains(cn.name)) return; // JNI/RegisterNatives surface
        boolean isEnum = (cn.access & org.objectweb.asm.Opcodes.ACC_ENUM) != 0;

        // tiny per-class name pool: private/static members collapse onto a handful
        // of shared names (valid via overloading), maximising name chaos
        java.util.List<String> pool = java.util.List.of(
                ctx.names.freshMember(), ctx.names.freshMember(), ctx.names.freshMember(),
                ctx.names.freshMember(), ctx.names.freshMember());
        java.util.Set<String> usedInClass = new HashSet<>();

        for (MethodNode mn : cn.methods) {
            if (mn.name.startsWith("<")) continue;
            String key = mn.name + "|" + Mapping.params(mn.desc);
            if (mixin) {
                // members matched against targets (@Shadow/@Overwrite/@Accessor/@Inject handlers)
                // must keep names; only @Unique members are ours to rename
                if (!ctx.hasMixinMethodAnnotation(mn)) {
                    map.methodKeep.add(key);
                } else if (hasAnnotation(mn.visibleAnnotations, mn.invisibleAnnotations,
                        "Lorg/spongepowered/asm/mixin/Unique;")) {
                    map.methodMap.putIfAbsent(key, ctx.names.freshMember());
                } else {
                    map.methodKeep.add(key);
                }
                continue;
            }
            if (isEnum && ENUM_KEEP.contains(mn.name)) {
                map.methodKeep.add(key);
                continue;
            }
            // A mixin keeps the name of every method it does not own, so an interface that
            // such a mixin implements must keep that name as well. Renaming the interface
            // method here would leave the injected body not implementing it.
            if ((cn.access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0 && map.methodKeep.contains(key)) {
                map.methodKeep.add(key);
                continue;
            }
            // never rename any member the mixin framework relies on by name, even if the class
            // was not recognised as a mixin (@Shadow/@Inject/@Accessor handlers must match targets)
            if (ctx.hasMixinMethodAnnotation(mn)) {
                map.methodKeep.add(key);
                continue;
            }
            if (pinnedByAncestor(ctx, map, cn.name, key)) {
                map.methodKeep.add(key);
                continue;
            }
            boolean collapsible = (mn.access & (org.objectweb.asm.Opcodes.ACC_PRIVATE
                    | org.objectweb.asm.Opcodes.ACC_STATIC)) != 0
                    && (mn.access & org.objectweb.asm.Opcodes.ACC_SYNTHETIC) == 0;
            if (collapsible) {
                String params = Mapping.params(mn.desc);
                String chosen = null;
                for (int attempt = 0; attempt < pool.size(); attempt++) {
                    String cand = pool.get(Math.floorMod(cn.name.hashCode() + usedInClass.size(), pool.size()));
                    if (usedInClass.add(cand + "|" + params)) {
                        chosen = cand;
                        break;
                    }
                }
                if (chosen == null) chosen = ctx.names.freshMember();
                map.privateMethodMap.put(cn.name + "|" + mn.name + "|" + params, chosen);
                usedInClass.add(chosen + "|" + params);
                continue;
            }
            map.methodMap.putIfAbsent(key, ctx.names.freshMember());
        }
        for (var f : cn.fields) {
            if (f.name.equals("INSTANCE")) continue; // Kotlin object singleton — referenced by generated code
            if (mixin && !ctx.hasMixinFieldAnnotation(f)) {
                continue;
            }
            if (mixin && !hasAnnotation(f.visibleAnnotations, f.invisibleAnnotations,
                    "Lorg/spongepowered/asm/mixin/Unique;")) {
                continue;
            }
            map.fieldMap.putIfAbsent(cn.name + "|" + f.name, ctx.names.freshMember());
        }
    }

    private static boolean hasAnnotation(java.util.List<org.objectweb.asm.tree.AnnotationNode> a,
                                         java.util.List<org.objectweb.asm.tree.AnnotationNode> b,
                                         String desc) {
        if (a != null) {
            for (var x : a) if (x.desc.equals(desc)) return true;
        }
        if (b != null) {
            for (var x : b) if (x.desc.equals(desc)) return true;
        }
        return false;
    }

    /**
     * True when (name|params) is declared by any ancestor that is not itself renamed.
     *
     * Walks the FULL ancestor closure — superclass chain and, transitively, the
     * interfaces of every class in it. Walking only own interfaces misses the case
     * that matters most: an override of an interface method inherited through a
     * superclass (e.g. a Screen subclass overriding GuiEventListener.mouseClicked,
     * which Screen implements). Such a method would be renamed, the override link
     * would be cut, and the screen would silently stop receiving input.
     */
    private boolean pinnedByAncestor(Ctx ctx, Mapping map, String owner, String key) {
        Set<String> visited = new HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        String sup = ctx.jar.superClass(owner);
        if (sup != null) queue.add(sup);
        queue.addAll(List.of(ctx.jar.interfaces(owner)));
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (cur == null || cur.startsWith("[")) continue;
            if (cur.endsWith(";")) cur = cur.substring(1, cur.length() - 1);
            if (!visited.add(cur)) continue;
            boolean renamedAncestor = ctx.jar.classes.containsKey(cur)
                    && map.classMap.containsKey(cur)
                    && !ctx.opt.keepClasses.contains(cur);
            if (!renamedAncestor) {
                // An ancestor whose member table we cannot read is treated as if it
                // declared this member: renaming on a guess is what silently cuts
                // override links, while over-pinning only costs naming chaos.
                if (!ctx.jar.hasMemberTable(cur)) return true;
                if (ctx.jar.memberNamesOf(cur).contains(key)) return true;
            }
            String s = ctx.jar.superClass(cur);
            if (s != null) queue.add(s);
            for (String i : ctx.jar.interfaces(cur)) queue.add(i);
        }
        return false;
    }

    /**
     * True when some in-scope ancestor/interface of `owner` is itself renamed and declares
     * this key — i.e. the kept class's method overrides a member that would otherwise be
     * renamed out from under it.
     */
    private static boolean overridesRenamedAncestor(Ctx ctx, Mapping map, String owner, String key) {
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>();
        String sup = ctx.jar.superClass(owner);
        if (sup != null) queue.add(sup);
        queue.addAll(List.of(ctx.jar.interfaces(owner)));
        while (!queue.isEmpty()) {
            String c = queue.remove(0);
            if (c == null || !visited.add(c)) continue;
            String mapped = map.classMap.get(c);
            boolean renamedAncestor = mapped != null && !mapped.equals(c);
            if (renamedAncestor && ctx.jar.memberNamesOf(c).contains(key)) return true;
            String s = ctx.jar.superClass(c);
            if (s != null) queue.add(s);
            queue.addAll(List.of(ctx.jar.interfaces(c)));
        }
        return false;
    }

    private void rewriteMixinConfigs(Ctx ctx, Mapping map, String mixinRoot,
                                     Map<String, String> subPackageMap) {
        Gson gson = new Gson();
        for (Map.Entry<String, byte[]> en : new ArrayList<>(ctx.jar.entries.entrySet())) {
            String name = en.getKey();
            if (!name.endsWith(".mixins.json") && !name.endsWith("-mixins.json")) continue;
            try {
                JsonObject jo = gson.fromJson(new String(en.getValue(), StandardCharsets.UTF_8), JsonObject.class);
                if (jo == null || !jo.has("package")) continue;
                String oldPackage = jo.get("package").getAsString().replace('.', '/');
                if (!ctx.opt.inScope(oldPackage)) continue;
                // mixin 弱混: mixins under a kept package are not renamed — leave their config untouched
                if (inKeepPackages(ctx.opt, oldPackage)) continue;
                jo.addProperty("package", mixinRoot.replace('/', '.'));
                for (String listKey : List.of("mixins", "client", "server")) {
                    if (!jo.has(listKey) || !jo.get(listKey).isJsonArray()) continue;
                    JsonArray arr = jo.getAsJsonArray(listKey);
                    JsonArray out = new JsonArray();
                    for (JsonElement el : arr) {
                        String entry = el.getAsString();
                        String oldInternal = oldPackage + "/" + entry.replace('.', '/');
                        String newInternal = map.classMap.get(oldInternal);
                        if (newInternal == null) {
                            out.add(entry); // not renamed (keep rule) — keep entry
                            continue;
                        }
                        String rel = newInternal;
                        String root = mixinRoot + "/";
                        if (rel.startsWith(root)) rel = rel.substring(root.length());
                        out.add(rel.replace('/', '.'));
                    }
                    jo.add(listKey, out);
                }
                byte[] bytes = new GsonBuilder().setPrettyPrinting().create()
                        .toJson(jo).getBytes(StandardCharsets.UTF_8);
                ctx.jar.entries.put(name, bytes);
                ctx.log("[rename] mixin config rewritten: " + name);
            } catch (Throwable t) {
                ctx.log("[!] mixin config rewrite failed: " + name + " (" + t + ")");
            }
        }
    }

    private void rewriteFabricModJson(Ctx ctx, Mapping map) {
        byte[] raw = ctx.jar.entries.get("fabric.mod.json");
        if (raw == null) return;
        try {
            JsonObject jo = new Gson().fromJson(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
            boolean changed = false;
            if (jo.has("entrypoints") && jo.get("entrypoints").isJsonObject()) {
                JsonObject eps = jo.getAsJsonObject("entrypoints");
                for (Map.Entry<String, JsonElement> e : eps.entrySet()) {
                    if (!e.getValue().isJsonArray()) continue;
                    JsonArray arr = e.getValue().getAsJsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        String v = arr.get(i).getAsString();
                        String dotted = v.replace('/', '.');
                        String mapped = map.classMap.get(dotted);
                        if (mapped == null) {
                            mapped = map.classMap.get(dotted.replace('.', '/'));
                        }
                        if (mapped != null) {
                            arr.set(i, new com.google.gson.JsonPrimitive(Names.dotted(mapped)));
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                ctx.jar.entries.put("fabric.mod.json",
                        new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
                                .toJson(jo).getBytes(StandardCharsets.UTF_8));
                ctx.log("[rename] fabric.mod.json entrypoints rewritten");
            }
        } catch (Throwable t) {
            ctx.log("[!] fabric.mod.json rewrite failed: " + t);
        }
    }

    private static String pkg(String internal) {
        int i = internal.lastIndexOf('/');
        return i < 0 ? "" : internal.substring(0, i);
    }

    /** true if the class lives in a keepPackages subtree (mixin 弱混: keep name + members). */
    private static boolean inKeepPackages(Options opt, String internal) {
        for (String p : opt.keepPackages) {
            if (internal.equals(p) || internal.startsWith(p + "/")) return true;
        }
        return false;
    }

    /** A class is a mixin if listed in a *.mixins.json config, annotated @Mixin, or any member carries a mixin annotation. */
    private static boolean isMixinClass(Ctx ctx, String internalName) {
        if (ctx.mixinClasses.contains(internalName)) return true;
        ClassNode cn = ctx.jar.classes.get(internalName);
        if (cn == null) return false;
        if (ctx.isMixin(cn)) return true;
        // robust fallback: a class whose methods/fields carry @Shadow/@Inject/@Accessor/... IS a mixin
        if (cn.methods != null) {
            for (var mn : cn.methods) if (ctx.hasMixinMethodAnnotation(mn)) return true;
        }
        if (cn.fields != null) {
            for (var fn : cn.fields) if (ctx.hasMixinFieldAnnotation(fn)) return true;
        }
        return false;
    }

    /** Read every *.mixins.json and record its declared mixin classes (ORIGINAL internal names). */
    private static void collectMixinClassesFromConfigs(Ctx ctx) {
        Gson gson = new Gson();
        for (Map.Entry<String, byte[]> en : ctx.jar.entries.entrySet()) {
            String n = en.getKey();
            if (!n.endsWith(".mixins.json") && !n.endsWith("-mixins.json")) continue;
            try {
                JsonObject jo = gson.fromJson(new String(en.getValue(), StandardCharsets.UTF_8), JsonObject.class);
                if (jo == null || !jo.has("package")) continue;
                String pkg = jo.get("package").getAsString().replace('.', '/');
                for (String listKey : List.of("mixins", "client", "server")) {
                    if (!jo.has(listKey) || !jo.get(listKey).isJsonArray()) continue;
                    for (JsonElement el : jo.getAsJsonArray(listKey)) {
                        ctx.mixinClasses.add(pkg + "/" + el.getAsString().replace('.', '/'));
                    }
                }
            } catch (Throwable t) {
                ctx.log("[!] mixin config scan failed: " + n + " (" + t + ")");
            }
        }
    }

    public static byte[] serialize(ClassNode cn) {
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    @SuppressWarnings("unused")
    private static void noop(Util.Rng r) {
    }
}

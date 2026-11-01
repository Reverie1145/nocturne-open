package cn.reverie.obf.passes;

import cn.reverie.obf.Ctx;
import cn.reverie.obf.Util;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pass 6: reference hiding via invokedynamic.
 *
 * Direct call/field sites are replaced with indy whose bootstrap decrypts the
 * real target ("owner\x01name\x01desc", XOR-encoded with a key derived from
 * the per-site junk name + mode) and resolves it through MethodHandles.
 * The plaintext target only exists inside the JVM's CallSite cache at runtime.
 *
 * Excluded: constructors/super calls, JDK/LWJGL/mixin-invoke owners, native
 * targets, mixin classes (the Mixin transformer copies their bodies verbatim).
 */
public final class RefPass {

    private static final Set<String> EXCLUDED_OWNERS = Set.of(
            "java/lang/invoke/MethodHandles", "java/lang/invoke/MethodHandle",
            "java/lang/invoke/MethodType", "java/lang/invoke/CallSite",
            "java/lang/invoke/LambdaMetafactory", "java/lang/invoke/StringConcatFactory",
            "cn/reverie/nocturne/StringVault", "cn/reverie/nocturne/NativeGuard",
            "cn/reverie/nocturne/NBootstrap", "cn/reverie/nocturne/NRes",
            "cn/reverie/nocturne/PackBoot", "cn/reverie/nocturne/DecryptingClassLoader");

    private static final Set<String> REF_EXCLUDE = Set.of(
            "cn/reverie/nocturne/StringVault",
            "cn/reverie/nocturne/NativeGuard",
            "cn/reverie/nocturne/NBootstrap",
            "cn/reverie/nocturne/NRes",
            "cn/reverie/nocturne/PackBoot",
            "cn/reverie/nocturne/DecryptingClassLoader",
            "cn/reverie/client/nocturneClient"
    );

    private static boolean ownerExcluded(String owner) {
        return owner.startsWith("java/") || owner.startsWith("javax/")
                || owner.startsWith("jdk/") || owner.startsWith("sun/")
                || owner.startsWith("org/lwjgl/") || owner.startsWith("org/spongepowered/")
                || owner.startsWith("[") || EXCLUDED_OWNERS.contains(owner);
    }

    /**
     * Guard: the MethodType the indy carries is what BsmGen.boot() slices to look
     * the target up. Assert here that slicing yields exactly the target descriptor,
     * otherwise every site of that mode resolves against the wrong type at runtime
     * (e.g. PUTFIELD passing the receiver type to findSetter -> NoSuchFieldError).
     */
    private static void checkLookupShape(String owner, String name, String desc, int mode, Type callType) {
        Type target = Type.getType(desc);
        Type sliced;
        switch (mode) {
            case 0, 6 -> sliced = Type.getMethodType(target.getReturnType(),
                    dropFirst(callType.getArgumentTypes()));
            case 1 -> sliced = callType;
            case 2, 3 -> sliced = callType.getReturnType();
            case 4 -> sliced = callType.getArgumentTypes().length == 2
                    ? callType.getArgumentTypes()[1] : null;
            case 5 -> sliced = callType.getArgumentTypes().length == 1
                    ? callType.getArgumentTypes()[0] : null;
            default -> sliced = null;
        }
        if (sliced == null || !sliced.equals(target)) {
            throw new IllegalStateException("[refs] indy shape mismatch mode=" + mode
                    + " owner=" + owner + " name=" + name + " desc=" + desc
                    + " callType=" + callType.getDescriptor() + " sliced="
                    + (sliced == null ? "null" : sliced.getDescriptor()));
        }
    }

    private static Type[] dropFirst(Type[] a) {
        Type[] out = new Type[Math.max(0, a.length - 1)];
        for (int i = 1; i < a.length; i++) out[i - 1] = a[i];
        return out;
    }

    public void apply(Ctx ctx) {
        if (!ctx.opt.refs) return;

        // ---- generate two BSM variants in random packages ----
        List<String> bsms = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String pkg = ctx.names.freshPackage();
            String internal = pkg + "/" + ctx.names.freshName(5, 12);
            ctx.generated.add(internal);
            ClassNode cn = BsmGen.emit(internal, i, ctx);
            byte[] bytes = BsmGen.write(cn, ctx.jar);
            ctx.jar.classes.put(internal, cn);
            ctx.jar.entries.put(internal + ".class", bytes);
            bsms.add(internal);
        }

        Handle[] bsmHandles = new Handle[bsms.size()];
        String bsmDesc = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                + "Ljava/lang/invoke/MethodType;ILjava/lang/String;)Ljava/lang/invoke/CallSite;";
        for (int i = 0; i < bsms.size(); i++) {
            bsmHandles[i] = new Handle(Opcodes.H_INVOKESTATIC, bsms.get(i), "boot", bsmDesc, false);
        }

        int rewritten = 0;
        java.util.Map<String, String> newToOld = new java.util.HashMap<>();
        for (var me : ctx.map.classMap.entrySet()) newToOld.put(me.getValue(), me.getKey());

        for (var e : ctx.jar.classes.entrySet()) {
            if (ctx.generated.contains(e.getKey())) continue;
            String origName = newToOld.getOrDefault(e.getKey(), e.getKey());
            if (REF_EXCLUDE.contains(origName) || REF_EXCLUDE.contains(e.getKey())) continue;
            ClassNode cn = e.getValue();
            if (ctx.inKeepPackage(cn.name)) continue; // mixin 弱混: keep mixin bodies byte-compatible
            if (ctx.opt.isSoft(origName) || ctx.opt.isSoft(cn.name)) continue; // soft packages: no ref indy
            // mixin classes: skip only methods carrying mixin annotations
            String self = cn.name;
            for (MethodNode mn : cn.methods) {
                if (mn.name.startsWith("<")) continue; // <init>/<clinit>: no ref-hiding (write static-final via BSM is illegal)
                if (ctx.isMixin(cn) && ctx.hasMixinMethodAnnotation(mn)) continue;
                if (mn.instructions == null) continue;
                for (var i = mn.instructions.getFirst(); i != null; i = i.getNext()) {
                    int mode = -1;
                    String owner = null, name = null, desc = null;
                    Type callType = null;
                    if (i instanceof MethodInsnNode m) {
                        owner = m.owner;
                        name = m.name;
                        desc = m.desc;
                        if (m.getOpcode() == Opcodes.INVOKESTATIC) {
                            mode = 1;
                            callType = Type.getMethodType(desc);
                        } else if (m.getOpcode() == Opcodes.INVOKEVIRTUAL
                                || m.getOpcode() == Opcodes.INVOKEINTERFACE) {
                            mode = 0;
                            // indy MethodType must carry the receiver (desc has only explicit args)
                            Type ret = Type.getReturnType(desc);
                            Type[] args = Type.getArgumentTypes(desc);
                            Type[] withRecv = new Type[args.length + 1];
                            withRecv[0] = Type.getObjectType(owner);
                            System.arraycopy(args, 0, withRecv, 1, args.length);
                            callType = Type.getMethodType(ret, withRecv);
                        } else if (m.getOpcode() == Opcodes.INVOKESPECIAL
                                && !name.equals("<init>") && owner.equals(self)) {
                            mode = 6; // same-class private — findSpecial via privateLookupIn
                            // the raw desc lacks the receiver; indy's MethodType must include it
                            Type ret = Type.getReturnType(desc);
                            Type[] args = Type.getArgumentTypes(desc);
                            Type[] withRecv = new Type[args.length + 1];
                            withRecv[0] = Type.getObjectType(owner);
                            System.arraycopy(args, 0, withRecv, 1, args.length);
                            callType = Type.getMethodType(ret, withRecv);
                        }
                    } else if (i instanceof FieldInsnNode f) {
                        owner = f.owner;
                        name = f.name;
                        desc = f.desc;
                        Type fieldType = Type.getType(desc);
                        Type ownerType = Type.getObjectType(owner);
                        switch (f.getOpcode()) {
                            case Opcodes.GETFIELD -> {
                                mode = 2;
                                callType = Type.getMethodType(fieldType, ownerType);
                            }
                            case Opcodes.GETSTATIC -> {
                                mode = 3;
                                callType = Type.getMethodType(fieldType);
                            }
                            case Opcodes.PUTFIELD -> {
                                mode = 4;
                                callType = Type.getMethodType(Type.VOID_TYPE, ownerType, fieldType);
                            }
                            case Opcodes.PUTSTATIC -> {
                                mode = 5;
                                callType = Type.getMethodType(Type.VOID_TYPE, fieldType);
                            }
                            default -> {
                            }
                        }
                    }
                    if (mode < 0 || owner == null) continue;
                    if (ownerExcluded(owner) || name.startsWith("<")) continue;
                    if (ctx.generated.contains(owner)) continue;

                    checkLookupShape(owner, name, desc, mode, callType);
                    String junkName = ctx.names.freshMember();
                    int variant = ctx.rng.nextInt(bsmHandles.length);
                    String ref = BsmGen.encode(owner, name, desc, junkName, mode, variant);
                    var indy = new org.objectweb.asm.tree.InvokeDynamicInsnNode(
                            junkName, callType.getDescriptor(), bsmHandles[variant], mode, ref);
                    mn.instructions.insert(i, indy);
                    mn.instructions.remove(i);
                    rewritten++;
                }
            }
        }
        ctx.log("[refs] hid " + rewritten + " call/field sites behind invokedynamic");
    }

    @SuppressWarnings("unused")
    private static void touch(Label l) {
    }
}

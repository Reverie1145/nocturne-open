package cn.reverie.obf.passes;

import cn.reverie.obf.Ctx;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Pass 7a: decompiler traps.
 * - misleading SourceFile + junk SourceDebugExtension on every scoped class
 * - straight-line arithmetic junk methods and junk fields (branch-free, so
 *   no frames are required and nothing can break verification)
 */
public final class TrapPass {

    public void apply(Ctx ctx) {
        int traps = 0;
        for (var e : ctx.jar.classes.entrySet()) {
            if (ctx.generated.contains(e.getKey())) continue;
            ClassNode cn = e.getValue();
            if (!ctx.opt.inScope(originalOf(ctx, cn.name)) && !ctx.opt.inScope(cn.name)) continue;

            cn.sourceFile = ctx.names.freshName(6, 16) + ".java";
            cn.sourceDebug = junkSmap(ctx);
            if (cn.visibleAnnotations == null && !ctx.isMixin(cn)) {
                // junk field (never read) — interfaces/annotations reject non-pubic-static-final
                // fields, so only add it to concrete classes
                if ((cn.access & org.objectweb.asm.Opcodes.ACC_INTERFACE) == 0) {
                    cn.fields.add(new FieldNode(org.objectweb.asm.Opcodes.ACC_PRIVATE
                            | org.objectweb.asm.Opcodes.ACC_STATIC,
                            ctx.names.freshMember(), "J", null, null));
                }
                // junk private-static method is legal in interfaces since Java 9
                cn.methods.add(junkMethod(ctx));
                cn.methods.add(junkMethod(ctx)); // second junk method
                // add a fake deprecated annotation to make decompiler output noisier
                if (cn.visibleAnnotations == null) {
                    cn.visibleAnnotations = new java.util.ArrayList<>();
                }
                cn.visibleAnnotations.add(new org.objectweb.asm.tree.AnnotationNode("Ljava/lang/Deprecated;"));
                traps++;
            }
        }
        ctx.log("[traps] poisoned " + traps + " classes");
    }

    private static String originalOf(Ctx ctx, String renamed) {
        if (ctx.map == null) return renamed;
        for (var e : ctx.map.classMap.entrySet()) {
            if (e.getValue().equals(renamed)) return e.getKey();
        }
        return renamed;
    }

    private static String junkSmap(Ctx ctx) {
        StringBuilder sb = new StringBuilder("SMAP\n");
        for (int i = 0; i < 8; i++) {
            sb.append(ctx.names.freshName(3, 9)).append(".java\n");
        }
        sb.append("*S ").append(ctx.names.freshName(4, 8)).append("\n*F\n+1 ");
        sb.append(ctx.names.freshName(4, 12)).append("\n");
        sb.append(ctx.names.freshName(4, 12)).append(".kt\n*L\n");
        for (int i = 0; i < 40; i++) sb.append(ctx.rng.nextInt(400)).append('#').append(1 + ctx.rng.nextInt(9)).append('\n');
        sb.append("*E\n");
        return sb.toString();
    }

    /** Branch-free junk — safe without StackMapTable. */
    private static MethodNode junkMethod(Ctx ctx) {
        MethodNode mn = new MethodNode(org.objectweb.asm.Opcodes.ACC_PRIVATE
                | org.objectweb.asm.Opcodes.ACC_STATIC, ctx.names.freshMember(), "(I)J", null, null);
        mn.maxStack = 6;
        mn.maxLocals = 2;
        mn.visitInsn(org.objectweb.asm.Opcodes.ICONST_0);
        mn.visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, 1);
        int n = 24 + ctx.rng.nextInt(120);
        for (int i = 0; i < n; i++) {
            int v = ctx.rng.nextInt();
            mn.visitLdcInsn(v);
            mn.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 1);
            mn.visitInsn(ctx.rng.nextBool(0.5)
                    ? org.objectweb.asm.Opcodes.IXOR : org.objectweb.asm.Opcodes.IADD);
            mn.visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, 1);
            if (i % 3 == 0) {
                mn.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 1);
                mn.visitIntInsn(org.objectweb.asm.Opcodes.BIPUSH, 13);
                mn.visitInsn(org.objectweb.asm.Opcodes.IREM);
                mn.visitVarInsn(org.objectweb.asm.Opcodes.ISTORE, 1);
            }
        }
        mn.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 0);
        mn.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 1);
        mn.visitInsn(org.objectweb.asm.Opcodes.IXOR);
        mn.visitInsn(org.objectweb.asm.Opcodes.I2L);
        mn.visitLdcInsn(0x5DEECE66DL);
        mn.visitInsn(org.objectweb.asm.Opcodes.LMUL);
        mn.visitInsn(org.objectweb.asm.Opcodes.LRETURN);
        return mn;
    }

    @SuppressWarnings("unused")
    private static void touch(List<ClassNode> l) {
    }
}

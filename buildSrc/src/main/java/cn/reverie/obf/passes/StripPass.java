package cn.reverie.obf.passes;

import cn.reverie.obf.Ctx;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Iterator;

/** Pass 1: strip everything that makes bytecode readable — debug tables, Kotlin metadata, line numbers. */
public final class StripPass {

    private static final String KOTLIN_METADATA = "Lkotlin/Metadata;";

    public void apply(Ctx ctx) {
        int n = 0;
        for (ClassNode cn : ctx.jar.classes.values()) {
            if (!ctx.opt.stripDebug && !ctx.opt.stripKotlinMetadata) continue;
            if (ctx.opt.stripDebug) {
                cn.sourceFile = null;
                cn.sourceDebug = null;
                // keep InnerClasses/nest attrs: the JVM and invokedynamic need them
            }
            if (ctx.opt.stripKotlinMetadata) {
                n += stripAnnotation(cn.visibleAnnotations);
                n += stripAnnotation(cn.invisibleAnnotations);
            }
            for (MethodNode mn : cn.methods) {
                if (ctx.opt.stripDebug) {
                    mn.localVariables = null;
                    mn.visibleLocalVariableAnnotations = null;
                    mn.invisibleLocalVariableAnnotations = null;
                    if (mn.instructions != null) {
                        Iterator<AbstractInsnNode> it = mn.instructions.iterator();
                        while (it.hasNext()) {
                            AbstractInsnNode insn = it.next();
                            if (insn instanceof LineNumberNode) it.remove();
                        }
                    }
                }
                if (ctx.opt.stripKotlinMetadata) {
                    n += stripAnnotation(mn.visibleAnnotations);
                    n += stripAnnotation(mn.invisibleAnnotations);
                }
            }
        }
        ctx.log("[strip] removed " + n + " kotlin metadata annotations, debug tables cleared");
    }

    private int stripAnnotation(java.util.List<AnnotationNode> list) {
        if (list == null) return 0;
        int before = list.size();
        list.removeIf(a -> a.desc.equals(KOTLIN_METADATA));
        return before - list.size();
    }

    @SuppressWarnings("unused")
    private static boolean isEnum(ClassNode cn) {
        return (cn.access & Opcodes.ACC_ENUM) != 0;
    }
}

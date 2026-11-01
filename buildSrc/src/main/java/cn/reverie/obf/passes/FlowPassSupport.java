package cn.reverie.obf.passes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Bytecode emission helpers shared by flow-flattening stages. */
final class FlowPassSupport {
    private FlowPassSupport() {}
    static boolean bail(java.util.Map<String, Integer> reasons, String why) {
        reasons.merge(why, 1, Integer::sum);
        return false;
    }

    static void setState(InsnList out, int slot, int value, LabelNode disp) {
        pushInt(out, value);
        out.add(new VarInsnNode(Opcodes.ISTORE, slot));
        out.add(new JumpInsnNode(Opcodes.GOTO, disp));
    }

    static void pushAndGo(InsnList out, int slot, int value, LabelNode join) {
        pushInt(out, value);
        out.add(new JumpInsnNode(Opcodes.GOTO, join));
    }

    static boolean isBranch(int op) {
        return op == Opcodes.GOTO || op == Opcodes.JSR || op == Opcodes.RET
                || (op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE)
                || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL
                || op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH;
    }

    static int termKind(AbstractInsnNode last, boolean lastIsBranch) {
        if (!lastIsBranch) {
            if (last != null) {
                int op = last.getOpcode();
                if (op == Opcodes.ATHROW || (op >= Opcodes.IRETURN && op <= Opcodes.RETURN)) return Kind.RETURN;
            }
            return Kind.FALL;
        }
        int op = last.getOpcode();
        if (op == Opcodes.GOTO) return Kind.GOTO;
        if (op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH) return Kind.SWITCH;
        if ((op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE) || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL) {
            return Kind.IF;
        }
        return Kind.FALL;
    }

    static void pushInt(InsnList out, int v) {
        if (v >= -1 && v <= 5) out.add(new InsnNode(Opcodes.ICONST_0 + v));
        else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) out.add(new IntInsnNode(Opcodes.BIPUSH, v));
        else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) out.add(new IntInsnNode(Opcodes.SIPUSH, v));
        else out.add(new LdcInsnNode(v));
    }

    static final class Kind {
        static final int FALL = 0, GOTO = 1, IF = 2, SWITCH = 3, RETURN = 4;
    }
}

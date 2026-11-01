package cn.reverie.obf.passes;

import cn.reverie.obf.Ctx;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Map;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

/**
 * Pass 4: number obfuscation. Integer/Long constants become XOR or ADD/SUB
 * chains with per-build random operands.
 */
public final class NumberPass {

    public void apply(Ctx ctx) {
        if (!ctx.opt.numbers) return;
        int n = 0;
        for (Map.Entry<String, ClassNode> e : ctx.jar.classes.entrySet()) {
            if (ctx.generated.contains(e.getKey())) continue;
            ClassNode cn = e.getValue();
            for (var mn : cn.methods) {
                if (mn.instructions == null) continue;
                for (AbstractInsnNode i = mn.instructions.getFirst(); i != null; ) {
                    AbstractInsnNode next = i.getNext();
                    if (i instanceof LdcInsnNode ldc) {
                        InsnList il = null;
                        if (ldc.cst instanceof Integer v && Math.abs(v) > 6) {
                            il = obfInt(v, ctx);
                        } else if (ldc.cst instanceof Long v && Math.abs(v) > 6) {
                            il = obfLong(v, ctx);
                        } else if (ldc.cst instanceof Float v && Float.isFinite(v) && v != 0f) {
                            il = obfFloat(v, ctx);
                        } else if (ldc.cst instanceof Double v && Double.isFinite(v) && v != 0d) {
                            il = obfDouble(v, ctx);
                        }
                        if (il != null) {
                            mn.instructions.insert(ldc, il);
                            mn.instructions.remove(ldc);
                            mn.maxStack = Math.max(mn.maxStack == -1 ? 0 : mn.maxStack,
                                    (mn.maxStack == -1 ? 0 : mn.maxStack) + 4);
                            n++;
                        }
                    }
                    i = next;
                }
            }
        }
        ctx.log("[numbers] rewrote " + n + " constants");
    }

    private InsnList obfInt(int v, Ctx ctx) {
        InsnList il = new InsnList();
        int depth = 1 + ctx.rng.nextInt(2); // 1-2 layers
        int cur = v;
        int[] ops = new int[depth];
        int[] vals = new int[depth];
        boolean[] isXor = new boolean[depth];
        for (int d = depth - 1; d >= 0; d--) {
            vals[d] = ctx.rng.nextInt();
            isXor[d] = ctx.rng.nextBool(0.5);
            cur = isXor[d] ? (cur ^ vals[d]) : (cur - vals[d]);
        }
        il.add(new LdcInsnNode(cur));
        for (int d = 0; d < depth; d++) {
            il.add(new LdcInsnNode(vals[d]));
            il.add(new InsnNode(isXor[d] ? org.objectweb.asm.Opcodes.IXOR : org.objectweb.asm.Opcodes.IADD));
        }
        return il;
    }

    private InsnList obfLong(long v, Ctx ctx) {
        InsnList il = new InsnList();
        int depth = 1 + ctx.rng.nextInt(2);
        long cur = v;
        long[] vals = new long[depth];
        boolean[] isXor = new boolean[depth];
        for (int d = depth - 1; d >= 0; d--) {
            vals[d] = ctx.rng.nextLong();
            isXor[d] = ctx.rng.nextBool(0.5);
            cur = isXor[d] ? (cur ^ vals[d]) : (cur - vals[d]);
        }
        il.add(new LdcInsnNode(cur));
        for (int d = 0; d < depth; d++) {
            il.add(new LdcInsnNode(vals[d]));
            il.add(new InsnNode(isXor[d] ? org.objectweb.asm.Opcodes.LXOR : org.objectweb.asm.Opcodes.LADD));
        }
        return il;
    }

    private InsnList obfFloat(float v, Ctx ctx) {
        InsnList il = new InsnList();
        int bits = Float.floatToRawIntBits(v);
        InsnList inner = obfInt(bits, ctx);
        il.add(inner);
        il.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKESTATIC, "java/lang/Float",
                "intBitsToFloat", "(I)F", false));
        return il;
    }

    private InsnList obfDouble(double v, Ctx ctx) {
        InsnList il = new InsnList();
        long bits = Double.doubleToRawLongBits(v);
        InsnList inner = obfLong(bits, ctx);
        il.add(inner);
        il.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKESTATIC, "java/lang/Double",
                "longBitsToDouble", "(J)D", false));
        return il;
    }

    @SuppressWarnings("unused")
    private static void touch(Ctx c) {
    }
}

package cn.reverie.obf.passes;

import static cn.reverie.obf.passes.FlowPassSupport.*;

import cn.reverie.obf.Ctx;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pass 5: control-flow flattening.
 *
 * Method bodies become a dispatcher loop: while(true) switch(state). Original
 * basic blocks are re-emitted as sparse-switch cases; branches become state
 * assignments; opaque predicates ((t*t-t)&1 == 0, always true) route noise
 * transitions through dead blocks that rejoin the real flow, so even perfect
 * static analysis must evaluate arithmetic to recover the graph.
 *
 * Exception handler entry blocks live OUTSIDE the switch (they are entered
 * with the exception on the stack and must not also be dispatcher targets —
 * that would merge incompatible stack shapes). Bogus blocks likewise never
 * target handler blocks.
 *
 * The new body is swapped in only after the whole method was built; any
 * unexpected shape leaves the method untouched.
 */
public final class FlowPass {

    /** ISTORE(54) .. ASTORE_3(78) — every local-store opcode, shorthand forms included. */
    private static final int LAST_LOCAL_STORE_OPCODE = 78;
    public final Set<ClassNode> flattened = new HashSet<>();
    public final Set<MethodNode> flattenedMethods = new HashSet<>();

    public void apply(Ctx ctx) {
        if (!ctx.opt.flow) {
            ctx.log("[flow] disabled by config");
            return;
        }
        int ok = 0, skip = 0;
        java.util.Map<String, Integer> reasons = new java.util.HashMap<>();
        for (Map.Entry<String, ClassNode> e : ctx.jar.classes.entrySet()) {
            if (ctx.generated.contains(e.getKey())) continue;
            ClassNode cn = e.getValue();
            if (ctx.inKeepPackage(cn.name)) continue; // mixin 弱混: keep mixin bodies byte-compatible
            // mixin classes: only @Inject/@Unique-style bodies get flattened;
            // @Overwrite bodies are copied verbatim by the Mixin transformer (skipped via eligibleWhy)
            // soft packages (UI render path): renamed + strings, but no flattening — a wrongly
            // flattened body silently skips blocks (nothing drawn) instead of failing loudly
            if (ctx.opt.isSoft(ctx.map != null ? ctx.map.originalOf(cn.name) : cn.name) || ctx.opt.isSoft(cn.name)) {
                continue;
            }

            for (MethodNode mn : cn.methods) {
                if (ctx.isMixin(cn) && ctx.hasMixinMethodAnnotation(mn)) { reasons.merge("mixin-annotated", 1, Integer::sum); skip++; continue; }
                String why = eligibleWhy(mn, ctx);
                if (why != null) {
                    skip++;
                    reasons.merge(why, 1, Integer::sum);
                    continue;
                }
                try {
                    if (flatten(mn, cn, ctx, reasons)) ok++;
                    else {
                        skip++;
                        reasons.merge("flatten-false", 1, Integer::sum);
                    }
                } catch (Throwable t) {
                    skip++;
                    reasons.merge("exception", 1, Integer::sum);
                    ctx.log("[flow] kept original body: " + cn.name + "#" + mn.name + " (" + t + ")");
                }
            }
        }
        ctx.flowSkipped = skip;
        ctx.log("[flow] flattened " + ok + " methods, kept " + skip + " reasons=" + reasons);
    }

    private String eligibleWhy(MethodNode mn, Ctx ctx) {
        if (mn.instructions == null || mn.instructions.size() == 0) return "empty";
        if (mn.name.startsWith("<")) return "ctor";
        if ((mn.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return "abstract";
        int size = 0;
        for (AbstractInsnNode i = mn.instructions.getFirst(); i != null; i = i.getNext()) {
            int op = i.getOpcode();
            if (op == Opcodes.JSR || op == Opcodes.RET) return "jsr";
            if (op == Opcodes.MONITORENTER || op == Opcodes.MONITOREXIT) return "monitor";
            if (!(i instanceof LabelNode || i instanceof FrameNode || i instanceof LineNumberNode)) size++;
        }
        if (size < 14) return "small";
        if (size > ctx.opt.flowMaxInsns) return "huge";
        if (mn.desc.contains("Lkotlin/coroutines/Continuation;")) return "suspend";
        if (handlerStoresException(mn)) return "exc-slot";
        return null;
    }

    /**
     * True when a handler entry block begins by storing the exception into a local
     * slot. Such a handler is emitted outside the dispatcher switch, but the block
     * that follows it becomes a switch case, so the verifier merges the switch-entry
     * locals (where the slot still holds whatever the method put there, typically an
     * Object method parameter) with the handler locals (Throwable). The merged slot
     * type degrades to Object and the trailing athrow then fails verification with
     * "Bad type on operand stack". Bodies shaped like this are left untouched.
     */
    private static boolean handlerStoresException(MethodNode mn) {
        if (mn.tryCatchBlocks == null || mn.tryCatchBlocks.isEmpty()) return false;
        for (TryCatchBlockNode tc : mn.tryCatchBlocks) {
            AbstractInsnNode i = tc.handler;
            while (i != null && (i instanceof LabelNode || i instanceof FrameNode || i instanceof LineNumberNode)) {
                i = i.getNext();
            }
            if (i == null) continue;
            int op = i.getOpcode();
            if (op >= Opcodes.ISTORE && op <= LAST_LOCAL_STORE_OPCODE) return true;
        }
        return false;
    }

    private boolean eligible(MethodNode mn, Ctx ctx) {
        if (mn.instructions == null || mn.instructions.size() == 0) return false;
        if (mn.name.startsWith("<")) return false;
        if ((mn.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return false;
        int size = 0;
        for (AbstractInsnNode i = mn.instructions.getFirst(); i != null; i = i.getNext()) {
            int op = i.getOpcode();
            if (op == Opcodes.JSR || op == Opcodes.RET
                    || op == Opcodes.MONITORENTER || op == Opcodes.MONITOREXIT) return false;
            if (!(i instanceof LabelNode || i instanceof FrameNode || i instanceof LineNumberNode)) size++;
        }
        if (size < 14 || size > ctx.opt.flowMaxInsns) return false;
        if (mn.desc.contains("Lkotlin/coroutines/Continuation;")) return false; // suspend state machines
        // try/catch methods are now supported
        return true;
    }

    /**
     * Emitting moves instruction nodes out of the original list (ASM's add() keeps the old
     * next pointer and relinks prev), so bailing out mid-way would leave a corrupted body
     * behind while still reporting the method as untouched. Snapshot everything first and
     * restore whenever the flattened body is not swapped in.
     */
    private boolean flatten(MethodNode mn, ClassNode cn, Ctx ctx, java.util.Map<String, Integer> reasons) {
        Map<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode i = mn.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof LabelNode l) labelMap.put(l, new LabelNode());
        }
        InsnList snapshot = new InsnList();
        for (AbstractInsnNode i = mn.instructions.getFirst(); i != null; i = i.getNext()) {
            snapshot.add(i.clone(labelMap));
        }
        List<TryCatchBlockNode> tcSnapshot = new ArrayList<>();
        if (mn.tryCatchBlocks != null) {
            for (TryCatchBlockNode tc : mn.tryCatchBlocks) {
                tcSnapshot.add(new TryCatchBlockNode(
                        labelMap.getOrDefault(tc.start, tc.start),
                        labelMap.getOrDefault(tc.end, tc.end),
                        labelMap.getOrDefault(tc.handler, tc.handler),
                        tc.type));
            }
        }
        boolean swapped = false;
        try {
            swapped = flattenInner(mn, cn, ctx, reasons);
            return swapped;
        } finally {
            if (!swapped) {
                mn.instructions = snapshot;
                mn.tryCatchBlocks = tcSnapshot;
            }
        }
    }

    private boolean flattenInner(MethodNode mn, ClassNode cn, Ctx ctx, java.util.Map<String, Integer> reasons) {
        // ---- linearize ----
        List<AbstractInsnNode> real = new ArrayList<>();
        Map<LabelNode, Integer> labelIdx = new HashMap<>();
        int count = 0;
        for (AbstractInsnNode i = mn.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof LabelNode l) {
                labelIdx.putIfAbsent(l, count);
            } else if (!(i instanceof FrameNode) && !(i instanceof LineNumberNode)) {
                real.add(i);
                count++;
            }
        }
        if (real.isEmpty()) return false;
        int n = real.size();

        // ---- leaders ----
        boolean[] leader = new boolean[n + 1];
        leader[0] = true;
        for (int i = 0; i < n; i++) {
            AbstractInsnNode insn = real.get(i);
            int op = insn.getOpcode();
            if (insn instanceof JumpInsnNode j) {
                Integer t = labelIdx.get(j.label);
                if (t == null) return bail(reasons, "leader-null-jump");
                leader[t] = true;
                if (op != Opcodes.GOTO) leader[i + 1] = true;
            } else if (insn instanceof TableSwitchInsnNode s) {
                Integer d = labelIdx.get(s.dflt);
                if (d == null) return bail(reasons, "leader-null-tsw");
                leader[d] = true;
                for (LabelNode l : s.labels) {
                    Integer t = labelIdx.get(l);
                    if (t == null) return bail(reasons, "leader-null-tsw-k");
                    leader[t] = true;
                }
                leader[i + 1] = true;
            } else if (insn instanceof LookupSwitchInsnNode s) {
                Integer d = labelIdx.get(s.dflt);
                if (d == null) return bail(reasons, "leader-null-lsw");
                leader[d] = true;
                for (LabelNode l : s.labels) {
                    Integer t = labelIdx.get(l);
                    if (t == null) return bail(reasons, "leader-null-lsw-k");
                    leader[t] = true;
                }
                leader[i + 1] = true;
            } else if (op == Opcodes.ATHROW || (op >= Opcodes.IRETURN && op <= Opcodes.RETURN)) {
                leader[i + 1] = true;
            }
        }
        for (TryCatchBlockNode tc : mn.tryCatchBlocks) {
            Integer s = labelIdx.get(tc.start), e = labelIdx.get(tc.end), h = labelIdx.get(tc.handler);
            if (s == null || e == null || h == null) return bail(reasons, "tc-null");
            leader[s] = true;
            if (e <= n) leader[e] = true;
            leader[h] = true;
        }

        // ---- blocks ----
        int nb = 0;
        int[] blockOf = new int[n + 1];
        int[] blockStartIdx = new int[n + 1];
        int[] blockEndIdx = new int[n + 1];
        for (int i = 0; i < n; i++) {
            if (leader[i]) {
                if (nb > 0) blockEndIdx[nb - 1] = i;
                blockStartIdx[nb] = i;
                nb++;
            }
            blockOf[i] = nb - 1;
        }
        blockEndIdx[nb - 1] = n;
        blockOf[n] = nb;

        List<LabelNode> handlerLabels = new ArrayList<>();
        for (TryCatchBlockNode tc : mn.tryCatchBlocks) handlerLabels.add(tc.handler);
        Set<Integer> handlerBlocks = new HashSet<>();
        for (LabelNode h : handlerLabels) handlerBlocks.add(blockOf[labelIdx.get(h)]);
        if (handlerBlocks.contains(0)) return bail(reasons, "handler-at-0"); // entry must be a normal block

        // ---- full pre-validation BEFORE touching any node (emission mutates
        // node links, so we must never bail once emitting) ----
        for (int b = 0; b < nb; b++) {
            int start = blockStartIdx[b], end = blockEndIdx[b];
            for (int i = start; i < end; i++) {
                if (isBranch(real.get(i).getOpcode()) && i != end - 1) return bail(reasons, "mid-branch"); // branch mid-block
            }
            AbstractInsnNode last = end > start ? real.get(end - 1) : null;
            boolean lastIsBranch = last != null && isBranch(last.getOpcode());
            int kind = termKind(last, lastIsBranch);
            if (handlerBlocks.contains(b) && kind == Kind.SWITCH) return bail(reasons, "switch-in-handler"); // rare — keep original
            if (kind == Kind.FALL && end >= n) return bail(reasons, "fall-off-end"); // fallthrough off method end
        }

        List<Integer> normalBlocks = new ArrayList<>();
        for (int b = 0; b < nb; b++) {
            if (!handlerBlocks.contains(b)) normalBlocks.add(b);
        }
        if (normalBlocks.isEmpty()) return bail(reasons, "no-normal");

        // ---- emit ----
        InsnList out = new InsnList();
        int stateSlot = mn.maxLocals;
        int tempSlot = mn.maxLocals + 1;
        int nBogus = Math.min(6, Math.max(2, nb / 24));
        int total = nb + nBogus;

        LabelNode disp = new LabelNode();
        LabelNode[] entry = new LabelNode[total];
        for (int i = 0; i < total; i++) entry[i] = new LabelNode();

        pushInt(out, 0); // block 0 = original entry
        pushInt(out, 0); // tempSlot initialized on every path so exception-frame merges never see TOP
        out.add(new VarInsnNode(Opcodes.ISTORE, tempSlot));
        out.add(new VarInsnNode(Opcodes.ISTORE, stateSlot));
        out.add(disp);
        out.add(new VarInsnNode(Opcodes.ILOAD, stateSlot));
        List<Integer> keys = new ArrayList<>();
        List<LabelNode> labels = new ArrayList<>();
        for (int b : normalBlocks) {
            keys.add(b);
            labels.add(entry[b]);
        }
        LabelNode def = new LabelNode();
        out.add(new LookupSwitchInsnNode(def, keys.stream().mapToInt(Integer::intValue).toArray(),
                labels.toArray(new LabelNode[0])));
        out.add(def);
        out.add(new InsnNode(Opcodes.ACONST_NULL));
        out.add(new InsnNode(Opcodes.ATHROW)); // corrupted state only; no NEW keeps frames free of uninitialized types

        List<TryCatchBlockNode> newTCs = new ArrayList<>();
        LabelNode[] blockEnd = new LabelNode[nb];

        // normal blocks
        String[] probe = new String[1];
        for (int b : normalBlocks) {
            int start = blockStartIdx[b], end = blockEndIdx[b];
            LabelNode bEnd = new LabelNode();
            blockEnd[b] = bEnd;
            out.add(entry[b]);
            AbstractInsnNode last = end > start ? real.get(end - 1) : null;
            boolean lastIsBranch = last != null && isBranch(last.getOpcode());
            for (int i = start; i < end; i++) {
                AbstractInsnNode insn = real.get(i);
                if (i == end - 1 && lastIsBranch) break;

                out.add(insn);
            }
            int kind = termKind(last, lastIsBranch);
            if (kind != Kind.RETURN) {
                if (end >= n && kind != Kind.GOTO && kind != Kind.SWITCH && kind != Kind.IF) throw new IllegalStateException("flow: post-validation miss FALL at end");
                LabelNode fake = null;
                int bogusSlot = nb + ctx.rng.nextInt(nBogus);
                if ((kind == Kind.GOTO || kind == Kind.FALL) && ctx.rng.nextBool(0.35)) { // predicate needs an empty operand stack
                    fake = new LabelNode();
                    out.add(new LdcInsnNode(ctx.rng.nextInt()));
                    out.add(new VarInsnNode(Opcodes.ISTORE, tempSlot));
                    out.add(new VarInsnNode(Opcodes.ILOAD, tempSlot));
                    out.add(new VarInsnNode(Opcodes.ILOAD, tempSlot));
                    out.add(new InsnNode(Opcodes.IMUL));
                    out.add(new VarInsnNode(Opcodes.ILOAD, tempSlot));
                    out.add(new InsnNode(Opcodes.ISUB));
                    out.add(new InsnNode(Opcodes.ICONST_1));
                    out.add(new InsnNode(Opcodes.IAND));
                    out.add(new JumpInsnNode(Opcodes.IFNE, fake)); // never taken: t*(t-1) is always even
                }
                switch (kind) {
                    case Kind.GOTO -> setState(out, stateSlot,
                            blockOf[labelIdx.get(((JumpInsnNode) last).label)], disp);
                    case Kind.IF -> {
                        LabelNode tLabel = new LabelNode();
                        out.add(new JumpInsnNode(last.getOpcode(), tLabel));
                        setState(out, stateSlot, blockOf[end], disp);
                        out.add(tLabel);
                        setState(out, stateSlot, blockOf[labelIdx.get(((JumpInsnNode) last).label)], disp);
                    }
                    case Kind.SWITCH -> {
                        LabelNode set = new LabelNode();
                        if (last instanceof TableSwitchInsnNode s) {
                            LabelNode[] kl = new LabelNode[s.labels.size()];
                            LabelNode d = new LabelNode();
                            for (int i = 0; i < kl.length; i++) kl[i] = new LabelNode();
                            out.add(new TableSwitchInsnNode(s.min, s.max, d, kl));
                            for (int i = 0; i < kl.length; i++) {
                                out.add(kl[i]);
                                pushAndGo(out, stateSlot, blockOf[labelIdx.get(s.labels.get(i))], set);
                            }
                            out.add(d);
                            pushAndGo(out, stateSlot, blockOf[labelIdx.get(s.dflt)], set);
                        } else {
                            LookupSwitchInsnNode s = (LookupSwitchInsnNode) last;
                            LabelNode[] kl = new LabelNode[s.labels.size()];
                            LabelNode d = new LabelNode();
                            for (int i = 0; i < kl.length; i++) kl[i] = new LabelNode();
                            out.add(new LookupSwitchInsnNode(d,
                                    s.keys.stream().mapToInt(Integer::intValue).toArray(), kl));
                            for (int i = 0; i < kl.length; i++) {
                                out.add(kl[i]);
                                pushAndGo(out, stateSlot, blockOf[labelIdx.get(s.labels.get(i))], set);
                            }
                            out.add(d);
                            pushAndGo(out, stateSlot, blockOf[labelIdx.get(s.dflt)], set);
                        }
                        out.add(set);
                        out.add(new VarInsnNode(Opcodes.ISTORE, stateSlot));
                        out.add(new JumpInsnNode(Opcodes.GOTO, disp));
                    }
                    default -> setState(out, stateSlot, blockOf[end], disp); // fallthrough
                }
                if (fake != null) {
                    out.add(fake);
                    out.add(new JumpInsnNode(Opcodes.GOTO, entry[bogusSlot]));
                }
            }
            out.add(bEnd);
        }

        // bogus noise blocks — rejoin a random NORMAL transition so even if taken
        // they preserve semantics
        for (int j = 0; j < nBogus; j++) {
            out.add(entry[nb + j]);
            for (int k = 0; k < 3 + ctx.rng.nextInt(6); k++) {
                out.add(new LdcInsnNode(ctx.rng.nextInt()));
                out.add(new VarInsnNode(Opcodes.ISTORE, tempSlot));
                out.add(new VarInsnNode(Opcodes.ILOAD, tempSlot));
                out.add(new VarInsnNode(Opcodes.ILOAD, tempSlot));
                out.add(new InsnNode(ctx.rng.nextBool(0.5) ? Opcodes.IXOR : Opcodes.IADD));
                out.add(new VarInsnNode(Opcodes.ISTORE, tempSlot));
            }
            setState(out, stateSlot, normalBlocks.get(ctx.rng.nextInt(normalBlocks.size())), disp);
        }

        // try/catch re-scoping: per covered block; handlers are exception-entry blocks
        for (TryCatchBlockNode tc : mn.tryCatchBlocks) {
            int s = labelIdx.get(tc.start), e = Math.min(labelIdx.get(tc.end), n);
            int h = blockOf[labelIdx.get(tc.handler)];
            int sb = blockOf[s], eb = e < n ? blockOf[e] : nb;
            for (int b = sb; b < eb; b++) {
                if (blockEnd[b] != null) {
                    newTCs.add(new TryCatchBlockNode(entry[b], blockEnd[b], entry[h], tc.type));
                }
            }
        }

        // exception-entry blocks (outside switch): body + dispatch
        for (int b = 0; b < nb; b++) {
            if (!handlerBlocks.contains(b)) continue;
            int start = blockStartIdx[b], end = blockEndIdx[b];
            out.add(entry[b]);
            AbstractInsnNode last = end > start ? real.get(end - 1) : null;
            boolean lastIsBranch = last != null && isBranch(last.getOpcode());
            for (int i = start; i < end; i++) {
                AbstractInsnNode insn = real.get(i);
                if (i == end - 1 && lastIsBranch) break;

                out.add(insn);
            }
            int kind = termKind(last, lastIsBranch);
            if (kind == Kind.RETURN) {
                // body ended with return/athrow
            } else if (kind == Kind.GOTO) {
                setState(out, stateSlot, blockOf[labelIdx.get(((JumpInsnNode) last).label)], disp);
            } else if (kind == Kind.IF) {
                LabelNode tLabel = new LabelNode();
                out.add(new JumpInsnNode(last.getOpcode(), tLabel));
                setState(out, stateSlot, blockOf[end], disp);
                out.add(tLabel);
                setState(out, stateSlot, blockOf[labelIdx.get(((JumpInsnNode) last).label)], disp);
            } else if (kind == Kind.SWITCH) { // pre-validated: never fires
                throw new IllegalStateException("flow: post-validation miss SWITCH-in-handler");
            } else {
                if (end >= n) throw new IllegalStateException("flow: post-validation miss end>=n");
                setState(out, stateSlot, blockOf[end], disp);
            }
        }

        // ---- swap in ----
        // Only the finished list can be judged: while emitting, walking next() runs through
        // original nodes that have not been re-linked yet, so a mid-build length mismatch is
        // expected and harmless. A mismatch on the rebuilt list means real corruption.
        out.add(new LabelNode()); // terminal: cuts the stale next pointer of the last emitted real node
        InsnList fresh = new InsnList();
        for (AbstractInsnNode i : out.toArray()) fresh.add(i);
        fresh.add(new LabelNode()); // terminal: cuts any stale next pointer of the last real node
        {
            int chain = 0;
            AbstractInsnNode w = fresh.getFirst();
            while (w != null && chain <= fresh.size() + 1) { chain++; w = w.getNext(); }
            if (chain != fresh.size()) probe[0] = "chain=" + chain + " size=" + fresh.size();
        }
        if (probe[0] != null) {
            return bail(reasons, "insn-chain");
        }
        mn.instructions = fresh;
        mn.tryCatchBlocks = newTCs;
        mn.maxLocals += 2;
        mn.maxStack = Math.max(mn.maxStack == -1 ? 0 : mn.maxStack, 6); // opaque predicates + state pushes need headroom
        flattened.add(cn);
        flattenedMethods.add(mn);
        return true;
    }

}

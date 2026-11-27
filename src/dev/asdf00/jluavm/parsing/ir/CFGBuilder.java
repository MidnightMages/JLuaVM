package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.container.LocalVariable.LocalVariableAccessor;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.IRElement.ConstantNode;
import dev.asdf00.jluavm.parsing.ir.Terminator.Branch;
import dev.asdf00.jluavm.parsing.ir.Terminator.Jump;
import dev.asdf00.jluavm.parsing.ir.Terminator.Return;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Supplier;

/**
 * Incrementally builds the {@link ControlFlowGraph} of a single function while parsing.
 * <p>
 * This builder creates and links blocks via the following steps:
 * <ol>
 *     <li><b>create</b>: has an id but no layout index yet. Used for targets of not yet parsed code (branch arms and
 *     joins, labels jumped to before placement). Edges into it are forward edges.</li>
 *     <li><b>place</b>: appended to the layout in source order and made the current block, which receives all
 *     emitted statements. Edges created later towards it are back edges.</li>
 *     <li><b>terminate</b>: a control flow operation sets its {@link Terminator} and places the next block.</li>
 * </ol>
 * There always is exactly one open current block. After a jump or return, the next block starts out unreachable and
 * is removed in {@link #finish(Position)}, which also bypasses empty jump-only blocks. Placeholder blocks of forward
 * gotos are never placed, see {@link #mergeLabel(JumpLabel, JumpLabel)}.
 * <p>
 * Branches are emitted as {@code branch(cond)}, {@code enterTrue()}, optional {@code enterFalse()} and
 * {@code exitBranch()}, nested in stack order. Each arm ends with a jump to the join block, a missing false arm
 * jumps there directly.
 * <p>
 * To-be-closed variables are closed on the edges leaving their scope: {@link #exitScope(LocalVariableAccessor[])} for
 * the regular scope end, {@link #jump(JumpLabel, LocalVariableAccessor[])} for breaks and gotos and
 * {@link #ret(Position, IRElement[], LocalVariableAccessor[])} for returns.
 */
public final class CFGBuilder {
    private final ControlFlowGraph cfg;
    private final ArrayDeque<BranchFrame> branches = new ArrayDeque<>();
    private final ArrayList<JumpLabel> labels = new ArrayList<>();
    private BasicBlock cur;
    private int nextId = 0;
    private int nextOrder = 0;
    private boolean finished = false;

    public CFGBuilder(boolean hasVararg) {
        var entry = newBlock();
        cfg = new ControlFlowGraph(hasVararg, entry);
        place(entry);
    }

    public void statement(IRElement stat) {
        assert !finished : "builder already finished";
        assert stat != null : "null statement";
        cur.statements.add(stat);
    }

    public void branch(IRElement condition) {
        var frame = new BranchFrame(newBlock(), newBlock(), newBlock());
        cur.setTerminator(new Branch(condition.pos, condition, frame.onTrue, frame.onFalse));
        branches.push(frame);
    }

    public void enterTrue() {
        var frame = branches.peek();
        assert frame != null && frame.state == BranchFrame.State.CREATED : "enterTrue() without matching branch()";
        frame.state = BranchFrame.State.IN_TRUE;
        place(frame.onTrue);
    }

    public void enterFalse() {
        var frame = branches.peek();
        assert frame != null && frame.state == BranchFrame.State.IN_TRUE : "enterFalse() without preceding enterTrue()";
        frame.state = BranchFrame.State.IN_FALSE;
        cur.setTerminator(new Jump(frame.join));
        place(frame.onFalse);
    }

    public void exitBranch() {
        var frame = branches.pop();
        assert frame.state != BranchFrame.State.CREATED : "exitBranch() without entering a branch arm";
        cur.setTerminator(new Jump(frame.join));
        if (frame.state != BranchFrame.State.IN_FALSE) {
            // no false arm, so we continue at the join point
            place(frame.onFalse);
            cur.setTerminator(new Jump(frame.join));
        }
        place(frame.join);
    }

    public void label(JumpLabel label) {
        assert !label.placed : "label %s placed twice".formatted(label);
        var target = blockOf(label);
        cur.setTerminator(new Jump(target));
        place(target);
        label.placed = true;
    }

    public Jump jump(JumpLabel label, LocalVariableAccessor[] toClose) {
        var jump = new Jump(blockOf(label), toClose);
        cur.setTerminator(jump);
        place(newBlock());
        return jump;
    }

    public Jump jump(JumpLabel label) {
        return jump(label, Singletons.EMPTY_LOCAL_ACCESSOR_ARRAY);
    }

    public void ret(Position pos, IRElement[] values, LocalVariableAccessor[] toClose) {
        cur.setTerminator(new Return(pos, values, toClose));
        place(newBlock());
    }

    public void exitScope(LocalVariableAccessor[] toClose) {
        if (toClose.length > 0) {
            var next = newBlock();
            cur.setTerminator(new Jump(next, toClose));
            place(next);
        }
    }

    /**
     * Redirects all jumps to the (unplaced) placeholder label {@code from} to the label {@code into}. Used to resolve
     * forward gotos.
     */
    public void mergeLabel(JumpLabel from, JumpLabel into) {
        assert !from.placed && from.block != null : "%s is not an unresolved placeholder label".formatted(from);
        var placeholder = from.block;
        assert placeholder.order == -1 && placeholder.statements.isEmpty() && placeholder.terminator() == null;
        var target = blockOf(into);
        for (var pred : new ArrayList<>(placeholder.allPredecessors())) {
            pred.retarget(placeholder, target);
        }
        assert placeholder.allPredecessors().isEmpty();
        from.block = target;
        from.placed = true;
    }

    public ControlFlowGraph finish(Position endPos) {
        assert !finished : "builder already finished";
        assert branches.isEmpty() : "unclosed branches";
        assert labels.stream().allMatch(l -> l.placed) : "unplaced labels: " + labels.stream().filter(l -> !l.placed).toList();
        cur.setTerminator(new Return(endPos, new IRElement[]{ConstantNode.ofNil(endPos)},
                Singletons.EMPTY_LOCAL_ACCESSOR_ARRAY));
        finished = true;
        removeUnreachable();
        bypassEmptyJumpBlocks();
        assert verify();
        return cfg;
    }

    private BasicBlock newBlock() {
        return new BasicBlock(nextId++);
    }

    private BasicBlock blockOf(JumpLabel label) {
        if (label.block == null) {
            label.block = newBlock();
            labels.add(label);
        }
        return label.block;
    }

    private void place(BasicBlock block) {
        assert block.order == -1 : "B%d placed twice".formatted(block.id);
        assert cur == null || cur.terminator() != null : "B%d is left open".formatted(cur.id);
        block.order = nextOrder++;
        cfg.blocks.add(block);
        cur = block;
    }

    private void removeUnreachable() {
        var reachable = new HashSet<BasicBlock>();
        var work = new ArrayDeque<BasicBlock>();
        work.push(cfg.entry);
        while (!work.isEmpty()) {
            var b = work.pop();
            if (reachable.add(b)) {
                assert b.order != -1 : "reachable block B%d was never placed".formatted(b.id);
                b.successors().forEach(work::push);
            }
        }
        for (var b : cfg.blocks) {
            if (!reachable.contains(b)) {
                b.clearTerminator();
            }
        }
        cfg.blocks.removeIf(b -> !reachable.contains(b));
    }

    private void bypassEmptyJumpBlocks() {
        for (var b : new ArrayList<>(cfg.blocks)) {
            // a jump closing variables is not empty
            if (b != cfg.entry && b.statements.isEmpty() && b.terminator() instanceof Jump j && j.toClose.length == 0
                    && j.target() != b) {
                var target = j.target();
                for (var pred : new ArrayList<>(b.allPredecessors())) {
                    pred.retarget(b, target);
                }
                b.clearTerminator();
                cfg.blocks.remove(b);
            }
        }
    }

    private boolean verify() {
        assert cfg.entry.allPredecessors().stream().allMatch(p -> p.hasBackEdgeTo(cfg.entry)) : "forward edge into entry";
        for (var b : cfg.blocks) {
            assert b.terminator() != null : "B%d is not terminated".formatted(b.id);
            assert ((Supplier<Boolean>) () -> {
                if (b.terminator() instanceof Jump j) {
                    return j.toClose != null;
                } else if (b.terminator() instanceof Return r) {
                    return r.toClose != null;
                } else {
                    return b.terminator() instanceof Branch;
                }
            }).get() : "B%d has no close list".formatted(b.id);
            for (var succ : b.successors()) {
                assert cfg.blocks.contains(succ) : "B%d jumps to removed block B%d".formatted(b.id, succ.id);
                var list = b.hasBackEdgeTo(succ) ? succ.backEdgePredecessors : succ.predecessors;
                assert list.contains(b) : "B%d missing as predecessor of B%d".formatted(b.id, succ.id);
            }
            for (var pred : b.allPredecessors()) {
                assert pred.successors().contains(b) : "B%d missing as successor of B%d".formatted(b.id, pred.id);
                assert cfg.blocks.contains(pred) : "removed block B%d is predecessor of B%d".formatted(pred.id, b.id);
            }
        }
        return true;
    }

    /**
     * An open branch. Pushed by {@code branch()} in state {@code CREATED} with all three blocks created but unplaced.
     * {@code enterTrue()} places {@link #onTrue} ({@code IN_TRUE}), an optional {@code enterFalse()} jumps to the
     * initially floating {@link #join} and places {@link #onFalse} ({@code IN_FALSE}). {@code exitBranch()} pops the
     * frame, jumps to {@link #join}, lets a never entered {@link #onFalse} jump there directly and places
     * {@link #join}.
     */
    private static final class BranchFrame {
        enum State {
            CREATED, IN_TRUE, IN_FALSE
        }

        final BasicBlock onTrue;
        final BasicBlock onFalse;
        final BasicBlock join;
        State state = State.CREATED;

        BranchFrame(BasicBlock onTrue, BasicBlock onFalse, BasicBlock join) {
            this.onTrue = onTrue;
            this.onFalse = onFalse;
            this.join = join;
        }
    }
}

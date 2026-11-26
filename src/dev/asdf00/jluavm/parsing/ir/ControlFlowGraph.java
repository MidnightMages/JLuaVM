package dev.asdf00.jluavm.parsing.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ControlFlowGraph {
    public final boolean hasVararg;
    public final BasicBlock entry;
    /**
     * All blocks of this function in layout order.
     */
    public final List<BasicBlock> blocks = new ArrayList<>();

    ControlFlowGraph(boolean hasVararg, BasicBlock entry) {
        this.hasVararg = hasVararg;
        this.entry = entry;
    }

    public int maxBlockId() {
        return blocks.stream().mapToInt(b -> b.id).max().orElse(-1);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("CFG(vararg=").append(hasVararg).append(", entry=").append(entry).append(")\n");
        for (var b : blocks) {
            sb.append(b).append(" preds=").append(ids(b.predecessors))
                    .append(" backPreds=").append(ids(b.backEdgePredecessors)).append('\n');
            for (var stat : b.statements) {
                sb.append("    ").append(stat).append('\n');
            }
            sb.append("    -> ").append(b.terminator());
            for (var succ : b.successors()) {
                if (b.hasBackEdgeTo(succ)) {
                    sb.append(" [back edge to ").append(succ).append(']');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String ids(List<BasicBlock> blocks) {
        return blocks.stream().map(BasicBlock::toString).collect(Collectors.joining(", ", "[", "]"));
    }
}

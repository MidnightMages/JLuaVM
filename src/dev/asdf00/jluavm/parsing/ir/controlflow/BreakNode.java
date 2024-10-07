package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.VarInfo;
import dev.asdf00.jluavm.parsing.container.VarScope;
import dev.asdf00.jluavm.parsing.ir.Node;

public class BreakNode extends ClosingNode {
    private final VarScope loopScope;

    public BreakNode(VarScope loopScope, VarInfo[] toClose) {
        super(toClose);
        this.loopScope = loopScope;
    }

    @Override
    public String generate() {
        var sb = new StringBuilder();
        genCloses(sb);
        sb.append("break $loop_").append(loopScope.id).append(';');
        return sb.toString();
    }
}

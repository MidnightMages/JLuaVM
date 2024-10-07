package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.VarScope;
import dev.asdf00.jluavm.parsing.ir.Node;

public class BreakNode extends Node {
    private final VarScope loopScope;

    public BreakNode(VarScope loopScope) {
        this.loopScope = loopScope;
    }

    @Override
    public String generate() {
        return "break $loop_" + loopScope.id + ";";
    }
}

package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.VarInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public abstract class ClosingNode extends Node {
    public VarInfo[] toClose;

    protected ClosingNode(VarInfo[] toClose) {
        this.toClose = toClose;
    }

    protected void genCloses(StringBuilder sb) {
        for (var info : toClose) {
            sb.append("$vm.close(").append(info.jName).append(");\n");
        }
    }
}

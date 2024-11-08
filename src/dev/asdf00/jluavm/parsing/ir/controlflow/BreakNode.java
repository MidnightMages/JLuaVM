package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.Node;

public class BreakNode extends Node {
    public final int scopeCount;
    public int closeCnt;

    public BreakNode(int scopeCount, int closeCnt) {
        this.scopeCount = scopeCount;
        this.closeCnt = closeCnt;
    }

    @Override
    public String generate(CompilationState cState) {
        return IRBlock.genClose(cState, closeCnt) + "\nvm.internalBreak(" + scopeCount + ");\nreturn;";
    }
}

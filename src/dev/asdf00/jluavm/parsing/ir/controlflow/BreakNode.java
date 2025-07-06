package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.Node;

public class BreakNode extends Node {
    public final int scopeCount;
    public int closeCnt;

    public BreakNode(Position sourcePos, int scopeCount, int closeCnt) {
        super(sourcePos);
        this.scopeCount = scopeCount;
        this.closeCnt = closeCnt;
    }

    @Override
    public String generate(CompilationState cState) {
        return IRBlock.genClose(cState, closeCnt) + "\nvm.internalBreak(" + scopeCount + ");\nif(true) return;";
    }
}

package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.Node;

public class DoEndNode extends Node {
    public final Node[] block;
    public final int localCnt;
    public final int toClose;

    public DoEndNode(Position sourcePos, Node[] block, int localCnt, int toClose) {
        super(sourcePos);
        this.block = block;
        this.localCnt = localCnt;
        this.toClose = toClose;
    }

    @Override
    public String generate(CompilationState cState) {
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        if (block.length < 1) {
            return "// empty do-end block";
        }
        var sb = new StringBuilder();
        sb.append(block[0].generate(cState));
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        for (int i = 1; i < block.length; i++) {
            sb.append('\n').append(block[i].generate(cState));
            assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        }
        sb.append(IRBlock.genClose(cState, toClose));
        return sb.toString();
    }
}

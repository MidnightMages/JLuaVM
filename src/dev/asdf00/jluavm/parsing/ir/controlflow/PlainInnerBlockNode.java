package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.Node;

public class PlainInnerBlockNode extends Node {
    public final IRBlock innerBlock;

    public PlainInnerBlockNode(IRBlock innerBlock) {
        this.innerBlock = innerBlock;
    }

    @Override
    public String generate(CompilationState cState) {
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        var info = cState.generateEStackCallInfo(0);
        String bName = innerBlock.generate(cState);
        return """
                vm.callInternal(%d, this::%s);
                return;
                case %d:
                """.formatted(info.resumeLabel(), bName, info.resumeLabel());
    }
}

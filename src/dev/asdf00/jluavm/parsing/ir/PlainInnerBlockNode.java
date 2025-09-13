package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.container.Position;

public class PlainInnerBlockNode extends Node {
    public final IRBlock innerBlock;

    public PlainInnerBlockNode(Position sourcePos, IRBlock innerBlock) {
        super(sourcePos);
        this.innerBlock = innerBlock;
    }

    @Override
    public String generate(CompilationState cState) {
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        var info = cState.generateEStackCallInfo(0);
        String bName = innerBlock.generate(cState);
        return """
                vm.callInternal(%d, %d, this::%s, "%s");
                return;
                case %d:""".formatted(info.resumeLabel(), innerBlock.localsStart, bName, bName, info.resumeLabel());
    }
}

package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.controlflow.FunctionCallNode;

public class PruneToOneNode extends Node {
    public final FunctionCallNode call;

    public PruneToOneNode(FunctionCallNode call) {
        super(call.sourcePos);
        this.call = call;
    }

    @Override
    public String generate(CompilationState cState) {
        call.expectedResultCnt = 1;
        return call.generate(cState);
    }
}

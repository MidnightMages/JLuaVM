package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class FunctionStatementNode extends Node {
    public final FunctionCallNode callNode;

    public FunctionStatementNode(FunctionCallNode callNode) {
        this.callNode = callNode;
    }

    @Override
    public String generate(CompilationState cState) {
        callNode.expectedResultCnt = 0;
        return callNode.generate(cState);
    }
}

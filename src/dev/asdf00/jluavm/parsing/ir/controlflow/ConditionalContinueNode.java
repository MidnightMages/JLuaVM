package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.Node;

public class ConditionalContinueNode extends Node {
    public final Node condition;
    public final boolean breakOnFalse;
    public final int closeCnt;

    public ConditionalContinueNode(Node condition, boolean breakOnFalse, int closeCnt) {
        this.condition = condition;
        this.breakOnFalse = breakOnFalse;
        this.closeCnt = closeCnt;
    }

    @Override
    public String generate(CompilationState cState) {
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        String condCode = condition.generate(cState);
        String closing = IRBlock.genClose(cState, closeCnt);
        String cSpot = cState.popEStack();
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        return """
                %s
                %s
                if (%sRTUtils.isTruthy(%s)) {
                    break resumeSwitch;
                } else {
                    break loop;
                }
                """.formatted(condCode, breakOnFalse ? "" : "!", cSpot);
    }
}

package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.Node;

public class ReturnNode extends Node {
    public final Node[] values;
    public final int closableCnt;

    public ReturnNode(Node[] values, int closableCnt) {
        this.values = values;
        this.closableCnt = closableCnt;
    }

    @Override
    public String generate(CompilationState cState) {
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        var sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            sb.append(values[i].generate(cState)).append('\n');
        }
        String closings = IRBlock.genClose(cState, closableCnt);
        sb.append(closings).append('\n');
        String[] spots = new String[values.length];
        for (int i = spots.length - 1; i >= 0; i--) {
            spots[i] = cState.popEStack();
        }
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        sb.append("vm.returnValue(").append(String.join(", ", spots)).append(");\nif (true) return;");
        return sb.toString();
    }
}

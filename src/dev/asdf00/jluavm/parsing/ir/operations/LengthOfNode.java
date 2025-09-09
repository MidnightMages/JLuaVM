package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class LengthOfNode extends Node {
    public final Node value;

    public LengthOfNode(Position sourcePos, Node value) {
        super(sourcePos);
        this.value = value;
    }

    @Override
    public String generate(CompilationState cState) {
        String result = value.generate(cState) + "\n";
        String vSpot = cState.popEStack();
        EStackCallInfo info = cState.generateEStackCallInfo(1);
        String rSpot = cState.pushEStack();
        String block = """
                %s = lengthOf(%d, vm, %d, %s);
                if (%s == null) {
                    %s
                    return;
                }
                case %d:""".formatted(
                rSpot, sourcePos.line(), info.resumeLabel(), vSpot,
                rSpot,
                info.saveEStack(),
                info.resumeLabel());
        return result + block;
    }
}

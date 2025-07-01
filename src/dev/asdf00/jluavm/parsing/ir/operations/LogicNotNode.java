package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class LogicNotNode extends Node {
    public final Node value;

    public LogicNotNode(Position sourcePos, Node value) {
        super(sourcePos);
        this.value = value;
    }

    @Override
    public String generate(CompilationState cState) {
        String result = value.generate(cState) + "\n";
        String vSpot = cState.popEStack();
        String rSpot = cState.pushEStack();
        result += "\n%s = LuaObject.of(!RTUtils.isTruthy(%s));".formatted(rSpot, vSpot);
        return result;
    }
}

package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class LogicNotNode extends Node {
    public final Node value;

    public LogicNotNode(Node value) {
        this.value = value;
    }

    @Override
    public String generate(CompilationState cState) {
        String vSpot = cState.popEStack();
        String rSpot = cState.pushEStack();
        String result = "%s = LuaObject.of(!RTUtils.isTruthy(%s));".formatted(rSpot, vSpot);
        return result;
    }
}

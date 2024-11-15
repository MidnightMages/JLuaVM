package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRFunction;
import dev.asdf00.jluavm.parsing.ir.Node;

public class FunctionDefinitionNode extends Node {
    public final Node[] captures;
    public final IRFunction function;

    public FunctionDefinitionNode(Node[] captures, IRFunction function) {
        this.captures = captures;
        this.function = function;
    }

    @Override
    public String generate(CompilationState cState) {
        String jClassName = function.generate(cState);
        String[] captSpots = new String[captures.length];
        var sb = new StringBuilder();
        for (int i = 0; i < captures.length; i++) {
            sb.append(captures[i].generate(cState)).append('\n');
            captSpots[i] = cState.peekEStack();
        }
        return "LuaObject.of(new %s(%s))".formatted(jClassName, String.join(", ", captSpots));
    }
}

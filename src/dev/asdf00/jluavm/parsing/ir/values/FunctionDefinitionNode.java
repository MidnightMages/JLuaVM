package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRFunction;
import dev.asdf00.jluavm.parsing.ir.Node;

public class FunctionDefinitionNode extends Node {
    public final Node[] captures;
    public final IRFunction function;

    public FunctionDefinitionNode(Position sourcePos, Node[] captures, IRFunction function) {
        super(sourcePos);
        this.captures = captures;
        this.function = function;
    }

    @Override
    public String generate(CompilationState cState) {
        String innerFuncIdx = function.generate(cState);
        var spot = cState.pushEStack();
        var captSpots = new StringBuilder();
        var sb = new StringBuilder();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < captures.length; i++) {
            sb.append(captures[i].generate(cState)).append('\n');
            captSpots.append(", ").append(cState.peekEStack());
        }
        for (int i = 0; i < captures.length; i++) {
            cState.popEStack();
        }
        return sb.append("%s = LuaObject.of(newInnerFunction(innerFunctions[%s], _ENV%s));".formatted(spot, innerFuncIdx, captSpots)).toString();
    }
}

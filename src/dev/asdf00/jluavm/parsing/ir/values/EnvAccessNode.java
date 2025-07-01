package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class EnvAccessNode extends Node {
    public EnvAccessNode(Position sourcePos) {
        super(sourcePos);
    }

    @Override
    public String generate(CompilationState cState) {
        var spot = cState.pushEStack();
        return spot + " = _ENV[0];";
    }
}

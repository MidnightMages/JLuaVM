package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class EnvAccessNode extends Node {
    @Override
    public String generate(CompilationState cState) {
        var spot = cState.pushEStack();
        return spot + " = _ENV[0];";
    }
}

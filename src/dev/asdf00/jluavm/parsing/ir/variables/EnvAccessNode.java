package dev.asdf00.jluavm.parsing.ir.variables;

import dev.asdf00.jluavm.parsing.ir.Node;

public class EnvAccessNode extends Node {
    public final String varName;

    public EnvAccessNode(String varName) {
        this.varName = varName;
    }

    @Override
    public String generate() {
        return "_ENV.get(\"%s\")".formatted(varName);
    }
}

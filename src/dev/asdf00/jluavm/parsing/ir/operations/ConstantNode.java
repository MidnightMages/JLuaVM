package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.Node;

public class ConstantNode extends Node {
    private final String codeRepr;

    public ConstantNode(String codeRepr) {
        this.codeRepr = codeRepr;
    }

    @Override
    public String generate() {
        return codeRepr;
    }
}

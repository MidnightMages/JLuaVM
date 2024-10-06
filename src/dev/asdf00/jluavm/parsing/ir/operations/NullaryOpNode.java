package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.Node;

public class NullaryOpNode extends Node {
    private final String codeRepr;

    public NullaryOpNode(String codeRepr) {
        this.codeRepr = codeRepr;
    }

    @Override
    public String generate() {
        return codeRepr;
    }
}

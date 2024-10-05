package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.Node;

public abstract class BinaryOpNode extends Node {
    protected final Node x;
    protected final Node y;

    protected BinaryOpNode(Node x, Node y) {
        this.x = x;
        this.y = y;
    }
}

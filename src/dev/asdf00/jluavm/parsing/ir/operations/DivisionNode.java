package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.Node;

public class DivisionNode extends BinaryOpNode {
    protected DivisionNode(Node x, Node y) {
        super(x, y);
    }

    @Override
    public String generate() {
        return "$LuaOpHelper.divide(%s, %s)".formatted(x.generate(), y.generate());
    }
}

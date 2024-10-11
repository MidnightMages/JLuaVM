package dev.asdf00.jluavm.parsing.ir.variables;

import dev.asdf00.jluavm.parsing.ir.Node;

public class DeRefNode extends Node {
    public Node value;
    public Node idx;

    public DeRefNode(Node value, Node idx) {
        this.value = value;
        this.idx = idx;
    }

    @Override
    public String generate() {
        return "TypeUtils$.asType($vm, LuaTable$.class, %s).get(%s)".formatted(value.generate());
    }
}

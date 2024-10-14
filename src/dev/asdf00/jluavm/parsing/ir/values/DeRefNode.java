package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.ir.Node;

public class DeRefNode extends Node {
    public Node value;
    public Node idx;

    public DeRefNode(Node value, Node idx) {
        maxDepth = Math.max(value.getMaxDepth(), idx.getMaxDepth()) + 1;
        this.value = value;
        this.idx = idx;
    }

    @Override
    public String generate() {
        return "".formatted(value.generate());
    }
}

package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.container.Position;

public abstract class Node {
    public final Position sourcePos;

    public Node(Position sourcePos) {
        this.sourcePos = sourcePos;
    }

    public abstract String generate(CompilationState cState);

    protected static String P(String s) {
        return "(" + s + ")";
    }
}

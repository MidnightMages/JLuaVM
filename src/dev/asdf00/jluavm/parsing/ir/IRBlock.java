package dev.asdf00.jluavm.parsing.ir;

public class IRBlock extends Node {
    public final Node[] statements;

    public IRBlock(Node[] statements) {
        this.statements = statements;
    }

    @Override
    public String generate(CompilationState cState) {
        return null;
    }
}

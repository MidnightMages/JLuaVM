package dev.asdf00.jluavm.parsing.ir;

public class StatementNode extends Node {
    protected Node statement;

    @Override
    public String generate() {
        return statement.generate();
    }
}

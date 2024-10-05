package dev.asdf00.jluavm.parsing.ir;

public abstract class Node {
    public abstract String generate();

    protected static String P(String s) {
        return "(" + s + ")";
    }
}

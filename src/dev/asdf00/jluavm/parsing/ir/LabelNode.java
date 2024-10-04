package dev.asdf00.jluavm.parsing.ir;

public class LabelNode extends Node {
    public boolean isLast;
    public int jumpLabel;

    @Override
    public String generate() {
        return "case " + jumpLabel + ":\n";
    }
}

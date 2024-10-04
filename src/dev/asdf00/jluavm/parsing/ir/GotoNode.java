package dev.asdf00.jluavm.parsing.ir;

public class GotoNode extends Node {
    public String stLabel;
    public LabelNode label;

    @Override
    public String generate() {
        return label.isLast
                ? "break $jumpLoop;"
                : "$jumpLabel = " + label.jumpLabel + ";\ncontinue $jumpLoop;";
    }
}

package dev.asdf00.jluavm.parsing.container;

public class LabelInfo {
    public final String label;
    public final int[] closablesPerScope;

    public LabelInfo(String label, int[] closablesPerScope) {
        this.label = label;
        this.closablesPerScope = closablesPerScope;
    }
}

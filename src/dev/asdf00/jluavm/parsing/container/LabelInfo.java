package dev.asdf00.jluavm.parsing.container;

public class LabelInfo {
    public final String label;
    public final int[] localsPerScope;
    public final int[] closablesPerScope;

    public LabelInfo(String label, int[] localsPerScope, int[] closablesPerScope) {
        this.label = label;
        this.localsPerScope = localsPerScope;
        this.closablesPerScope = closablesPerScope;
    }
}

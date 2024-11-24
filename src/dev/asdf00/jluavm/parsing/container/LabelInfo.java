package dev.asdf00.jluavm.parsing.container;

public record LabelInfo(String label, int[] localsPerScope, int[] closablesPerScope) {
}

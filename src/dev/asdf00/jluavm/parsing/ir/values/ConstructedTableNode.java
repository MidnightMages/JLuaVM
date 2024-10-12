package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.ir.Node;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ConstructedTableNode extends Node {
    private final Node[] keyValEntries;

    public ConstructedTableNode(Node[] keyValEntries) {
        this.keyValEntries = keyValEntries;
    }

    @Override
    public String generate() {
        return "LuaTable$.of($vm, " + Arrays.stream(keyValEntries).map(e -> e.generate()).collect(Collectors.joining(", ")) + ")";
    }
}

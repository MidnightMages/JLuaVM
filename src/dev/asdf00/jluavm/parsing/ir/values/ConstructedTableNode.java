package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ConstructedTableNode extends Node {
    private final Node[] keyValEntries;

    public ConstructedTableNode(Node[] keyValEntries) {
        this.keyValEntries = keyValEntries;
    }

    @Override
    public String generate(CompilationState cState) {
        if (keyValEntries.length % 2 == 1) {
            throw new InternalLuaLoadingError("key without argument");
        }
        var sb = new StringBuilder();
        var spotList = new String[keyValEntries.length];
        for (int i = 0; i < keyValEntries.length; i++) {
            sb.append(keyValEntries[i].generate(cState)).append('\n');
        }
        for (int i = keyValEntries.length - 1; i >= 0; i--) {
            spotList[i] = cState.popEStack();
        }
        var spot = cState.pushEStack();
        sb.append(spot).append(" = LuaObject.table(");
        if (spotList.length > 0) {
            sb.append(spotList[0]);
        }
        for (int i = 1; i < spotList.length; i++) {
            sb.append(", ").append(spotList[i]);
        }
        sb.append(");");
        return sb.toString();
    }
}

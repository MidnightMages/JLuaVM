package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.ir.controlflow.GotoNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class IRFunction extends IRBlock {
    public final LinkedHashMap<String, ArrayList<GotoNode>> needFixup = new LinkedHashMap<>();
    public final String jClassName;

    public IRFunction(String jClassName) {
        this.jClassName = jClassName;
    }

    public String toJavaClass() {
        return null;
    }

    public String[] getInnerFunctions() {
        return null;
    }
}

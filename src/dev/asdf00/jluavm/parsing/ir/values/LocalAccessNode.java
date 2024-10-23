package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.container.VarInfo.SpecificVarInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class LocalAccessNode extends Node {
    public final SpecificVarInfo info;

    public LocalAccessNode(SpecificVarInfo info) {
        this.info = info;
    }

    @Override
    public String generate() {
        var result = info.jName;
        if (info.sitsInBox()) {
            result += ".value";
        }
        return result;
    }
}

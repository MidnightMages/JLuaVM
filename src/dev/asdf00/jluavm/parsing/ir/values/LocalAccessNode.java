package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.container.SpecificVarInfo;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class LocalAccessNode extends Node {
    public final SpecificVarInfo info;

    public LocalAccessNode(SpecificVarInfo info) {
        this.info = info;
    }

    @Override
    public String generate(CompilationState cState) {
        String spot = cState.pushEStack();
        String load;
        if (info.closureIdx() < 0) {
            if (info.baseInfo().sitsInBox()) {
                load = "%s = stackFrame[%d].unbox();".formatted(spot, info.baseInfo().lVarIdx);
            } else {
                load = "%s = stackFrame[%d];".formatted(spot, info.baseInfo().lVarIdx);
            }
        } else {
            if (info.baseInfo().sitsInBox()) {
                load = "%s = closures[%d].unbox();".formatted(spot, info.closureIdx());
            } else {
                load = "%s = closures[%d];".formatted(spot, info.closureIdx());
            }
        }
        return load;
    }
}

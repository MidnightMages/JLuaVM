package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.container.SpecificVarInfo;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

/**
 * This node essentially acts like a {@link LocalAccessNode} but does not unwrap boxes.
 */
public class PossiblyBoxedLocalAccessNode extends Node {
    public final SpecificVarInfo info;

    public PossiblyBoxedLocalAccessNode(SpecificVarInfo info) {
        this.info = info;
    }

    @Override
    public String toString() {
        return info.baseInfo().toString();
    }

    @Override
    public String generate(CompilationState cState) {
        String spot = cState.pushEStack();
        String load;
        if (info.closureIdx() < 0) {
            load = "%s = stackFrame[%d];".formatted(spot, info.baseInfo().lVarIdx);
        } else {
            load = "%s = closures[%d];".formatted(spot, info.closureIdx());
        }
        return load;
    }
}

package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.SymTable;
import dev.asdf00.jluavm.parsing.container.LabelInfo;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class LabelNode extends Node {
    public final LabelInfo info;

    public LabelNode(Position sourcePos, LabelInfo info) {
        super(sourcePos);
        this.info = info;
    }

    @Override
    public String generate(CompilationState cState) {
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        var res = cState.generateEStackCallInfo(0);
        cState.registerLabelPatchResolution(cState.patchForLabel(info), res.resumeLabel());
        return "case %d:".formatted(res.resumeLabel());
    }
}

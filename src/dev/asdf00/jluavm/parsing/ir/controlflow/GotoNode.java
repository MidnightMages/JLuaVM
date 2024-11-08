package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.Node;

public class GotoNode extends Node {
    public final String resumePatchLabel;
    public int scopeExits;
    public int closableCnt;
    public int closePatchCnt;

    public GotoNode(String resumePatchLabel, int scopeExits, int closableCnt, int closePatchCnt) {
        this.resumePatchLabel = resumePatchLabel;
        this.scopeExits = scopeExits;
        this.closableCnt = closableCnt;
        this.closePatchCnt = closePatchCnt;
    }

    @Override
    public String generate(CompilationState cState) {
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        String closings = IRBlock.genClose(cState, closableCnt);
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        String patches = "";
        return """
                %s
                %s
                vm.internalGoto(%d, %s);
                return;
                """.formatted(closings, patches, scopeExits, resumePatchLabel);
    }

    // TODO: generate patches
}

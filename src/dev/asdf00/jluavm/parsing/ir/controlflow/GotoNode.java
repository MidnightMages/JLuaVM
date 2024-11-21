package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.container.LabelInfo;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.Node;

import java.util.ArrayList;

public class GotoNode extends Node {
    public LabelInfo label;
    public int scopeExits;
    public int closableCnt;
    public int closePatchCnt;
    public int firstLocalToDrop;
    public int dropLocalsCnt;

    public GotoNode(Position sourcePos) {
        this(sourcePos, null, 0, 0, 0, 0, 0);
    }

    public GotoNode(Position sourcePos, LabelInfo label, int scopeExits, int closableCnt, int closePatchCnt, int firstLocalToDrop, int dropLocalsCnt) {
        super(sourcePos);
        this.label = label;
        this.scopeExits = scopeExits;
        this.closableCnt = closableCnt;
        this.closePatchCnt = closePatchCnt;
        this.firstLocalToDrop = firstLocalToDrop;
        this.dropLocalsCnt = dropLocalsCnt;
    }

    @Override
    public String generate(CompilationState cState) {
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        String closings = IRBlock.genClose(cState, closableCnt);
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        var patches = new StringBuilder();
        if (closePatchCnt > 0) {
            patches.append("vm.addClosable(LuaObject.nil());");
        } else {
            patches.append("// nothing to patch");
        }
        for (int i = 1; i < closePatchCnt; i++) {
            patches.append(" vm.addClosable(LuaObject.nil());");
        }
        return """
                %s
                %s
                vm.internalGoto(%d, %s);
                if(true) return;""".formatted(closings, patches.toString(), scopeExits, cState.patchForLabel(label));
    }
}

package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class VarArgsTableNode extends Node {
    public final Node varargs;

    public VarArgsTableNode(Position sourcePos, Node varargs) {
        super(sourcePos);
        this.varargs = varargs;
    }

    @Override
    public String generate(CompilationState cState) {
        String prev = varargs.generate(cState);
        String varargsSpot = cState.popEStack();
        String tblSpot = cState.pushEStack();
        return prev + "\n%s = LuaObject.tableFromArray(%s.asArray());".formatted(tblSpot, varargsSpot);
    }
}

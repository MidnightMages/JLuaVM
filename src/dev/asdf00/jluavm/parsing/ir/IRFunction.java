package dev.asdf00.jluavm.parsing.ir;

public class IRFunction extends Node {
    public final Node[] statements;
    public final int localCnt;
    public final int closableCnt;
    public final int maxLocals;
    public final int argCnt;
    public final boolean hasParamsArg;

    public IRFunction(Node[] statements, int localCnt, int closableCnt, int maxLocals, int argCnt, boolean hasParamsArg) {
        this.statements = statements;
        this.localCnt = localCnt;
        this.closableCnt = closableCnt;
        this.maxLocals = maxLocals;
        this.argCnt = argCnt;
        this.hasParamsArg = hasParamsArg;
    }

    @Override
    public String generate(CompilationState cState) {
        int innerFunctionIdx = cState.openFunction(maxLocals, argCnt, hasParamsArg, localCnt);
        var sb = new StringBuilder();
        for (int i = 0; i < statements.length; i++) {
            sb.append(statements[i].generate(cState)).append('\n');
            assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        }
        sb.append(IRBlock.genClose(cState, closableCnt)).append('\n');
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        sb.append("""
                    vm.returnValue();
                    return;
                    """);
        cState.closeFunction(sb.toString());
        return Integer.toString(innerFunctionIdx);
    }
}

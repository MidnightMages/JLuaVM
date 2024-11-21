package dev.asdf00.jluavm.parsing.ir;

public class IRBlock extends Node {
    public final Node[] statements;
    public final Node continueCondition;
    public final boolean breakOnFalse;
    public final int localCnt;
    public final int closableCnt;

    public IRBlock(Node[] statements, int localCnt, int closableCnt) {
        this(statements, null, false, localCnt, closableCnt);
    }

    public IRBlock(Node[] statements, Node continueCondition, boolean breakOnFalse, int localCnt, int closableCnt) {
        assert statements != null;
        this.statements = statements;
        this.continueCondition = continueCondition;
        this.breakOnFalse = breakOnFalse;
        this.localCnt = localCnt;
        this.closableCnt = closableCnt;
    }

    @Override
    public String generate(CompilationState cState) {
        String blockName = cState.openInnerBlock(localCnt);
        if (statements.length < 1) {
            assert closableCnt == 0;
            cState.closeInnerBlock("// empty inner block");
            return blockName;
        }
        var sb = new StringBuilder();
        for (int i = 0; i < statements.length; i++) {
            sb.append(statements[i].generate(cState)).append('\n');
            assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        }
        if (continueCondition != null) {
            // this is a loop, generate conditional exit
            String cBlock = continueCondition.generate(cState);
            String closings = genClose(cState, closableCnt);
            String cSpot = cState.popEStack();
            assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
            sb.append("""
                    %s
                    %s
                    if (%sRTUtils.isTruthy(%s)) {
                        vm.internalContinue();
                        return;
                    } else {
                        vm.internalReturn();
                        return;
                    }""".formatted(cBlock, closings, breakOnFalse ? "" : "!", cSpot));
        } else {
            // this is not a loop, finish with standard internal return
            sb.append(genClose(cState, closableCnt)).append('\n');
            assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
            sb.append("""
                    vm.internalReturn();
                    return;""");
        }
        cState.closeInnerBlock(sb.toString());
        return blockName;
    }

    public static String genClose(CompilationState cState, int cnt) {
        var sb = new StringBuilder();
        for (int i = 1; i < cnt; i++) {
            String close = cState.pushEStack();
            var mtGetCall = cState.generateEStackCallInfo(1);
            String mtval = cState.pushEStack();
            cState.popEStack();
            var mvalCall = cState.generateEStackCallInfo(0);
            cState.popEStack();
            // close next
            sb.append('\n').append("""
                    %s = vm.getNextClosable();
                    %s = getMetaClose(vm, %d, %s);
                    if (%s == null) {
                        %s
                        return;
                    }
                    case %d:
                    if (RTUtils.isTruthy(%s)) {
                        %s
                        if (%s.isFunction()) vm.callExternal(%d, %s.getFunc(), %s);
                        else vm.callInternal(%d, LuaFunction::callWithMeta, %s, %s);
                        return;
                    }
                    case %d:""".formatted(close,
                            mtval, mtGetCall.resumeLabel(), close,
                            close,
                            mtGetCall.saveEStack(),
                            mtGetCall.resumeLabel(),
                            close,
                            mvalCall.saveEStack(),
                            mtval, mvalCall.resumeLabel(), mtval, close,
                            mvalCall.resumeLabel(), mtval, close,
                            mvalCall.resumeLabel()));
        }
        return sb.isEmpty() ? "// nothing to close" : sb.toString();
    }
}

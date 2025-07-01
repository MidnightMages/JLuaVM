package dev.asdf00.jluavm.parsing.ir;

import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.container.Position;

public class IRBlock extends Node {
    public final Node[] statements;
    public final Node continueCondition;
    public final boolean breakOnFalse;
    public final int localCnt;
    public final int closableCnt;

    public IRBlock(Position sourcePos, Node[] statements, int localCnt, int closableCnt) {
        this(sourcePos, statements, null, false, localCnt, closableCnt);
    }

    public IRBlock(Position sourcePos, Node[] statements, Node continueCondition, boolean breakOnFalse, int localCnt, int closableCnt) {
        super(sourcePos);
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
        if (statements.length < 1 && continueCondition == null) {
            assert closableCnt == 0;
            cState.closeInnerBlock("""
                    vm.internalReturn(); // empty inner block
                    return;
                    """);
            return blockName;
        }
        var sb = new StringBuilder();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < statements.length; i++) {
            sb.append(statements[i].generate(cState)).append('\n');
            assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
            if (CompilationState.DEBUG_MODE) {
                sb.append("debugPoint(\"at lua l%d:c%d\", _ENV, stackFrame, vm);\n".formatted(statements[i].sourcePos.line(), statements[i].sourcePos.col()));
            }
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
        for (int i = 0; i < cnt; i++) {
            String close = cState.pushEStack();
            cState.popEStack();
            var info = cState.generateEStackCallInfo(0);
            // close next
            sb.append('\n').append("""
                    %s = vm.getNextClosable();
                    if (RTUtils.isTruthy(%s)) {
                        var mval = %s.getMetaValueOrNil(Singletons.__close);
                        %s
                        if (mval.isFunction()) vm.callExternal(%d, mval.getFunc(), %s);
                        else vm.callInternal(%d, LuaFunction::callWithMeta, "::callWithMeta", mval, %s);
                        return;
                    }
                    case %d:""".formatted(
                    close,
                    close,
                    close,
                    info.saveEStack(),
                    info.resumeLabel(), close,
                    info.resumeLabel(), close,
                    info.resumeLabel()));
        }
        return sb.isEmpty() ? "// nothing to close" : sb.toString();
    }
}

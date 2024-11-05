package dev.asdf00.jluavm.parsing.ir;

public class IRBlock extends Node {
    public final Node[] statements;
    public final Node continueCondition;
    public final boolean breakOnFalse;
    public final int closableCnt;

    public IRBlock(Node[] statements, int closableCnt) {
        this(statements, null, false, closableCnt);
    }

    public IRBlock(Node[] statements, Node continueCondition, boolean breakOnFalse, int closableCnt) {
        this.statements = statements;
        this.continueCondition = continueCondition;
        this.breakOnFalse = breakOnFalse;
        this.closableCnt = closableCnt;
    }

    @Override
    public String generate(CompilationState cState) {
        String blockName = cState.openInnerBlock(continueCondition != null);
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
                        break resumeSwitch;
                    } else {
                        vm.internalReturn();
                        return;
                    }
                    """.formatted(cBlock, closings, breakOnFalse ? "" : "!", cSpot));
        } else {
            // this is not a loop, finish with standard internal return
            sb.append(genClose(cState, closableCnt)).append('\n');
            assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
            sb.append("""
                    vm.internalReturn();
                    return;
                    """);
        }
        cState.closeInnerBlock(sb.toString());
        return blockName;
    }

    public static String genClose(CompilationState cState, int cnt) {
        var sb = new StringBuilder();
        for (int i = 1; i < cnt; i++) {
            String close = cState.pushEStack();
            String mtbl = cState.pushEStack();
            cState.popEStack();
            var mtGetCall = cState.generateEStackCallInfo(1);
            String mtval = cState.pushEStack();
            cState.popEStack();
            var mvalCall = cState.generateEStackCallInfo(0);
            cState.popEStack();
            // close next variable
            sb.append('\n').append("""
                    %s = vm.getNextClosable();
                    case %d:
                    if (RTUtils.isTruthy(%s)) {
                        if (resume != %d) {
                            %s = %s.getMetaTable();
                            if (%s == null) {
                                vm.error(new LuaMetaTableError());
                                return;
                            } else if (%s.isTable()) {
                                LuaObject table = %s;
                                LuaObject key = Singletons.__close;
                                if (table.hasKey(key)) {
                                    %s = table.get(key);
                                } else {
                                    LuaObject mtbl = table.getMetaTable();
                                    if (mtbl == null) {
                                        %s = LuaObject.nil();
                                    } else {
                                        %s
                                        vm.callInternal(%d, LuaFunction::getWithMeta, table, key, mtbl);
                                        return;
                                    }
                                }
                            } else if (%s.isUserData()) {
                                try {
                                    %s = %s.get(Singletons.__close);
                                } catch (LuaRuntimeError ex) {
                                    vm.error(new LuaForeignCallError());
                                    return;
                                }
                            } else {
                                vm.error(new LuaTypeError());
                                return;
                            }
                        }
                        %s
                        if (%s.isFunction()) {
                            vm.callExternal(%d, %s.getFunc(), %s);
                            return;
                        } else {
                            vm.callInternal(%d, LuaFunction::callWithMeta, %s, %s);
                            return;
                        }
                    }
                    case %d:
                    """.formatted(close,
                    mtGetCall.resumeLabel(),
                    close,
                    mtGetCall.resumeLabel(),
                    mtbl, close,
                    mtbl,
                    mtbl,
                    mtbl,
                    mtval,
                    mtval,
                    mtGetCall.saveEStack(),
                    mtGetCall.resumeLabel(),
                    mtbl,
                    mtval, mtbl,
                    mvalCall.saveEStack(),
                    mtval,
                    mvalCall.resumeLabel(), mtval, close,
                    mvalCall.resumeLabel(), mtval, close,
                    mvalCall.resumeLabel()));
        }
        return sb.isEmpty() ? "// nothing to close" : sb.toString();
    }
}

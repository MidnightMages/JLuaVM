package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class DoEndNode extends Node {
    public final Node[] block;
    public final int toClose;

    public DoEndNode(Node[] block, int toClose) {
        this.block = block;
        this.toClose = toClose;
    }

    @Override
    public String generate(CompilationState cState) {
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        if (block.length < 1) {
            return "// empty do-end block";
        }
        var sb = new StringBuilder();
        sb.append(block[0].generate(cState));
        assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        for (int i = 1; i < block.length; i++) {
            sb.append('\n').append(block[i].generate(cState));
            assert cState.clearEStack() == 0 : "we expect the expression stack to be empty here";
        }
        for (int i = 1; i < toClose; i++) {
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
        return null;
    }
}

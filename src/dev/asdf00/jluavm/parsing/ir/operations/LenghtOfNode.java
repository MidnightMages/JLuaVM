package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class LenghtOfNode extends Node {
    public final Node value;

    public LenghtOfNode(Position sourcePos, Node value) {
        super(sourcePos);
        this.value = value;
    }

    @Override
    public String generate(CompilationState cState) {
        String result = value.generate(cState) + "\n";
        String vSpot = cState.peekEStack();
        EStackCallInfo callGetMetaValue = cState.generateEStackCallInfo(1);
        EStackCallInfo callFinalCall = cState.generateEStackCallInfo(1);
        String mtSpot = cState.pushEStack();
        cState.popEStack();
        cState.popEStack();
        String rSpot = cState.pushEStack();
        // TODO: minify
        String block = """
                case %d:
                if (%s.isString()) {
                    %s = LuaObject.of(%s.getString().length());
                } else {
                    if (resume != %d) {
                        %s = %s.getMetaTable();
                        if (%s != null) {
                            LuaObject table = %s;
                            if (table.isTable()) {
                                LuaObject key = Singletons.__len;
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
                            } else if (table.isUserData()) {
                                try {
                                    %s = table.get(Singletons.__len);
                                } catch (LuaRuntimeError ex) {
                                    vm.error(new LuaForeignCallError());
                                    return;
                                }
                            } else {
                                vm.error(new LuaTypeError());
                                return;
                            }
                        } else {
                            %s = LuaObject.nil();
                        }
                    }
                    if (%s.isNil() && %s.isTable()) {
                        %s = %s.len();
                    } else {
                        %s
                        if (%s.isFunction()) {
                            vm.callExternal(%d, %s.getFunc(), %s);
                            return;
                        } else {
                            vm.callInternal(%d, LuaFunction::callWithMeta, %s, %s);
                            return;
                        }
                    }
                }
                case %d:""".formatted(callGetMetaValue.resumeLabel(),
                vSpot,
                rSpot, vSpot,
                callGetMetaValue.resumeLabel(),
                mtSpot, vSpot,
                mtSpot,
                mtSpot,
                mtSpot,
                mtSpot,
                callGetMetaValue.saveEStack(),
                callGetMetaValue.resumeLabel(),
                mtSpot,
                mtSpot,
                mtSpot, vSpot,
                rSpot, vSpot,
                callFinalCall.saveEStack(),
                mtSpot,
                callFinalCall.resumeLabel(), mtSpot, vSpot,
                callFinalCall.resumeLabel(), mtSpot, vSpot,
                callFinalCall.resumeLabel());
        return result + block;
    }
}

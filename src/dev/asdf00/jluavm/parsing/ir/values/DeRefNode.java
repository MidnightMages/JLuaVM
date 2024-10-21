package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class DeRefNode extends Node {
    public final Node value;
    public final Node idx;

    public DeRefNode(Node value, Node idx) {
        this.value = value;
        this.idx = idx;
    }

    @Override
    public String generate(CompilationState cState) {
        String prev = value.generate(cState) + "\n";
        prev = idx.generate(cState) + "\n";

        String i = cState.popEStack();
        String v = cState.popEStack();
        EStackCallInfo callInfo = cState.generateEStackCallInfo(1);
        String r = cState.pushEStack();

        String result = """
        if (%s.isTable()) {
            LuaObject table = %s;
            LuaObject key = RTUtils.tryCoerceFloatToInt(%s);
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
                %s = %s.get(%s);
            } catch (LuaRuntimeError ex) {
                vm.error(new LuaForeignCallError());
                return;
            }
        } else {
            vm.error(new LuaTypeError());
            return;
        }
        case %d:
        """.formatted(v, v, i, r, r, callInfo.saveEStack(), callInfo.resumeLabel(), v, r, v, i, callInfo.resumeLabel());
        return result;
    }
}

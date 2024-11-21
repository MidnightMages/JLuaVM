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
        prev += idx.generate(cState) + "\n";
        return prev + dereference(cState);
    }

    public static String dereference(CompilationState cState) {
        String i = cState.popEStack();
        String v = cState.popEStack();
        EStackCallInfo callInfo = cState.generateEStackCallInfo(1);
        String r = cState.pushEStack();

        String result = """
                %s = indexedGet(vm, %d, %s, %s);
                if (%s == null) {
                    %s
                    return;
                }
                case %d:""".formatted(r, callInfo.resumeLabel(), v, i, r, callInfo.saveEStack(), callInfo.resumeLabel());
        return result;
    }
}

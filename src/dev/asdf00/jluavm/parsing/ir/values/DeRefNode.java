package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class DeRefNode extends Node {
    public final Node value;
    public final Node idx;

    public DeRefNode(Position sourcePos, Node value, Node idx) {
        super(sourcePos);
        this.value = value;
        this.idx = idx;
    }

    @Override
    public String generate(CompilationState cState) {
        String prev = value.generate(cState) + "\n";
        prev += idx.generate(cState) + "\n";
        return prev + dereference(cState);
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
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

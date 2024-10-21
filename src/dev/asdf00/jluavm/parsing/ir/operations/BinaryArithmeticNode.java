package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class BinaryArithmeticNode extends Node {
    public final String op;
    public final Node x;
    public final Node y;

    public BinaryArithmeticNode(String op, Node x, Node y) {
        this.op = op;
        this.x = x;
        this.y = y;
    }

    @Override
    public String generate(CompilationState cState) {
        String prev = x.generate(cState) + "\n";
        prev += y.generate(cState) + "\n";

        String sy = cState.popEStack();
        String sx = cState.popEStack();
        EStackCallInfo callInfo = cState.generateEStackCallInfo(1);
        String r = cState.pushEStack();

        String result = """
        if (%s.isArithmetic() && %s.isArithmetic()) {
            %s = %s.%s(%s);
        } else {
            %s
            vm.callInternal(%d, LuaFunction::binaryArithmeticOpWithMeta, Singletons.__%s, %s, %s);
            return;
        }
        case %d:
        """.formatted(sx, sy,
                sx, sx, op, sy,
                callInfo.saveEStack(),
                callInfo.resumeLabel(), op, sx, sy,
                callInfo.resumeLabel());
        return prev + result;
    }
}

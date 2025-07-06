package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class RelationalOpNode extends Node {
    public final String op;
    public final boolean invertOperands;
    public final Node x;
    public final Node y;

    public RelationalOpNode(Position sourcePos, String op, boolean invertOperands, Node x, Node y) {
        super(sourcePos);
        this.op = op;
        this.invertOperands = invertOperands;
        this.x = x;
        this.y = y;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    public String generate(CompilationState cState) {
        var sb = new StringBuilder();
        sb.append(x.generate(cState)).append('\n');
        sb.append(y.generate(cState)).append('\n');
        String ySpot = cState.popEStack();
        String xSpot = cState.popEStack();
        EStackCallInfo callInfo = cState.generateEStackCallInfo(1);
        String rSpot = cState.pushEStack();
        if (invertOperands) {
            // swap operands
            String tmpSwap = xSpot;
            xSpot = ySpot;
            ySpot = tmpSwap;
        }
        String block = """
                if (%s.isNumber() && %s.isNumber() || %s.isString() && %s.isString()) {
                    %s = %s.%s(%s);
                } else {
                    %s
                    vm.callInternal(%d, LuaFunction::binaryOpWithMeta, "::binaryOpWithMeta", Singletons.__%s, %s, %s);
                    return;
                }
                case %d:
                if (resume == %d && !%s.isBoolean()) {
                    %s = LuaObject.of(RTUtils.isTruthy(%s));
                }""".formatted(xSpot, ySpot, xSpot, ySpot,
                rSpot, xSpot, op, ySpot,
                callInfo.saveEStack(),
                callInfo.resumeLabel(), op, xSpot, ySpot,
                callInfo.resumeLabel(),
                callInfo.resumeLabel(), rSpot,
                rSpot, rSpot);
        sb.append(block);
        return sb.toString();
    }
}

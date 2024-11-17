package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class EqualsNode extends Node {
    public final Node x;
    public final Node y;

    public EqualsNode(Node x, Node y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String generate(CompilationState cState) {
        var sb = new StringBuilder();
        sb.append(x.generate(cState)).append('\n');
        sb.append(y.generate(cState)).append('\n');
        String ySpot = cState.popEStack();
        String xSpot = cState.popEStack();
        CompilationState.EStackCallInfo callInfo = cState.generateEStackCallInfo(1);
        String rSpot = cState.pushEStack();
        // here we rely on NIL, TRUE and FALSE being singletons and only do a reference compare in those cases.
        // for numbers and strings we do a deep equals, for tables and userdata we attempt a meta table lookup if the
        // reference compare fails
        String block = """
                if (%s.isNumber() && %s.isNumber() || %s.isString() && %s.isString()) {
                    %s = %s.eq(%s);
                } else if (%s.getType() == %s.getType()) {
                    if (%s == %s) {
                        %s = LuaObject.TRUE;
                    } else if (%s.isTable() || %s.isUserData()) {
                        %s
                        vm.callInternal(%d, LuaFunction::binaryOpWithMeta, Singletons.__eq, %s, %s);
                        return;
                    } else {
                        %s = LuaObject.FALSE;
                    }
                } else {
                    %s = LuaObject.FALSE;
                }
                case %d:
                if (resume == %d && !%s.isBoolean()) {
                    %s = LuaObject.of(RTUtils.isTruthy(%s));
                }""".formatted(xSpot, ySpot, xSpot, ySpot,
                rSpot, xSpot, ySpot,
                xSpot, ySpot,
                xSpot, ySpot,
                rSpot,
                xSpot, xSpot,
                callInfo.saveEStack(),
                callInfo.resumeLabel(), xSpot, ySpot,
                rSpot,
                rSpot,
                callInfo.resumeLabel(),
                callInfo.resumeLabel(), rSpot,
                rSpot, rSpot);
        sb.append(block);
        return sb.toString();
    }
}

package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class BinaryOpNode extends Node {
    protected final String typeRestriction;
    public final String op;
    public final Node x;
    public final Node y;

    protected BinaryOpNode(String typeRestriction, String op, Node x, Node y) {
        this.typeRestriction = typeRestriction;
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
                if (%s.%s() && %s.%s()) {
                    %s = %s.%s(%s);
                } else {
                    %s
                    vm.callInternal(%d, LuaFunction::binaryOpWithMeta, Singletons.__%s, %s, %s);
                    return;
                }
                case %d:
                """.formatted(sx, typeRestriction, sy, typeRestriction,
                sx, sx, op, sy,
                callInfo.saveEStack(),
                callInfo.resumeLabel(), op, sx, sy,
                callInfo.resumeLabel());
        return prev + result;
    }

    public static BinaryOpNode arithmetic(String op, Node x, Node y) {
        return new BinaryOpNode("isArithmetic", op, x, y);
    }

    public static BinaryOpNode bitwise(String op, Node x, Node y) {
        return new BinaryOpNode("isIntCoercible", op, x, y);
    }
}

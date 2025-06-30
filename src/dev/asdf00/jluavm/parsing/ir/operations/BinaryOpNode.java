package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class BinaryOpNode extends Node {
    protected final String typeRestriction;
    public final String op;
    public final Node x;
    public final Node y;

    protected BinaryOpNode(Position sourcePos, String typeRestriction, String op, Node x, Node y) {
        super(sourcePos);
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

        var idivPrefix = """
            if (%s.isLong() && %s.isLong() && %s.lVal == 0) {
                vm.error(LuaObject.of("attempt to divide by zero"));
                return;
            } else\s
            """.formatted(sx, sy, sy);
        String result = ("idiv".equals(op) ? idivPrefix : "") +
            """
            if (%s.%s() && %s.%s()) {
                %s = %s.%s(%s);
            } else {
                %s
                vm.callInternal(%d, LuaFunction::binaryOpWithMeta, "::binaryOpWithMeta", Singletons.__%s, %s, %s);
                return;
            }
            case %d:""".formatted(sx, typeRestriction, sy, typeRestriction,
            sx, sx, op, sy,
            callInfo.saveEStack(),
            callInfo.resumeLabel(), op, sx, sy,
            callInfo.resumeLabel());
        if ("concat".equals(op)) {
            result = "debugPoint(%s, %s);\n".formatted(sx, sy) + result;
        }
        return prev + result;
    }

    public static BinaryOpNode arithmetic(Position pos, String op, Node x, Node y) {
        return new BinaryOpNode(pos, "isNumberCoercible", op, x, y);
    }

    public static BinaryOpNode stringConcat(Position pos, Node x, Node y) {
        return new BinaryOpNode(pos, "isArithmetic", "concat", x, y);
    }

    public static BinaryOpNode bitwise(Position pos, String op, Node x, Node y) {
        return new BinaryOpNode(pos, "isIntCoercible", op, x, y);
    }
}

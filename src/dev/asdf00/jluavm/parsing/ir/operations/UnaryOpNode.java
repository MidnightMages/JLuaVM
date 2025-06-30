package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class UnaryOpNode extends Node {
    protected final String typeRestriction;
    public final String op;
    public final Node value;

    protected UnaryOpNode(Position sourcePos, String typeRestriction, String op, Node value) {
        super(sourcePos);
        this.typeRestriction = typeRestriction;
        this.op = op;
        this.value = value;
    }

    @Override
    public String generate(CompilationState cState) {
        String prev = value.generate(cState) + "\n";
        String vSpot = cState.popEStack();
        EStackCallInfo info = cState.generateEStackCallInfo(1);
        String rSpot = cState.pushEStack();
        String block = """
                if (%s.%s()) {
                    %s = %s.%s();
                } else {
                    %s
                    vm.callInternal(%d, LuaFunction::unaryOpWithMeta, "::unaryOpWithMeta", Singletons.__%s, %s);
                    return;
                }
                case %d:""".formatted(vSpot, typeRestriction, rSpot, vSpot, op, info.saveEStack(), info.resumeLabel(), op, vSpot, info.resumeLabel());
        return prev + block;
    }

    public static UnaryOpNode negate(Position pos, Node value) {
        return new UnaryOpNode(pos, "isArithmetic", "unm", value);
    }

    public static UnaryOpNode invert(Position pos, Node value) {
        return new UnaryOpNode(pos, "isIntCoercible", "bnot", value);
    }
}

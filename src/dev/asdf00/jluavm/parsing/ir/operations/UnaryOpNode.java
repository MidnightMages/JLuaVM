package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;

public class UnaryOpNode extends Node {
    protected final String typeRestriction;
    public final String op;
    public final Node value;

    protected UnaryOpNode(String typeRestriction, String op, Node value) {
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
                    vm.callInternal(%d, LuaFunction::unaryOpWithMeta, Singletons.__%s, %s);
                    return;
                }
                case %d:""".formatted(vSpot, typeRestriction, rSpot, vSpot, op, info.saveEStack(), info.resumeLabel(), op, vSpot, info.resumeLabel());
        return prev + block;
    }

    public static UnaryOpNode negate(Node value) {
        return new UnaryOpNode("isArithmetic", "unm", value);
    }

    public static UnaryOpNode invert(Node value) {
        return new UnaryOpNode("isIntCoercible", "bnot", value);
    }
}

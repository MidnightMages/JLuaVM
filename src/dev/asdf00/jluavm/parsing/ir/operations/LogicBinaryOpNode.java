package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class LogicBinaryOpNode extends Node {
    boolean isOr;
    public final Node x;
    public final Node y;

    public LogicBinaryOpNode(boolean isOr, Node x, Node y) {
        this.isOr = isOr;
        this.x = x;
        this.y = y;
    }

    @Override
    public String generate(CompilationState cState) {
        String result = x.generate(cState);
        String xSpot = cState.peekEStack();
        /**
         * Here we employ a little bit of trickery to avoid having to do an internal call for the 'if' body:
         * All internal resume points are pulled to before the if condition. Then, inside the if condition the
         * expression stack spot of the (non-)truthy variable is set to always trigger the if condition  on resume.
         * Inside the 'if', a second switch-case is placed that again jumps according to the value of the resume.
         */
        int beforeMaxResume = cState.getCurResumeLabel();
        var sb = new StringBuilder();
        sb.append("\nif (");
        if (isOr) {
            sb.append('!');
        }
        sb.append("RTUtils.isTruthy(").append(xSpot).append(")) {\n");
        // overwrite value of x with condition trigger to be able to resume into the scope if this java 'if'
        sb.append(xSpot).append(" = LuaObject.").append(isOr ? "FALSE" : "TRUE").append(";\nswitch (resume) {\ncase -1");
        for (int i = 0; i <= beforeMaxResume; i++) {
            sb.append(", ").append(i);
        }
        sb.append(":\n");
        String yGeneration = y.generate(cState);
        String ySpot = cState.popEStack();
        cState.popEStack(); // pop xSpot
        String rSpot = cState.pushEStack();
        assert xSpot.equals(rSpot) : "expression stack mismatch (%s vs %s)".formatted(xSpot, rSpot);
        // set result to
        sb.append("}\n").append(rSpot).append(" = ").append(ySpot).append(";\n}");

        String ifBlock = sb.toString();
        int afterMaxResume = cState.getCurResumeLabel();
        if (afterMaxResume > beforeMaxResume) {
            // generate outer jump label before if
            var isb = new StringBuilder();
            isb.append("\ncase ").append(beforeMaxResume + 1);
            for (int i = beforeMaxResume + 2; i <= afterMaxResume; i++) {
                isb.append(", ").append(i);
            }
            isb.append(':');
            result += isb.toString();
        }
        result += ifBlock;

        return result;
    }
}

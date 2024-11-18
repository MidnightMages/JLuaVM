package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.SpecificVarInfo;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class CoerceNumericForNode extends Node {
    public final SpecificVarInfo iteratorVar;
    public final SpecificVarInfo upperBoundVar;
    public final SpecificVarInfo stepVar;

    public CoerceNumericForNode(SpecificVarInfo iteratorVar, SpecificVarInfo upperBoundVar, SpecificVarInfo stepVar) {
        this.iteratorVar = iteratorVar;
        this.upperBoundVar = upperBoundVar;
        this.stepVar = stepVar;
    }

    @Override
    public String generate(CompilationState cState) {
        String itr = getLocalExpression(iteratorVar);
        String ub = getLocalExpression(upperBoundVar);
        String sv = getLocalExpression(stepVar);
        return """
                if (!%s.isNumberCoercible() || !%s.isNumberCoercible() || !%s.isNumberCoercible()) {
                    vm.error(new LuaTypeError());
                    return;
                } else if (!%s.isLong() || !%s.isLong() || !%s.isLong()) {
                    %s
                    %s
                    %s
                }""".formatted(sv, ub, itr,
                sv, ub, itr,
                setLocalStatement(stepVar, "LuaObject.of(%s.asDouble())".formatted(sv)),
                setLocalStatement(upperBoundVar, "LuaObject.of(%s.asDouble())".formatted(ub)),
                setLocalStatement(iteratorVar, "LuaObject.of(%s.asDouble())".formatted(itr)));
    }

    public static String getLocalExpression(SpecificVarInfo info) {
        String load;
        if (info.closureIdx() < 0) {
            if (info.baseInfo().sitsInBox()) {
                load = "stackFrame[%d].unbox()".formatted(info.baseInfo().lVarIdx);
            } else {
                load = "stackFrame[%d]".formatted(info.baseInfo().lVarIdx);
            }
        } else {
            if (info.baseInfo().sitsInBox()) {
                load = "closures[%d].unbox()".formatted(info.closureIdx());
            } else {
                load = "closures[%d]".formatted(info.closureIdx());
            }
        }
        return load;
    }

    public static String setLocalStatement(SpecificVarInfo info, String exp) {
        String load;
        if (info.closureIdx() < 0) {
            if (info.baseInfo().sitsInBox()) {
                load = "stackFrame[%d].setBox(%s);".formatted(info.baseInfo().lVarIdx, exp);
            } else {
                load = "stackFrame[%d] = %s;".formatted(info.baseInfo().lVarIdx, exp);
            }
        } else {
            if (info.baseInfo().sitsInBox()) {
                load = "closures[%d].setBox(%s);".formatted(info.closureIdx(), exp);
            } else {
                load = "closures[%d] = %s;".formatted(info.closureIdx(), exp);
            }
        }
        return load;
    }
}

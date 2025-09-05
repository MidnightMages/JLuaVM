package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.container.SpecificVarInfo;
import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

public class CoerceNumericForNode extends Node {
    public final SpecificVarInfo iteratorVar;
    public final SpecificVarInfo upperBoundVar;
    public final SpecificVarInfo stepVar;

    public CoerceNumericForNode(Position sourcePos, SpecificVarInfo iteratorVar, SpecificVarInfo upperBoundVar, SpecificVarInfo stepVar) {
        super(sourcePos);
        this.iteratorVar = iteratorVar;
        this.upperBoundVar = upperBoundVar;
        this.stepVar = stepVar;
    }

    @Override
    public String generate(CompilationState cState) {
        String itr = getLocalExpression(iteratorVar);
        String ub = getLocalExpression(upperBoundVar);
        String sv = getLocalExpression(stepVar);
        var call = cState.generateEStackCallInfo(1);
        String checkSpot = cState.pushEStack();
        cState.popEStack();
        return """
                if (numericForCheck(vm, %s, %s, %s)) {
                    return;
                } else if (!%s.isLong() || !%s.isLong() || !%s.isLong()) {
                    %s
                    %s
                    %s
                }
                %s = areEqual(%d, vm, %d, %s, LuaObject.of(0));
                if (%s == null) {
                    %s
                    return;
                }
                case %d:
                if (RTUtils.isTruthy(%s)) {
                    vm.error(LuaObject.of("'for' step is zero"));
                    return;
                }""".formatted(sv, ub, itr,
                sv, ub, itr,
                setLocalStatement(stepVar, "LuaObject.of(%s.asDouble())".formatted(sv)),
                setLocalStatement(upperBoundVar, "LuaObject.of(%s.asDouble())".formatted(ub)),
                setLocalStatement(iteratorVar, "LuaObject.of(%s.asDouble())".formatted(itr)),
                checkSpot, sourcePos.line(), call.resumeLabel(), sv,
                checkSpot,
                call.saveEStack(),
                call.resumeLabel(),
                checkSpot);
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

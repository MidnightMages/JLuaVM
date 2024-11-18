package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.SpecificVarInfo;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRBlock;
import dev.asdf00.jluavm.parsing.ir.Node;

public class StepForNode extends Node {
    public final SpecificVarInfo iteratorVar;
    public final SpecificVarInfo stepVar;
    public final int closableCnt;

    public StepForNode(SpecificVarInfo iteratorVar, SpecificVarInfo stepVar, int closableCnt) {
        this.iteratorVar = iteratorVar;
        this.stepVar = stepVar;
        this.closableCnt = closableCnt;
    }

    @Override
    public String generate(CompilationState cState) {
        String itr = CoerceNumericForNode.getLocalExpression(iteratorVar);
        String sv = CoerceNumericForNode.getLocalExpression(stepVar);
        String setLong = CoerceNumericForNode.setLocalStatement(iteratorVar, "LuaObject.of(%s.asLong() + %s.asLong())".formatted(itr, sv));
        String setDouble = CoerceNumericForNode.setLocalStatement(iteratorVar, "LuaObject.of(%s.asDouble() + %s.asDouble())".formatted(itr, sv));
        if (closableCnt > 0) {
            String closings;
            int lower = cState.getCurResumeLabel();
            closings = IRBlock.genClose(cState, closableCnt);
            int upper = cState.getCurResumeLabel();
            var sb = new StringBuilder();
            sb.append("case ").append(lower);
            for (int i = lower + 1; i <= upper; i++) {
                sb.append(", ").append(i);
            }
            sb.append(":\n").append("""
                    if (%s.isLong() && %s.isLong()) {
                        boolean overflow = resume >= %d && resume <= %d;
                        if (!overflow) {
                            overflow = %s.asLong() > 0 ? Long.MAX_VALUE - %s.asLong() < %s.asLong() : Long.MIN_VALUE - %s.asLong() > %s.asLong();
                        }
                        if (overflow) {
                            switch (resume) {
                            default:
                            %s
                            }
                            vm.internalReturn();
                            return;
                        }
                        %s
                    } else if (%s.isDouble() && %s.isDouble()) {
                        %s
                    } else {
                        throw new InternalLuaRuntimeError("incompatible for-step types '%%s' and '%%s'".formatted(%s.getType(), %s.getType()));
                    }""".formatted(itr, sv,
                    lower, upper,
                    sv, sv, itr, sv, itr,
                    closings,
                    setLong,
                    itr, sv,
                    setDouble,
                    itr, sv));
            return sb.toString();
        } else {
            return """
                    if (%s.isLong() && %s.isLong()) {
                        if (%s.asLong() > 0 ? Long.MAX_VALUE - %s.asLong() < %s.asLong() : Long.MIN_VALUE - %s.asLong() > %s.asLong()) {
                            vm.internalReturn();
                            return;
                        }
                        %s
                    } else if (%s.isDouble() && %s.isDouble()) {
                        %s
                    } else {
                        throw new InternalLuaRuntimeError("incompatible for-step types '%%s' and '%%s'".formatted(%s.getType(), %s.getType()));
                    }""".formatted(itr, sv,
                    sv, sv, itr, sv, itr,
                    setLong,
                    itr, sv,
                    setDouble,
                    itr, sv);
        }
    }
}

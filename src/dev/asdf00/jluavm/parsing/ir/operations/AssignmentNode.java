package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;
import dev.asdf00.jluavm.parsing.container.SpecificVarInfo;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.controlflow.FunctionCallNode;
import dev.asdf00.jluavm.parsing.ir.values.DeRefNode;
import dev.asdf00.jluavm.parsing.ir.values.LocalAccessNode;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.ArrayList;

public class AssignmentNode extends Node {
    public final Node[] targets;
    public final Node[] values;

    public AssignmentNode(Node[] targets, Node[] values) {
        this.targets = targets;
        this.values = values;
    }

    @Override
    public String generate(CompilationState cState) {
        var sb = new StringBuilder();
        var tTars = new Object[targets.length];
        // generate assignment values in left-to-right order
        for (int i = 0; i < tTars.length; i++) {
            if (targets[i] instanceof LocalAccessNode access) {
                tTars[i] = access.info;
            } else if (targets[i] instanceof DeRefNode deRef) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(deRef.value.generate(cState));
                String vSpot = cState.peekEStack();
                sb.append('\n').append(deRef.idx.generate(cState));
                String iSpot = cState.peekEStack();
                tTars[i] = new Tuple<>(vSpot, iSpot);
            } else {
                throw new InternalLuaLoadingError("what is %s in assignment?".formatted(targets[i].getClass().getName()));
            }
        }

        var vSpots = new ArrayList<String>();
        for (int i = 0; i < values.length - 1; i++) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            // generate target values
            sb.append(targets[i].generate(cState));
            vSpots.add(cState.peekEStack());
        }
        if (values.length > 0) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            // last value needs special care
            var lv = values[values.length - 1];
            if (lv instanceof FunctionCallNode call) {
                // unroll max(0, targets.length - values.length) return values
                int aCnt = Math.max(0, targets.length - values.length);
                call.expectedResultCnt = aCnt;
                sb.append(call.generate(cState));
                vSpots.addAll(cState.peekEStack(aCnt));
            } else {
                sb.append(lv.generate(cState));
                vSpots.add(cState.peekEStack());
            }
        }

        // fill open spots with nil
        for (int i = vSpots.size(); i < targets.length; i++) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            String spot = cState.pushEStack();
            sb.append(spot).append(" = LuaObject.nil();");
            vSpots.add(spot);
        }

        // we must have at least as many values as we have targets to assign at this point
        assert vSpots.size() >= targets.length;

        // tracks how many expression stack positions this assignment takes so that we check that later;
        int usageOfStack = vSpots.size();

        // perform assignments in right-to-left order
        for (int i = tTars.length - 1; i >= 0; i --) {
            sb.append('\n');
            if (tTars[i] instanceof SpecificVarInfo info) {
                // local assignment
                sb.append(genLocalSet(info, vSpots.get(i)));
            } else {
                var spots = (Tuple<String, String>) tTars[i];
                sb.append(genIndexedSet(cState, spots.x(), spots.y(), vSpots.get(i)));
                usageOfStack += 2;
            }
        }

        int cleared = cState.clearEStack();

        // check that we used as many stack spaces as expected;
        assert usageOfStack == cleared;

        return sb.toString();
    }

    private static String genIndexedSet(CompilationState cState, String obj, String idx, String val) {
        EStackCallInfo sInfo = cState.generateEStackCallInfo(0);
        String assignment = """
        if (%s.isTable()) {
            LuaObject table = %s;
            LuaObject key = RTUtils.tryCoerceFloatToInt(%s);
            if (key.isNil() || key.isNaN()) {
                vm.error(new LuaArgumentError());
                return;
            }
            if (table.hasKey(key)) {
                table.set(key, %s);
            } else {
                LuaObject mtbl = table.getMetaTable();
                if (mtbl == null) {
                    table.set(key, %s);
                } else {
                    %s
                    vm.callInternal(%d, LuaFunction::setWithMeta, table, key, %s, mtbl);
                    return;
                }
            }
        } else if (%s.isUserData()) {
            try {
                %s.set(%s, %s);
            } catch (LuaRuntimeError ex) {
                vm.error(new LuaForeignCallError());
                return;
            }
        } else {
            vm.error(new LuaTypeError());
            return;
        }
        case %d:
        """.formatted(obj, obj, idx, val, val, sInfo.saveEStack(), sInfo.resumeLabel(), val, obj, obj, idx, val, sInfo.resumeLabel());
        return assignment;
    }

    private static String genLocalSet(SpecificVarInfo info, String val) {
        String assignment;
        if (info.closureIdx() < 0) {
            if (info.baseInfo().sitsInBox()) {
                assignment = "stackFrame[%d].setBox(%s);".formatted(info.baseInfo().lVarIdx, val);
            } else {
                assignment = "stackFrame[%d] = %s;".formatted(info.baseInfo().lVarIdx, val);
            }
        } else {
            if (info.baseInfo().sitsInBox()) {
                assignment = "closures[%d].setBox(%s);".formatted(info.closureIdx(), val);
            } else {
                throw new InternalLuaLoadingError("setting variable %s in closure without box???".formatted(info.baseInfo()));
            }
        }
        return assignment;
    }
}

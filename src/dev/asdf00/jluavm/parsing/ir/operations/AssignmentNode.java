package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.exceptions.loading.InternalLuaSemanticError;
import dev.asdf00.jluavm.parsing.container.VarInfo;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.CompilationState.EStackCallInfo;
import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.controlflow.FunctionCallNode;
import dev.asdf00.jluavm.parsing.ir.values.DeRefNode;
import dev.asdf00.jluavm.parsing.ir.values.LocalAccessNode;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.ArrayList;
import java.util.function.Supplier;

public class AssignmentNode extends Node {
    private final Supplier<String> tempVarNameGen;
    public final Node[] targets;
    public final Node[] values;

    public AssignmentNode(Supplier<String> tempVarNameGen, Node[] targets, Node[] values) {
        this.tempVarNameGen = tempVarNameGen;
        this.targets = targets;
        this.values = values;
    }

    @Override
    public String generate(CompilationState cState) {
        String prev = "";
        var tTars = new ArrayList<>();
        for (var t : targets) {
            // generate assignment values
            if (t instanceof LocalAccessNode access) {
                tTars.add(access.info);
            } else if (t instanceof DeRefNode deRef) {
                if (!prev.isEmpty()) {
                    prev += "\n";
                }
                prev += deRef.value.generate(cState);
                String vSpot = cState.peekEStack();
                prev += "\n" + deRef.idx.generate(cState);
                String iSpot = cState.peekEStack();
                tTars.add(new Tuple<>(vSpot, iSpot));
            } else {
                throw new InternalLuaSemanticError("what is %s in assignment?".formatted(t.getClass().getName()));
            }
        }

        var vSpots = new ArrayList<String>();
        for (int i = 0; i < values.length - 1; i++) {
            if (!prev.isEmpty()) {
                prev += "\n";
            }
            // generate target values
            prev += targets[i].generate(cState);
            vSpots.add(cState.peekEStack());
        }
        if (values.length > 0) {
            if (!prev.isEmpty()) {
                prev += "\n";
            }
            // last value needs special care
            var lv = values[values.length - 1];
            if (lv instanceof FunctionCallNode call) {
                // unroll max(0, targets.length - values.length) return values
                int aCnt = Math.max(0, targets.length - values.length);
                call.expectedArgCnt = aCnt;
                prev += call.generate(cState);
                vSpots.addAll(cState.peekEStack(aCnt));
            } else {
                prev += lv.generate(cState);
                vSpots.add(cState.peekEStack());
            }
        }

        // TODO: fill remaining value spots with NIL
        //  assign all values with either genLocal/IndexedSet

        return null;
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

    private static String genLocalSet(VarInfo.SpecificVarInfo info, String val) {
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
                throw new InternalLuaSemanticError("setting variable %s in closure without box???".formatted(info.baseInfo()));
            }
        }
        return assignment;
    }

    public String generateOld() {
        assert targets.length > 0 : "empty targets in AssignmentNode";
        assert values.length > 0 : "empty values in AssignmentNode";

        if (targets.length == 1 && values.length == 1) {
            Node t = targets[0];
            if (t instanceof DeRefNode deRef) {
                return "TypeUtils$.asIndexable($vm, %s)._luaSet($vm, %s, %s);".formatted(
                        deRef.value.generate(), deRef.idx.generate(), values[0].generate());
            } else if (t instanceof LocalAccessNode localAccess) {
                return "%s = %s".formatted(localAccess.generate(), values[0].generate());
            }
            throw new RuntimeException("should not reach");
        } else {
            var sb = new StringBuilder();
            var assigTars = new ArrayList<>();

            // calculate targets up until the last deref and store them in temp vars
            for (Node t : targets) {
                if (t instanceof LocalAccessNode ln) {
                    assigTars.add(ln);
                } else if (t instanceof DeRefNode deRef) {
                    String container = tempVarNameGen.get();
                    sb.append("ILuaIndexable$ ").append(container).append(" = TypeUtils$.asIndexable($vm, ").append(deRef.value.generate()).append(");\n");
                    assigTars.add(new Tuple<>(container, deRef.idx));
                } else {
                    throw new RuntimeException("should not reach");
                }
            }

            // calculate as many values as we have targets and store them in temp vars
            boolean multiResultAlarm = false;
            var calcVals = new ArrayList<String>();
            for (int i = 0; i < targets.length && i < values.length; i++) {
                String container = tempVarNameGen.get();
                var curVal = values[i];
                if (i == values.length - 1 && curVal instanceof ResolveResultNode rfrNode) {
                    // if the last value is a function call, we strip the result handling here and to it manually later
                    sb.append("LuaVariable$[] ").append(container).append(" = ").append(rfrNode.call.generate()).append(";\n");
                    multiResultAlarm = true;
                } else {
                    sb.append("LuaVariable$ ").append(container).append(" = ").append(curVal.generate()).append(";\n");
                }
                calcVals.add(container);
            }

            // put the rest of the values into a discard variable
            if (targets.length < values.length) {
                String discard = tempVarNameGen.get();
                sb.append("LuaVariable$ ").append(discard).append(";\n");
                for (int i = targets.length; i < values.length; i++) {
                    var curVal = values[i];
                    if (curVal instanceof ResolveResultNode rfrNode) {
                        // since we don't use the result anyway, we can safely strip result handling
                        curVal = rfrNode.call;
                    }
                    sb.append(discard).append(" = ").append(curVal.generate()).append(";\n");
                }
                sb.append(discard).append(" = null;\n");
            }

            // assign all remaining values to their respective targets
            boolean isFirst = true;
            for (int i = 0; i < (multiResultAlarm ? calcVals.size() - 1 : calcVals.size()); i++) {
                if (!isFirst) {
                    sb.append('\n');
                }
                isFirst = false;
                if (assigTars.get(i) instanceof LocalAccessNode localAccess) {
                    sb.append(localAccess.generate()).append(" = ").append(calcVals.get(i)).append(";\n");
                } else {

                    var tp = (Tuple<String, Node>) assigTars.get(i);
                    sb.append(tp.x()).append("._luaSet($vm, ").append(tp.y().generate()).append(", ").append(calcVals.get(i)).append(");\n");
                }
                sb.append(calcVals.get(i)).append(" = null;");
            }

            if (multiResultAlarm) {
                // handle result assignments manually by either assigning nil or a return value
                String array = calcVals.get(calcVals.size() - 1);
                for (int i = calcVals.size() - 1, j = 0; i < assigTars.size(); i++) {
                    sb.append('\n');
                    if (assigTars.get(i) instanceof LocalAccessNode localAccess) {
                        sb.append(localAccess.generate()).append(" = ").append(array).append(".length <= ").append(j)
                                .append(" ? LuaNil$.singleton : ").append(array).append('[').append(j).append("];");
                    } else {
                        var tp = (Tuple<String, Node>) assigTars.get(i);
                        sb.append(tp.x()).append("._luaSet($vm, ").append(tp.y().generate()).append(", ").append(array).append(".length <= ")
                                .append(j).append(" ? LuaNil$.singleton : ").append(array).append('[').append(j).append("]);");
                    }
                    j++;
                }
            } else {
                // assign the rest of the targets to nil
                for (int i = calcVals.size(); i < assigTars.size(); i++) {
                    if (assigTars.get(i) instanceof LocalAccessNode localAccess) {
                        sb.append('\n').append(localAccess.generate()).append(" = LuaNil$.singleton;");
                    } else {
                        var tp = (Tuple<String, Node>) assigTars.get(i);
                        sb.append('\n').append(tp.x()).append("._luaSet($vm, ").append(tp.y().generate()).append(", LuaNil$.singleton);");
                    }
                }
            }

            return sb.toString();
        }
    }
}

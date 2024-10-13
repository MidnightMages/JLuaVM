package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.values.DeRefNode;
import dev.asdf00.jluavm.parsing.ir.values.LocalAccessNode;
import dev.asdf00.jluavm.parsing.ir.values.ResolveResultNode;
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
    public String generate() {
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

package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.values.DeRefNode;
import dev.asdf00.jluavm.parsing.ir.values.LocalAccessNode;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.ArrayList;
import java.util.function.Supplier;

public class AssignmentNode extends Node {
    private final Supplier<String> tempValGen;
    public final Node[] targets;
    public final Node[] values;

    public AssignmentNode(Supplier<String> tempValGen, Node[] targets, Node[] values) {
        this.tempValGen = tempValGen;
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
                return "TypeUtils$.asType($vm, LuaTable$.class, %s).set($vm, %s, %s);".formatted(
                        deRef.value.generate(), deRef.idx.generate(), values[0].generate());
            } else if (t instanceof LocalAccessNode localAccess) {
                return "%s = %s;".formatted(localAccess.generate(), values[0].generate());
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
                    String container = tempValGen.get();
                    sb.append("LuaTable$ ").append(container).append(" = TypeUtils$.asType($vm, LuaTable$.class, ")
                            .append(deRef.value.generate()).append(");\n");
                    assigTars.add(new Tuple<>(container, deRef.idx));
                } else {
                    throw new RuntimeException("should not reach");
                }
            }

            // calculate as many values as we have targets and store them in temp vars
            var calcVals = new ArrayList<String>();
            for (int i = 0; i < targets.length && i < values.length; i++) {
                String container = tempValGen.get();
                sb.append("LuaVariable$ ").append(container).append(" = ").append(values[i].generate()).append(";\n");
                calcVals.add(container);
            }

            // put the rest of the values into a discard variable
            if (targets.length < values.length) {
                String discard = tempValGen.get();
                sb.append("LuaVariable$ ").append(discard).append(";\n");
                for (int i = targets.length; i < values.length; i++) {
                    sb.append(discard).append(" = ").append(values[i].generate()).append(";\n");
                }
                sb.append(discard).append(" = null;\n");
            }

            // assign all remaining values to their respective targets
            boolean isFirst = true;
            for (int i = 0; i < calcVals.size(); i++) {
                if (!isFirst) {
                    sb.append('\n');
                }
                isFirst = false;
                if (assigTars.get(i) instanceof LocalAccessNode localAccess) {
                    sb.append(localAccess.generate()).append(" = ").append(calcVals.get(i)).append(";\n");
                } else {
                    var tp = (Tuple<String, Node>) assigTars.get(i);
                    sb.append(tp.x()).append(".set($vm, ").append(tp.y().generate()).append(", ").append(calcVals.get(i)).append(");\n");
                }
                sb.append(calcVals.get(i)).append(" = null;");
            }

            // assign the rest of the targets to nil
            for (int i = calcVals.size(); i < assigTars.size(); i++) {
                if (assigTars.get(i) instanceof LocalAccessNode localAccess) {
                    sb.append('\n').append(localAccess.generate()).append(" = LuaNil$.singleton;");
                } else {
                    var tp = (Tuple<String, Node>) assigTars.get(i);
                    sb.append('\n').append(tp.x()).append(".set($vm, ").append(tp.y().generate()).append(", LuaNil$.singleton);");
                }
            }

            return sb.toString();
        }
    }
}

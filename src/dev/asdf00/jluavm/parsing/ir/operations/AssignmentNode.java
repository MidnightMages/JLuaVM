package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.values.DeRefNode;
import dev.asdf00.jluavm.parsing.ir.values.LocalAccessNode;

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
        if (targets.length == 1) {
            Node t = targets[0];
            if (values.length == 1) {
                if (t instanceof DeRefNode deRef) {
                    return "TypeUtils$.asType($vm, LuaTable$.class, %s).set(%s, %s);".formatted(
                            deRef.value.generate(), deRef.idx.generate(), values[0].generate());
                } else if (t instanceof LocalAccessNode localAccess) {
                    return "%s = %s;".formatted(localAccess.generate(), values[0].generate());
                }
                throw new RuntimeException("should not reach");
            } else {
                var sb = new StringBuilder();
                String assigContainer = "";
                Node index = null;
                Node access = null;
                if (t instanceof DeRefNode deRef) {
                    // if last op is deref, we store the last table and use the deref as an index for setting into this table
                    assigContainer = tempValGen.get();
                    index = deRef.idx;
                    sb.append("LuaTable$ ").append(assigContainer).append(" = TypeUtils$.asType($vm, LuaTable$.class, ")
                            .append(deRef.value.generate()).append(");\n");
                } else if (t instanceof LocalAccessNode localAccess) {
                    access = localAccess;
                } else {
                    throw new RuntimeException("should not reach");
                }
                var tVal = tempValGen.get();
                var dumpVal = tempValGen.get();
                // establish a container variable which will later be used for assigning the target and a dump variable
                sb.append("LuaVariable$ ").append(tVal).append(" = ").append(values[0].generate()).append(";\nLuaVariable$ ")
                        .append(dumpVal).append(';');
                for (int i = 1; i < values.length; i++) {
                    sb.append('\n').append(dumpVal).append(" = ").append(values[i].generate()).append(';');
                }
                // clear dump variable
                sb.append('\n').append(dumpVal).append(" = null;\n");
                // assign previously stored value
                if (access != null) {
                    sb.append(access.generate()).append(" = ").append(tVal).append(';');
                } else {
                    sb.append(assigContainer).append(".set(").append(index.generate()).append(", ").append(tVal).append(");");
                }
                return sb.toString();
            }
        } else {
            // TODO: multiple targets, any values

            return null;
        }
    }
}

package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.controlflow.FunctionCallNode;

/**
 * Every function call should generate a FunctionCall wrapped by a ResolveFunctionResult.
 * This node can be stripped away on later use but by stripping this node away, the user
 * must handle a LuaVariable$[] which is not a valid lua type.
 */
public class ResolveResultNode extends Node {
    public final FunctionCallNode call;

    public ResolveResultNode(FunctionCallNode call) {
        this.call = call;
    }

    @Override
    public String generate() {
        return "TypeUtils$.resolveResult(%s);".formatted(call.generate());
    }
}

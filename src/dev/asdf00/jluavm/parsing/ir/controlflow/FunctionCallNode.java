package dev.asdf00.jluavm.parsing.ir.controlflow;

import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.parsing.ir.values.ResolveResultNode;

/**
 * ATTENTION! This node, in stark contrast to all other value nodes generates a LuaVariable$[] which requires extra handling!
 * Usually this node is wrapped in a {@link ResolveResultNode}.
 */
public class FunctionCallNode extends Node {
    @Override
    public String generate() {
        return "";
    }
}

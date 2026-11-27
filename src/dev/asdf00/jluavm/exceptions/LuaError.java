package dev.asdf00.jluavm.exceptions;

import dev.asdf00.jluavm.parsing.ir.IRElement;

public class LuaError extends RuntimeException {
    private final IRElement.ErrorNode errorNode;

    public LuaError(IRElement.ErrorNode errorNode) {
        super("Lua error '%s' thrown at position %s".formatted(errorNode.msg.toString(), errorNode.pos));
        this.errorNode = errorNode;
    }
}

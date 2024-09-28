package dev.asdf00.jluavm.parsing.exceptions;

import dev.asdf00.jluavm.parsing.container.Position;

public class LuaSemanticException extends LuaLoadingException {
    public LuaSemanticException(Position pos, String msg) {
        super(pos, msg);
    }
}

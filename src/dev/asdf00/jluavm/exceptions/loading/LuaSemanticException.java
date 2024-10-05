package dev.asdf00.jluavm.exceptions.loading;

import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.parsing.container.Position;

public class LuaSemanticException extends LuaLoadingException {
    public LuaSemanticException(Position pos, String msg) {
        super(pos, msg);
    }
}

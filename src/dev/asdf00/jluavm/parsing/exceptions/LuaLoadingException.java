package dev.asdf00.jluavm.parsing.exceptions;

import dev.asdf00.jluavm.parsing.container.Position;

public abstract class LuaLoadingException extends RuntimeException {
    public final Position pos;
    protected LuaLoadingException(Position pos, String msg) {
        super(msg);
        this.pos = pos;
    }
}

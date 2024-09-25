package dev.asdf00.jluavm.parsing.exceptions;

import dev.asdf00.jluavm.parsing.Position;

public abstract class LuaReadingException extends RuntimeException {
    public final Position pos;
    protected LuaReadingException(Position pos, String msg) {
        super(msg);
        this.pos = pos;
    }
}

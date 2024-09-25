package dev.asdf00.jluavm.parsing.exceptions;

import dev.asdf00.jluavm.parsing.Position;

public class LuaLexerException extends LuaReadingException {
    public final Position pos;
    public LuaLexerException(Position pos, String msg) {
        super(msg);
        this.pos = pos;
    }
}

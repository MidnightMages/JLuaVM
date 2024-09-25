package dev.asdf00.jluavm.parsing.exceptions;

import dev.asdf00.jluavm.parsing.Position;

public class LuaLexerException extends LuaReadingException {
    public LuaLexerException(Position pos, String msg) {
        super(pos, msg);
    }
}

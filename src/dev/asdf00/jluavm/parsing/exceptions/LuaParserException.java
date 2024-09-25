package dev.asdf00.jluavm.parsing.exceptions;

import dev.asdf00.jluavm.parsing.Position;

public class LuaParserException extends LuaReadingException {
    public LuaParserException(Position pos, String msg) {
        super(pos, msg);
    }
}

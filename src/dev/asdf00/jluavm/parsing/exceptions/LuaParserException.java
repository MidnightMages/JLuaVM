package dev.asdf00.jluavm.parsing.exceptions;

import dev.asdf00.jluavm.parsing.container.Position;

public class LuaParserException extends LuaLoadingException {
    public LuaParserException(Position pos, String msg) {
        super(pos, msg);
    }
}

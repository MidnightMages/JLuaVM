package dev.asdf00.jluavm.exceptions.loading;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.exceptions.LuaLoadingException;

public class LuaParserException extends LuaLoadingException {
    public LuaParserException(Position pos, String msg) {
        super(pos, msg);
    }
}

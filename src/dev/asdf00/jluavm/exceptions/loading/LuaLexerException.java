package dev.asdf00.jluavm.exceptions.loading;

import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.parsing.container.Position;

public class LuaLexerException extends LuaLoadingException {
    public LuaLexerException(Position pos, String msg) {
        super(pos, msg);
    }
}

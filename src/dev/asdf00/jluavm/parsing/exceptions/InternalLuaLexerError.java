package dev.asdf00.jluavm.parsing.exceptions;

public class InternalLuaLexerError extends RuntimeException {
    public InternalLuaLexerError(String msg) {
        super(msg);
    }
}

package dev.asdf00.jluavm.exceptions.loading;

public class InternalLuaLexerError extends RuntimeException {
    public InternalLuaLexerError(String msg) {
        super(msg);
    }
    public InternalLuaLexerError(String msg, Throwable cause) {
        super(msg, cause);
    }
}

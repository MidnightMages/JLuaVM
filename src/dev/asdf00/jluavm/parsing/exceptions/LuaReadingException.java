package dev.asdf00.jluavm.parsing.exceptions;

public abstract class LuaReadingException extends RuntimeException {
    protected LuaReadingException(String msg) {
        super(msg);
    }
}

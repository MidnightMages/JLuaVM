package dev.asdf00.jluavm.exceptions;

public class LuaJavaError extends RuntimeException {
    public LuaJavaError(String message, Throwable e) {
        super(message, e);
    }

    public LuaJavaError(String message) {
        super(message);
    }
}

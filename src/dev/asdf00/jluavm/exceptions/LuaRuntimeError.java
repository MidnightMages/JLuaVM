package dev.asdf00.jluavm.exceptions;

public abstract class LuaRuntimeError extends RuntimeException {
    public LuaRuntimeError(String msg, Throwable cause) {
        super(msg, cause);
    }

    public LuaRuntimeError(String msg) {
        super(msg);
    }
}

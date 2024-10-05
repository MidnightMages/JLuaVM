package dev.asdf00.jluavm.exceptions;

public abstract class LuaRuntimeError extends RuntimeException {
    public LuaRuntimeError(String msg) {
        super(msg);
    }
}

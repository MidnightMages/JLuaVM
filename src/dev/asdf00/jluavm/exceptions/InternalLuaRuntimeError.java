package dev.asdf00.jluavm.exceptions;

public class InternalLuaRuntimeError extends RuntimeException {
    public InternalLuaRuntimeError(String msg) {
        super(msg);
    }
}

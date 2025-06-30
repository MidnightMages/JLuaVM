package dev.asdf00.jluavm.exceptions;

public class InternalLuaSerializationError extends LuaRuntimeError {
    public InternalLuaSerializationError(String msg, Throwable cause) {
        super(msg, cause);
    }
}

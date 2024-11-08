package dev.asdf00.jluavm.runtime.errors;

public class LuaTypeError extends AbstractLuaError {
    private final String message;

    public LuaTypeError(String message) {
        this.message = message;
    }
    public LuaTypeError() {
        this.message = "A lua type error has ocurred";
    }
}

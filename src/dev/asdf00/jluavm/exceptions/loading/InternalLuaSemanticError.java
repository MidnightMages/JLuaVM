package dev.asdf00.jluavm.exceptions.loading;

public class InternalLuaSemanticError extends RuntimeException {
    public InternalLuaSemanticError(String msg) {
        super(msg);
    }
}

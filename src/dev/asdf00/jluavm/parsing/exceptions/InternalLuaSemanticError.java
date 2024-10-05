package dev.asdf00.jluavm.parsing.exceptions;

public class InternalLuaSemanticError extends RuntimeException {
    public InternalLuaSemanticError(String msg) {
        super(msg);
    }
}

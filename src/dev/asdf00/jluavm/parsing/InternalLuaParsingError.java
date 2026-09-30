package dev.asdf00.jluavm.parsing;

public class InternalLuaParsingError extends Error {
    public InternalLuaParsingError(String message) {
        super(message);
    }
}
package dev.asdf00.jluavm.exceptions.runtime;

import dev.asdf00.jluavm.exceptions.LuaRuntimeError;

public class LuaNilError extends LuaRuntimeError {
    public LuaNilError(String msg) {
        super(msg);
    }
}

package dev.asdf00.jluavm.exceptions;

import dev.asdf00.jluavm.runtime.typesOLD.LuaStringOLD;
import dev.asdf00.jluavm.runtime.typesOLD.LuaVariableOLD;

public abstract class LuaRuntimeError$ extends RuntimeException {
    public LuaRuntimeError$(String msg) {
        super(msg);
    }

    public LuaVariableOLD getErrorString() {
        return new LuaStringOLD(getMessage());
    }
}

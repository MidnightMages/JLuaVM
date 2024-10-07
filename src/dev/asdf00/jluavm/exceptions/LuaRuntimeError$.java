package dev.asdf00.jluavm.exceptions;

import dev.asdf00.jluavm.types.LuaString$;
import dev.asdf00.jluavm.types.LuaVariable$;

public abstract class LuaRuntimeError$ extends RuntimeException {
    public LuaRuntimeError$(String msg) {
        super(msg);
    }

    public LuaVariable$ getErrorString() {
        return new LuaString$(getMessage());
    }
}

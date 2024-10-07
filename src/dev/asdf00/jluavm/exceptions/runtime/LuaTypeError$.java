package dev.asdf00.jluavm.exceptions.runtime;

import dev.asdf00.jluavm.exceptions.LuaRuntimeError$;

public class LuaTypeError$ extends LuaRuntimeError$ {
    public LuaTypeError$(String msg) {
        super(msg);
    }
}

package dev.asdf00.jluavm.exceptions.runtime;

import dev.asdf00.jluavm.exceptions.LuaRuntimeError$;

public class LuaArgumentError$ extends LuaRuntimeError$ {
    public LuaArgumentError$(String msg) {
        super(msg);
    }
}

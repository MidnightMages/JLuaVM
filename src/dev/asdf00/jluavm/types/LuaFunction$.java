package dev.asdf00.jluavm.types;

import dev.asdf00.jluavm.LuaVM;

public abstract class LuaFunction$ extends LuaVariable$ {
    protected final LuaVM $vm;

    public LuaFunction$(LuaVM vm) {
        super(LuaType.FUNC);
        this.$vm = vm;
    }

    public abstract LuaVariable$[] Invoke(LuaVariable$ ...arg);

}

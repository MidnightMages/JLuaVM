package dev.asdf00.jluavm.types;

import dev.asdf00.jluavm.internals.LuaVM_RT$;

public abstract class LuaFunction$ extends LuaVariable$ {
    public final LuaVM_RT$ $vm;

    public LuaFunction$(LuaVM_RT$ vm) {
        super(LuaType.FUNC);
        this.$vm = vm;
    }

    public abstract LuaVariable$[] Invoke(LuaVariable$ ...arg);

}

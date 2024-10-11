package dev.asdf00.jluavm.types;

import dev.asdf00.jluavm.internals.LuaVM_RT$;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class LuaFunction$ extends LuaVariable$ {
    public final LuaVM_RT$ $vm;
    public static LuaTable$ _ENV;

    public LuaFunction$(LuaVM_RT$ vm) {
        super(LuaType.FUNC);
        this.$vm = vm;
    }

    public abstract LuaVariable$[] Invoke(LuaVariable$ ...arg);

    protected static LuaString$ literalString$(String literal) {
        return new LuaString$(new String(Base64.getDecoder().decode(literal), StandardCharsets.UTF_8));
    }
}

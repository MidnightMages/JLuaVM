package dev.asdf00.jluavm.types;

import dev.asdf00.jluavm.internals.LuaVM_RT$;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class LuaFunction$ extends LuaVariable$ {
    public static LuaTable$ _ENV;

    public LuaFunction$() {
        super(LuaType.FUNC);
    }

    public abstract LuaVariable$[] invoke(LuaVM_RT$ $vm, LuaVariable$... arg);

    protected static LuaString$ literalStringB64$(String literal) {
        return new LuaString$(new String(Base64.getDecoder().decode(literal), StandardCharsets.UTF_8));
    }
}

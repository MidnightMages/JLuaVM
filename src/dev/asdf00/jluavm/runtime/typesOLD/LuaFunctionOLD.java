package dev.asdf00.jluavm.runtime.typesOLD;

import dev.asdf00.jluavm.internals.LuaVM_RT;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class LuaFunctionOLD extends LuaVariableOLD {
    public static LuaTableOLD _ENV;

    public LuaFunctionOLD() {
        super(LuaType.FUNC);
    }

    public abstract LuaVariableOLD[] invoke(LuaVM_RT $vm, LuaVariableOLD... arg);

    protected static LuaStringOLD literalStringB64$(String literal) {
        return new LuaStringOLD(new String(Base64.getDecoder().decode(literal), StandardCharsets.UTF_8));
    }
}

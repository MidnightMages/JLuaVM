package dev.asdf00.jluavm.runtime.typesOLD;

public final class CBox {
    public LuaVariableOLD value;

    private CBox(LuaVariableOLD value) {
        this.value = value;
    }

    public static CBox of(LuaVariableOLD value) {
        return new CBox(value);
    }
}

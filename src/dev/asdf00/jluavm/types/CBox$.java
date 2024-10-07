package dev.asdf00.jluavm.types;

public final class CBox$ {
    public LuaVariable$ value;

    private CBox$(LuaVariable$ value) {
        this.value = value;
    }

    public static CBox$ of(LuaVariable$ value) {
        return new CBox$(value);
    }
}

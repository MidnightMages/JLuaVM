package dev.asdf00.jluavm.runtime.types;

public class LuaTable implements ILuaVariable {
    public boolean hasKey(ILuaVariable key) {
        return true;
    }

    public ILuaVariable set(ILuaVariable key, ILuaVariable value) {
        return null;
    }

    public ILuaVariable get(ILuaVariable key) {
        return null;
    }
}

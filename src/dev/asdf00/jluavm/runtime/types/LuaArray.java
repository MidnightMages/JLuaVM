package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.runtime.utils.Singletons;

public final class LuaArray implements ILuaVariable {
    private final ILuaVariable[] array;

    public LuaArray(ILuaVariable[] array) {
        this.array = array;
    }

    public int length() {
        return array.length;
    }

    public ILuaVariable get(int i) {
        return i >= 0 && i < array.length ? array[i] : Singletons.NIL;
    }
}

package dev.asdf00.jluavm.runtime.types;

public interface ILuaVariable {
    default LuaTable getMetaTable() {
        return null;
    }

    default boolean isNil() {
        return false;
    }

    default boolean isNaN() {
        return false;
    }
}

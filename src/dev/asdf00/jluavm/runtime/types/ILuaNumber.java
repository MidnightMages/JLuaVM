package dev.asdf00.jluavm.runtime.types;

public interface ILuaNumber extends ILuaVariable {
    ILuaNumber rawAdd(ILuaNumber other);
}

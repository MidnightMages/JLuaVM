package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.ILuaVariable;
import dev.asdf00.jluavm.runtime.types.LuaArray;

@FunctionalInterface
public interface LFunc {
    void invoke(LuaVM_RT vm, ILuaVariable[] stackFrame, ILuaVariable[] args, int resume, ILuaVariable[] expressionStack, LuaArray returned);
}

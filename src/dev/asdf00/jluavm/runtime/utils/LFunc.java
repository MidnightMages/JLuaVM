package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.ILuaVariable;

@FunctionalInterface
public interface LFunc {
    LuaReturnValue invoke(LuaVM_RT vm, ILuaVariable[] stackFrame, ILuaVariable[] args, int resume, ILuaVariable[] expressionStack, ILuaVariable[] returned);
}

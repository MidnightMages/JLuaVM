package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.LuaObject;

@FunctionalInterface
public interface LFunc {
    void invoke(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject returned);
}

package dev.asdf00.jluavm.api.lambdas;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.LuaObject;

@FunctionalInterface
public interface LLBiMultiFunction {
    LuaObject[] apply(LuaVM_RT vm, LuaObject arg1, LuaObject arg2);
}

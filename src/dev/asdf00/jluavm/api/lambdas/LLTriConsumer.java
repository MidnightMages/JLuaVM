package dev.asdf00.jluavm.api.lambdas;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.LuaObject;

@FunctionalInterface
public interface LLTriConsumer {
    void accept(LuaVM_RT vm, LuaObject arg1, LuaObject arg2, LuaObject arg3);
}

package dev.asdf00.jluavm.api.lambdas;

import dev.asdf00.jluavm.internals.LuaVM_RT;

@FunctionalInterface
public interface LLRunnable {
    void run(LuaVM_RT vm);
}

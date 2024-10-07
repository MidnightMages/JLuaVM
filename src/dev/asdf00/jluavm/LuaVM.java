package dev.asdf00.jluavm;

import dev.asdf00.jluavm.internals.LuaVM_RT$;

public abstract class LuaVM {

    public static LuaVM create() {
        return new LuaVM_RT$();
    }

    public void load(String code) {

    }

    public VmResult run() {
        return new VmResult(VmRunState.SUCCESS, new Object[]{});
    }

    public record VmResult(VmRunState state, Object[] returnVars) {
    }

    public enum VmRunState {
        SUCCESS,
        EXECUTION_ERROR,
    }
}

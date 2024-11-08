package dev.asdf00.jluavm;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.stdlib.LMath;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

public abstract class LuaVM {

    public static LuaVM create() {
        return new LuaVM_RT();
    }

    public LuaVM withStdLib() {
        var _G = LuaObject.table();
        _G.set("math", LMath.getTable());
        return this;
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

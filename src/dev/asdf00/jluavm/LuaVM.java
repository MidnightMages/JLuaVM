package dev.asdf00.jluavm;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

public abstract class LuaVM {

    public static LuaVM create() {
        return new LuaVM_RT();
    }

    public LuaVM withStdLib() {
        var _G = LuaObject.table();

        // https://www.lua.org/manual/5.4/manual.html#6.7
        var mathTbl = LuaObject.table();
        _G.set("math", mathTbl);
        mathTbl.set("abs", AtomicLuaFunction.forOneResult((vm, x) -> {
            if (x.isNumber()) return x.asDouble() < 0 ? x.unm() : x;
            vm.errorArgType(1, "number", x);
            return LuaObject.NIL;
        }).obj());

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

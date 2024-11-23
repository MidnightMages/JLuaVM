package dev.asdf00.jluavm;

import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.parsing.Parser;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRFunction;
import dev.asdf00.jluavm.runtime.stdlib.LGlobal;
import dev.asdf00.jluavm.runtime.stdlib.LMath;
import dev.asdf00.jluavm.runtime.stdlib.LTable;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public abstract class LuaVM {

    public static LuaVM create() {
        return new LuaVM_RT();
    }

    protected LuaObject _G = LuaObject.nil();
    protected LuaFunction rootFunc = null;
    private void AssertRootFuncNull(){
        if (rootFunc != null)
            throw new IllegalStateException("Cannot set _G like this after code has been loaded");
    }

    private static final Supplier<String> jClassNameGen = new Supplier<>() {
        private final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public String get() {
            return "GeneratedLuaFunc_%d".formatted(cnt.getAndAdd(1));
        }
    };

    public LuaVM withStdLib() {
        AssertRootFuncNull();
        _G = LGlobal.getTable(false);
        _G.set("math", LMath.getTable());
        _G.set("table", LTable.getTable());
        return this;
    }

    public LuaVM withEmptyEnv() {
        AssertRootFuncNull();
        _G = LuaObject.table();
        return this;
    }

    public LuaObject get_G() {
        return _G;
    }

    public LuaVM withRootFunc(String code) throws LuaLoadingException, InternalLuaLoadingError {
        rootFunc = load(code, _G);
        return this;
    }

    public LuaFunction load(String code) throws LuaLoadingException, InternalLuaLoadingError {
        return load(code, _G);
    }

    public LuaFunction load(String code, LuaObject _ENV) throws LuaLoadingException, InternalLuaLoadingError {
        if (_ENV == null) {
            throw new InternalLuaLoadingError("got invalid _ENV");
        }
        IRFunction rootFunc = new Parser(code).parse();
        var javaIntermediateCode = new CompilationState(jClassNameGen);
        rootFunc.generate(javaIntermediateCode);
        javaIntermediateCode.resolveAllPatches();
        var rootCtor = javaIntermediateCode.loadAndLinkAllClasses();
        try {
            return rootCtor.newInstance(new LuaObject[]{_ENV}, Singletons.EMPTY_LUA_OBJ_ARRAY);
        } catch (ReflectiveOperationException e) {
            throw new InternalLuaLoadingError(e);
        }
    }

    public abstract VmResult run();

    public record VmResult(VmRunState state, LuaObject[] returnVars) {
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof VmResult other) {
                return state == other.state && Arrays.equals(returnVars, other.returnVars);
            }
            return false;
        }
    }

    public enum VmRunState {
        SUCCESS,
        EXECUTION_ERROR,
    }
}

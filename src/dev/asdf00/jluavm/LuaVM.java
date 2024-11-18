package dev.asdf00.jluavm;

import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.parsing.Parser;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRFunction;
import dev.asdf00.jluavm.runtime.stdlib.LGlobal;
import dev.asdf00.jluavm.runtime.stdlib.LMath;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.function.Supplier;

public abstract class LuaVM {

    public static LuaVM create() {
        return new LuaVM_RT();
    }

    private LuaObject _G = LuaObject.nil(); // TODO more into LuaVM_RT

    private static final Supplier<String> jClassNameGen = new Supplier<>() {
        private int cnt = 0;

        @Override
        public String get() {
            return "GeneratedLuaFunc_%d".formatted(cnt++);
        }
    };

    public LuaVM withStdLib() {
        _G = LGlobal.getTable(false);
        _G.set("math", LMath.getTable());
        return this;
    }

    public LuaObject get_G() {
        return _G;
    }

    public LuaFunction load(String code) {
        return load(code, _G);
    }

    public LuaFunction load(String code, LuaObject _ENV) throws InternalLuaLoadingError {
        IRFunction rootFunc = new Parser(code).parse();
        var javaIntermediateCode = new CompilationState(jClassNameGen);
        rootFunc.generate(javaIntermediateCode);
        javaIntermediateCode.resolveAllPatches();
        var rootCtor = javaIntermediateCode.loadAndLinkAllClasses(_ENV);
        try {
            return rootCtor.newInstance(Singletons.EMPTY_LUA_OBJ_ARRAY);
        } catch (ReflectiveOperationException e) {
            throw new InternalLuaLoadingError(e);
        }
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

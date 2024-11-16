package dev.asdf00.jluavm;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.parsing.Parser;
import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.IRFunction;
import dev.asdf00.jluavm.runtime.stdlib.LGlobal;
import dev.asdf00.jluavm.runtime.stdlib.LMath;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.function.Supplier;

public abstract class LuaVM {

    public static LuaVM create() {
        return new LuaVM_RT();
    }
    private LuaObject _G = null; // TODO more into LuaVM_RT

    private final Supplier<String> jClassNameGen = new Supplier<String>() {
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

    public void load(String code) {
        IRFunction rootFunc = new Parser(code).parse();
        var javaIntermediateCode = new CompilationState(jClassNameGen);
        rootFunc.generate(javaIntermediateCode);
        // TODO load stuff in javaIntermediateCode into the JVM
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

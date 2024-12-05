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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class LuaVM {

    public static LuaVM create() {
        return new LuaVM_RT();
    }

    protected LuaObject _G = LuaObject.nil();
    protected LuaFunction rootFunc = null;

    private void AssertRootFuncNull() {
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
        _G.set("string", LuaObject.table());  // TODO implement string functions
        _G.set("_EXT", LuaObject.table(
                LuaObject.of("nil"), LuaObject.table(),
                LuaObject.of("boolean"), LuaObject.table(),
                LuaObject.of("number"), LuaObject.table(),
                LuaObject.of("string"), _G.get("string"),
                LuaObject.of("function"), LuaObject.table(),
                LuaObject.of("thread"), LuaObject.table(),
                LuaObject.of("table"), LuaObject.table()
        ));
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

    public void dumpJICFor(String luaCode, Path into) {
        IRFunction rootFunc = new Parser(luaCode).parse();
        var javaIntermediateCode = new CompilationState(jClassNameGen);
        rootFunc.generate(javaIntermediateCode);
        javaIntermediateCode.resolveAllPatches();
        var lld = into.resolve("dev/asdf00/jluavm/lualoaded");
        try {
            if (!Files.exists(lld)) {
                Files.createDirectory(lld);
            }
            for (var jic : javaIntermediateCode.functionJavaCode) {
                Files.writeString(lld.resolve(jic.x() + ".java"), jic.y());
            }
            String depts = javaIntermediateCode.innerFunctionDependencies.stream().map(lst -> String.join(",", lst.stream().map(Object::toString).toArray(String[]::new)))
                    .collect(Collectors.joining(";"));
            Files.writeString(lld.resolve("depts.txt"), depts);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("successfully dumped files!");
    }

    public void withDumpedRoot(String depFile, Class<? extends LuaFunction>... jClasses) {
        @SuppressWarnings("unchecked")
        var constructors = (Constructor<? extends LuaFunction>[]) new Constructor<?>[jClasses.length];
        var depts = new Field[jClasses.length];
        for (int i = 0; i < constructors.length; i++) {
            try {
                constructors[i] = jClasses[i].getDeclaredConstructor(LuaObject[].class, LuaObject[].class);
                depts[i] = jClasses[i].getDeclaredField("innerFunctions");
            } catch (ReflectiveOperationException e) {
                throw new InternalLuaLoadingError(e);
            }
        }

        // load dependencies
        var innerFunctionDependencies = new ArrayList<ArrayList<Integer>>();
        var left = depFile;
        for (; ; ) {
            int gSplit = left.indexOf(';');
            if (gSplit < 0) {
                break;
            }
            var iStr = left.substring(0, gSplit);
            left = left.substring(gSplit + 1);
            var innerList = new ArrayList<Integer>();
            for (; ; ) {
                int iSplit = iStr.indexOf(',');
                if (iSplit < 0) {
                    break;
                }
                innerList.add(Integer.parseInt(iStr.substring(0, iSplit)));
                iStr = iStr.substring(iSplit + 1);
            }
            if (!iStr.isEmpty()) {
                innerList.add(Integer.parseInt(iStr));
            }
            innerFunctionDependencies.add(innerList);
        }
        if (!left.isEmpty()) {
            var iStr = left;
            var innerList = new ArrayList<Integer>();
            for (; ; ) {
                int iSplit = iStr.indexOf(',');
                if (iSplit < 0) {
                    break;
                }
                innerList.add(Integer.parseInt(iStr.substring(0, iSplit)));
                iStr = iStr.substring(iSplit + 1);
            }
            if (!iStr.isEmpty()) {
                innerList.add(Integer.parseInt(iStr));
            }
            innerFunctionDependencies.add(innerList);
        } else {
            innerFunctionDependencies.add(new ArrayList<>());
        }

        // link classes
        for (int i = 0; i < constructors.length; i++) {
            try {
                var innerDepts = innerFunctionDependencies.get(i);
                @SuppressWarnings("unchecked")
                var ds = (Constructor<? extends LuaFunction>[]) new Constructor<?>[innerDepts.size()];
                for (int j = 0; j < innerDepts.size(); j++) {
                    ds[j] = constructors[innerDepts.get(j)];
                }
                depts[i].set(null, ds);
            } catch (ReflectiveOperationException e) {
                throw new InternalLuaLoadingError(e);
            }
        }
        // return constructor for root function
        try {
            rootFunc = constructors[constructors.length - 1].newInstance(new LuaObject[]{_G}, Singletons.EMPTY_LUA_OBJ_ARRAY);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
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

        @Override
        public String toString() {
            return "VmResult[" +
                    "state=" + state +
                    ", returnVars=" + Arrays.toString(returnVars) +
                    ']';
        }
    }

    public enum VmRunState {
        SUCCESS,
        EXECUTION_ERROR,
    }
}

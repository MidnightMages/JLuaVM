package dev.asdf00.jluavm;

import dev.asdf00.jluavm.api.functions.ApiFunctionRegistry;
import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;
import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.Parser;
import dev.asdf00.jluavm.parsing.ir.IRFunction;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.types.AbstractGeneratedLuaFunction;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class LuaVM {

    public static final String STD_LIB_REG_ID = "jluavm.stdlib";
    private static final Supplier<String> J_CLASS_NAME_GEN;
    private static final ConcurrentHashMap<String, Constructor<? extends AbstractGeneratedLuaFunction>[]> COMPILATION_CACHE = new ConcurrentHashMap<>();
    private static final Object COMPILATION_CACHE_LOCK_OBJ = new Object();

    // =================================================================================================================
    // po-ta-tos, mash 'em, boil 'em, stick 'em in a stew
    // =================================================================================================================

    protected final Map<String, ApiFunctionRegistry> registries;
    protected LuaObject _G = LuaObject.nil();
    protected LuaFunction rootFunc = null;

    protected volatile boolean requestedStop = false;

    public static VmBuilder builder() {
        return new VmBuilder();
    }

    protected LuaVM() {
        registries = new HashMap<>();
    }

    protected LuaVM(Map<String, ApiFunctionRegistry> registries) {
        this.registries = registries;
    }

    public final VmResult run() {
        return runWithArgs(Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public abstract VmResult runWithArgs(LuaObject... rootArgs);

    public abstract VmResult runContinue();

    public abstract byte[] serialize();

    public void requestStop() {
        requestedStop = true;
    }

    // =================================================================================================================
    // static helper methods
    // =================================================================================================================

    public static Constructor<? extends AbstractGeneratedLuaFunction>[] compile(String code) throws LuaLoadingException {
        var cachedCtor = COMPILATION_CACHE.getOrDefault(code, null);
        if (cachedCtor == null) {
            synchronized (COMPILATION_CACHE_LOCK_OBJ) {
                cachedCtor = COMPILATION_CACHE.getOrDefault(code, null);
                if (cachedCtor == null) {
                    IRFunction rootFunc = new Parser(code).parse();
                    var javaIntermediateCode = new CompilationState(J_CLASS_NAME_GEN, code);
                    rootFunc.generate(javaIntermediateCode);
                    javaIntermediateCode.resolveAllPatches();
                    cachedCtor = javaIntermediateCode.loadAndLinkAllClasses();
                    COMPILATION_CACHE.put(code, cachedCtor); // TODO could optimize this cache by stripping comments maybe?
                }
            }
        }
        return cachedCtor;
    }

    public static LuaFunction load(String code, LuaObject _ENV) throws LuaLoadingException, InternalLuaLoadingError {
        if (_ENV == null) {
            throw new InternalLuaLoadingError("got invalid _ENV");
        }
        var unit = compile(code);
        try {
            return unit[unit.length - 1].newInstance("main.lua", 0, LuaObject.box(_ENV), Singletons.EMPTY_LUA_OBJ_ARRAY);
        } catch (ReflectiveOperationException e) {
            throw new InternalLuaLoadingError(e);
        }
    }

    // =================================================================================================================
    // debug access to java intermediate code
    // =================================================================================================================

    public void dumpJICFor(String luaCode, Path into) {
        IRFunction rootFunc = new Parser(luaCode).parse();
        var javaIntermediateCode = new CompilationState(J_CLASS_NAME_GEN, luaCode);
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
                constructors[i] = jClasses[i].getDeclaredConstructor(LuaObject.class, LuaObject[].class);
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
            rootFunc = constructors[constructors.length - 1].newInstance(_G, Singletons.EMPTY_LUA_OBJ_ARRAY);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // helper types
    // =================================================================================================================

    public enum VmRunState {
        SUCCESS,
        EXECUTION_ERROR,
        PAUSED,
    }

    public record VmResult(VmRunState state, LuaObject[] returnVars) {
        public static VmResult of(VmRunState state, LuaObject... returnVars) {
            return new VmResult(state, returnVars);
        }

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
                    ", returnVars=" + Arrays.toString(returnVars).replace("\\", "\\\\").replace("\n", "\\n") +
                    ']';
        }
    }

    // =================================================================================================================
    // static initializer for LuaVM
    // =================================================================================================================

    static {
        J_CLASS_NAME_GEN = new Supplier<>() {
            private final AtomicInteger cnt = new AtomicInteger(0);

            @Override
            public String get() {
                return "GeneratedLuaFunc_%d".formatted(cnt.getAndAdd(1));
            }
        };
    }
}

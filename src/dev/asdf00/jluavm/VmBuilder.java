package dev.asdf00.jluavm;

import dev.asdf00.jluavm.api.functions.ApiFunctionRegistry;
import dev.asdf00.jluavm.api.functions.MixedStateFunctionRegistry;
import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.internals.Coroutine;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.stdlib.*;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.StateDeserializer;
import dev.asdf00.jluavm.utils.Quadruple;
import dev.asdf00.jluavm.utils.Triple;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class VmBuilder {
    Map<String, ApiFunctionRegistry> registries = new HashMap<>();
    boolean noStd = false;

    public static VmBuilder create() {
        return new VmBuilder();
    }

    public VmBuilder noStdLib() {
        noStd = true;
        return this;
    }

    public VmBuilder withApiRegistry(ApiFunctionRegistry registry) {
        String id = registry.registryID();
        if (noStd || LuaVM.STD_LIB_REG_ID.equals(id)) {
            throw new IllegalArgumentException("ApiFunctionRegistry can not have id %s, because this is the default stdlib id".formatted(id));
        }
        if (registries.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate ApiFunctionRegistry ID %s".formatted(id));
        }
        registries.put(id, registry);
        return this;
    }

    public EnvVmBuilder emptyEnv() {
        noStd = true;
        return new EnvVmBuilder(this, LuaObject.table());
    }

    public EnvVmBuilder withEnv(LuaObject env) {
        return new EnvVmBuilder(this, env);
    }

    public EnvVmBuilder modifyEnv(Consumer<LuaObject> modificator) {
        return new EnvVmBuilder(this).modifyEnv(modificator);
    }

    public RootVmBuilder rootFunc(LuaFunction func) {
        return new EnvVmBuilder(this).rootFunc(func);
    }

    public RootVmBuilder rootFunc(String luaCode) throws LuaLoadingException {
        return new EnvVmBuilder(this).rootFunc(luaCode);
    }

    public DeserializedVmBuilder fromState(byte[] state) {
        if (!noStd) {
            var stdReg = initStdReg();
            registries.put(stdReg.registryID(), stdReg);
        }
        var deState = StateDeserializer.deserialize(registries, state);
        return new DeserializedVmBuilder(this, deState);
    }

    public static class EnvVmBuilder {
        VmBuilder parent;
        LuaObject env;

        EnvVmBuilder(VmBuilder parent, LuaObject env) {
            this.parent = parent;
            this.env = env;
            if (!parent.noStd) {
                var stdReg = initStdReg();
                parent.registries.put(stdReg.registryID(), stdReg);
            }
        }

        EnvVmBuilder(VmBuilder parent) {
            this.parent = parent;
            if (!parent.noStd) {
                var stdReg = initStdReg();
                parent.registries.put(stdReg.registryID(), stdReg);
                env = initStdLibEnv(stdReg);
            } else {
                env = LuaObject.table();
            }
        }

        public EnvVmBuilder modifyEnv(Consumer<LuaObject> modificator) {
            modificator.accept(env);
            return this;
        }

        public RootVmBuilder rootFunc(LuaFunction func) {
            return new RootVmBuilder(this, func);
        }

        public RootVmBuilder rootFunc(String luaCode) throws LuaLoadingException {
            return rootFunc(LuaVM.load(luaCode, env));
        }
    }

    public static class RootVmBuilder {
        EnvVmBuilder parent;
        LuaFunction func;

        RootVmBuilder(EnvVmBuilder parent, LuaFunction func) {
            this.parent = parent;
            this.func = func;
        }

        public RootVmBuilder modifyEnv(Consumer<LuaObject> modificator) {
            modificator.accept(parent.env);
            return this;
        }

        public LuaVM build() {
            return new LuaVM_RT(parent.parent.registries, func);
        }
    }

    public static class DeserializedVmBuilder {
        VmBuilder parent;
        Quadruple<Coroutine, Coroutine, Boolean, Boolean> state;

        DeserializedVmBuilder(VmBuilder parent, Quadruple<Coroutine, Coroutine, Boolean, Boolean> state) {
            this.parent = parent;
            this.state = state;
        }

        public LuaVM build() {
            return new LuaVM_RT(parent.registries, state);
        }
    }

    private static MixedStateFunctionRegistry initStdReg() {
        MixedStateFunctionRegistry stdLibReg = new MixedStateFunctionRegistry(LuaVM.STD_LIB_REG_ID);
        LGlobal.registerStdGlobal(stdLibReg, false);
        LMath.registerStdMath(stdLibReg);
        LTable.registerStdTable(stdLibReg);
        LString.registerStdString(stdLibReg);
        LCoroutine.registerStdCoroutine(stdLibReg);
        LDebug.registerStdDebug(stdLibReg, false);
        LVmLib.registerStdVm(stdLibReg);
        return stdLibReg;
    }

    private static LuaObject initStdLibEnv(MixedStateFunctionRegistry stdLibReg) {
        // prep global table
        LuaObject env = LuaObject.table();
        env.set("math", LuaObject.table());
        env.set("table", LuaObject.table());
        env.set("string", LuaObject.table());
        env.set("coroutine", LuaObject.table());
        env.set("debug", LuaObject.table());
        env.set("vm", LuaObject.table());

        // add all noninternal functions
        // all these functions are assumed to be stateless
        for (String fName : stdLibReg.getAllNames()) {
            if (fName.charAt(0) == '$') {
                // internal function, do not add to _G
                continue;
            }
            if (fName.indexOf('.') < 0) {
                // top level
                env.set(fName, LuaObject.of(stdLibReg.getFunction(fName)));
            } else {
                var path = fName.split("\\.");
                assert path.length == 2 : "only ever expected tbl.funcname, got: " + fName;
                env.get(path[0]).set(path[1], LuaObject.of(stdLibReg.getFunction(fName)));
            }
        }

        // set constants
        env.set("_VERSION", LuaObject.of("Lua 5.4"));
        env.set("_G", env);
        LMath.addMathConstants(env.get("math"));

        // clone stuff for extension functions
        env.set("_EXT", LuaObject.table(
                LuaObject.of("nil"), LuaObject.table(),
                LuaObject.of("boolean"), LuaObject.table(),
                LuaObject.of("number"), LuaObject.table(),
                LuaObject.of("string"), LString.createExtTable(env.get("string")),
                LuaObject.of("function"), LuaObject.table(),
                LuaObject.of("thread"), LuaObject.table(),
                LuaObject.of("table"), LuaObject.table()
        ));

        return env;
    }
}
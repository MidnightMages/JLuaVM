package dev.asdf00.jluavm.api.functions;

import dev.asdf00.jluavm.api.lambdas.*;
import dev.asdf00.jluavm.internals.Coroutine;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.function.BiFunction;

@SuppressWarnings("unused")
public final class AtomicLuaFunction extends LuaJavaApiFunction {
    private final BiFunction<LuaVM_RT, LuaObject[], LuaObject[]> backing;
    private final int argCount;
    private final boolean hasVararg;

    private AtomicLuaFunction(ApiFunctionRegistry registry, BiFunction<LuaVM_RT, LuaObject[], LuaObject[]> backing, int argCount, boolean hasVararg) {
        super(registry, Singletons.EMPTY_LUA_OBJ_ARRAY, Singletons.EMPTY_LUA_OBJ_ARRAY);
        this.backing = backing;
        this.argCount = argCount;
        this.hasVararg = hasVararg;
    }


    // helpers for lua functions of type F(a); F(a,b); F(a,b,...)
    public static AtomicLuaFunction forZeroResults(ApiFunctionRegistry registry, LLConsumer c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            c.accept(vm, args[0]);
            var r = vm.isErroring() ? null : Singletons.EMPTY_LUA_OBJ_ARRAY;
            cco.isYieldable = prevYieldability;
            return r;
        }, 1, false);
    }

    public static AtomicLuaFunction forZeroResults(ApiFunctionRegistry registry, LLBiConsumer c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            c.accept(vm, args[0], args[1]);
            var r = vm.isErroring() ? null : Singletons.EMPTY_LUA_OBJ_ARRAY;
            cco.isYieldable = prevYieldability;
            return r;
        }, 2, false);
    }

    public static AtomicLuaFunction vaForZeroResults(ApiFunctionRegistry registry, LLVaVoidFunction c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            c.accept(vm, args[0].asArray());
            var r = vm.isErroring() ? null : Singletons.EMPTY_LUA_OBJ_ARRAY;
            cco.isYieldable = prevYieldability;
            return r;
        }, 1, true);
    }
    // ... more if necessary

    // helpers for lua functions of type F(a)->r; F(a,b)->r; F(a,b,...)->r
    public static AtomicLuaFunction forOneResult(ApiFunctionRegistry registry, LLSupplier c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var iRes = c.apply(vm);
            var r = iRes == null ? null : new LuaObject[]{iRes};
            cco.isYieldable = prevYieldability;
            return r;
        }, 0, false);
    }

    public static AtomicLuaFunction forOneResult(ApiFunctionRegistry registry, LLFunction c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var iRes = c.apply(vm, args[0]);
            var r = iRes == null ? null : new LuaObject[]{iRes};
            cco.isYieldable = prevYieldability;
            return r;
        }, 1, false);
    }

    public static AtomicLuaFunction forOneResult(ApiFunctionRegistry registry, LLBiFunction c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var iRes = c.apply(vm, args[0], args[1]);
            var r = iRes == null ? null : new LuaObject[]{iRes};
            cco.isYieldable = prevYieldability;
            return r;
        }, 2, false);
    }

    public static AtomicLuaFunction forOneResult(ApiFunctionRegistry registry, LLTriFunction c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var iRes = c.apply(vm, args[0], args[1], args[2]);
            var r = iRes == null ? null : new LuaObject[]{iRes};
            cco.isYieldable = prevYieldability;
            return r;
        }, 3, false);
    }

    public static AtomicLuaFunction vaForOneResult(ApiFunctionRegistry registry, LLVaFunction c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var iRes = c.apply(vm, args[0].asArray());
            var r = iRes == null ? null : new LuaObject[]{iRes};
            cco.isYieldable = prevYieldability;
            return r;
        }, 1, true);
    }
    // ... more if necessary

    // helpers for lua functions of type F(a)->r[]; F(a,b)->r[]; F(a,b,...)->r[]
    public static AtomicLuaFunction forManyResults(ApiFunctionRegistry registry, LLMultiSupplier c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var r = c.apply(vm);
            cco.isYieldable = prevYieldability;
            return r;
        }, 0, false);
    }

    public static AtomicLuaFunction forManyResults(ApiFunctionRegistry registry, LLMultiFunction c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var r = c.apply(vm, args[0]);
            cco.isYieldable = prevYieldability;
            return r;
        }, 1, false);
    }

    public static AtomicLuaFunction forManyResults(ApiFunctionRegistry registry, LLBiMultiFunction c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var r = c.apply(vm, args[0], args[1]);
            cco.isYieldable = prevYieldability;
            return r;
        }, 2, false);
    }

    public static AtomicLuaFunction vaForManyResults(ApiFunctionRegistry registry, LLVaMultiFunction c) {
        return new AtomicLuaFunction(registry, (vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var r = c.apply(vm, args[0].asArray());
            cco.isYieldable = prevYieldability;
            return r;
        }, 1, true);
    }
    // ... more if necessary

    public static AtomicLuaFunction unimplementedFunction(ApiFunctionRegistry registry, String name) {
        return AtomicLuaFunction.forManyResults(registry, vm -> {
            vm.error(LuaObject.of("UNIMPLEMENTED FUNCTION '%s'".formatted(name)));
            return null;
        });
    }

    @Override
    public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        vm.registerLocals(argCount);
        LuaObject[] ires = backing.apply(vm, stackFrame);
        if (ires == null) {
            assert vm.isErroring() : "awkward null return value without error of atomic lua lambda";
        } else if (vm.isErroring()) {
            // only reachable after error in a atomic consumer where the empty array singleton is always returned
            assert ires == Singletons.EMPTY_LUA_OBJ_ARRAY;
        } else {
            vm.returnValue(ires);
        }
    }

    @Override
    public int getMaxLocalsSize() {
        return argCount;
    }

    @Override
    public int getArgCount() {
        return argCount;
    }

    @Override
    public boolean hasParamsArg() {
        return hasVararg;
    }

    public LuaObject obj() {
        return LuaObject.of(this);
    }
}

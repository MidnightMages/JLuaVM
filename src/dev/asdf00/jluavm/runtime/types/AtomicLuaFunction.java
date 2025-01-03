package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.api.lambdas.*;
import dev.asdf00.jluavm.internals.Coroutine;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@SuppressWarnings("unused")
public final class AtomicLuaFunction extends LuaFunction {
    private final BiFunction<LuaVM_RT, LuaObject[], LuaObject[]> backing;
    private final int argCount;
    private final boolean hasVararg;

    private AtomicLuaFunction(BiFunction<LuaVM_RT, LuaObject[], LuaObject[]> backing, int argCount, boolean hasVararg) {
        super(Singletons.EMPTY_LUA_OBJ_ARRAY, Singletons.EMPTY_LUA_OBJ_ARRAY);
        this.backing = backing;
        this.argCount = argCount;
        this.hasVararg = hasVararg;
    }


    // helpers for lua functions of type F(a); F(a,b); F(a,b,...)
    public static AtomicLuaFunction forZeroResults(LLConsumer c) {
        return new AtomicLuaFunction((vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            c.accept(vm, args[0]);
            var r = vm.isErroring() ? null : Singletons.EMPTY_LUA_OBJ_ARRAY;
            cco.isYieldable = prevYieldability;
            return r;
        }, 1, false);
    }

    public static AtomicLuaFunction forZeroResults(LLBiConsumer c) {
        return new AtomicLuaFunction((vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            c.accept(vm, args[0], args[1]);
            var r = vm.isErroring() ? null : Singletons.EMPTY_LUA_OBJ_ARRAY;
            cco.isYieldable = prevYieldability;
            return r;
        }, 2, false);
    }

    public static AtomicLuaFunction vaForZeroResults(LLVaVoidFunction c) {
        return new AtomicLuaFunction((vm, args) -> {
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
    public static AtomicLuaFunction forOneResult(LLSupplier c) {
        return new AtomicLuaFunction((vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var iRes = c.apply(vm);
            var r = iRes == null ? null : new LuaObject[]{iRes};
            cco.isYieldable = prevYieldability;
            return r;
        }, 0, false);
    }

    public static AtomicLuaFunction forOneResult(LLFunction c) {
        return new AtomicLuaFunction((vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var iRes = c.apply(vm, args[0]);
            var r = iRes == null ? null : new LuaObject[]{iRes};
            cco.isYieldable = prevYieldability;
            return r;
        }, 1, false);
    }

    public static AtomicLuaFunction forOneResult(LLBiFunction c) {
        return new AtomicLuaFunction((vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var iRes = c.apply(vm, args[0], args[1]);
            var r = iRes == null ? null : new LuaObject[]{iRes};
            cco.isYieldable = prevYieldability;
            return r;
        }, 2, false);
    }

    public static AtomicLuaFunction forOneResult(LLTriFunction c) {
        return new AtomicLuaFunction((vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var iRes = c.apply(vm, args[0], args[1], args[2]);
            var r = iRes == null ? null : new LuaObject[]{iRes};
            cco.isYieldable = prevYieldability;
            return r;
        }, 3, false);
    }

    public static AtomicLuaFunction vaForOneResult(LLVaFunction c) {
        return new AtomicLuaFunction((vm, args) -> {
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
    public static AtomicLuaFunction forManyResults(LLMultiSupplier c) {
        return new AtomicLuaFunction((vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var r = c.apply(vm);
            cco.isYieldable = prevYieldability;
            return r;
        }, 0, false);
    }

    public static AtomicLuaFunction forManyResults(LLMultiFunction c) {
        return new AtomicLuaFunction((vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var r = c.apply(vm, args[0]);
            cco.isYieldable = prevYieldability;
            return r;
        }, 1, false);
    }

    public static AtomicLuaFunction forManyResults(LLBiMultiFunction c) {
        return new AtomicLuaFunction((vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var r = c.apply(vm, args[0], args[1]);
            cco.isYieldable = prevYieldability;
            return r;
        }, 2, false);
    }

    public static AtomicLuaFunction vaForManyResults(LLVaMultiFunction c) {
        return new AtomicLuaFunction((vm, args) -> {
            Coroutine cco = vm.getCurrentCoroutine();
            boolean prevYieldability = cco.isYieldable;
            cco.isYieldable = false;
            var r = c.apply(vm, args[0].asArray());
            cco.isYieldable = prevYieldability;
            return r;
        }, 1, true);
    }
    // ... more if necessary

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

    @Override
    public byte[] serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs) {
        throw new UnsupportedOperationException("unimplemented");
    }

    public LuaObject obj() {
        return LuaObject.of(this);
    }
}

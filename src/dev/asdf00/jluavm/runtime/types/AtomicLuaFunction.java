package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.api.lambdas.*;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AtomicLuaFunction extends LuaFunction {
    private final int argCount;
    private final BiFunction<LuaVM_RT, LuaObject[], LuaObject[]> backing;
    private final boolean hasVararg;

    public AtomicLuaFunction(BiFunction<LuaVM_RT, LuaObject[], LuaObject[]> c, int nonVaArgCount, boolean hasVararg) {
        super(Singletons.EMPTY_LUA_OBJ_ARRAY);
        backing = c;
        this.hasVararg = hasVararg;
        argCount = nonVaArgCount + (hasVararg ? 1 : 0);
    }

    public AtomicLuaFunction(BiConsumer<LuaVM_RT, LuaObject[]> c, int nonVaArgCount, boolean hasVararg) {
        this((vm, args) -> {
            c.accept(vm,args);
            return Singletons.EMPTY_LUA_OBJ_ARRAY;
        }, nonVaArgCount, hasVararg);
    }

    public AtomicLuaFunction(Function<LuaVM_RT, LuaObject[]> c, int nonVaArgCount, boolean hasVararg) {
        this((BiFunction<LuaVM_RT, LuaObject[], LuaObject[]>) (vm, args) -> c.apply(vm), nonVaArgCount, hasVararg);
    }

    // helpers for lua functions of type F(a); F(a,b); F(a,b,...)
    public static AtomicLuaFunction forZeroResults(LLConsumer c) {
        return new AtomicLuaFunction((BiConsumer<LuaVM_RT, LuaObject[]>) (vm, args) -> c.accept(vm, args[0]), 1, false);
    }

    public static AtomicLuaFunction forZeroResults(LLBiConsumer c) {
        return new AtomicLuaFunction((BiConsumer<LuaVM_RT, LuaObject[]>) (vm, args) -> c.accept(vm, args[0], args[1]), 2, false);
    }
    // ... more if necessary

    // helpers for lua functions of type F(a)->r; F(a,b)->r; F(a,b,...)->r
    public static AtomicLuaFunction forOneResult(LLFunction c) {
        return new AtomicLuaFunction((vm, args) -> new LuaObject[]{c.apply(vm, args[0])}, 1, false);
    }

    public static AtomicLuaFunction forOneResult(LLBiFunction c) {
        return new AtomicLuaFunction((vm, args) -> new LuaObject[]{c.apply(vm, args[0], args[1])}, 2, false);
    }

    public static AtomicLuaFunction forOneResult(LLTriFunction c) {
        return new AtomicLuaFunction((vm, args) -> new LuaObject[]{c.apply(vm, args[0], args[1], args[2])}, 3, false);
    }
    // ... more if necessary

    // helpers for lua functions of type F(a)->r[]; F(a,b)->r[]; F(a,b,...)->r[]
    public static AtomicLuaFunction forManyResults(LLMultiFunction c) {
        return new AtomicLuaFunction((BiFunction<LuaVM_RT, LuaObject[], LuaObject[]>) (vm, args) -> c.apply(vm, args[0]), 1, false);
    }

    public static AtomicLuaFunction forManyResults(LLBiMultiFunction c) {
        return new AtomicLuaFunction((BiFunction<LuaVM_RT, LuaObject[], LuaObject[]>) (vm, args) -> c.apply(vm, args[0], args[1]), 2, false);
    }

    public static AtomicLuaFunction vaForManyResults(LLVaMultiFunction c) {
        return new AtomicLuaFunction(c::apply, 0, true);
    }
    // ... more if necessary

    @Override
    public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        vm.returnValue(backing.apply(vm,stackFrame));
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

    public LuaObject obj(){
        return LuaObject.of(this);
    }
}

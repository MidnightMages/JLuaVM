package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.function.*;

public class AtomicLuaFunction extends LuaFunction {
    private final int argCount = 0;
    private final BiFunction<LuaVM_RT, LuaObject[], LuaObject[]> backing;

    public AtomicLuaFunction(BiConsumer<LuaVM_RT, LuaObject[]> c) {
        super(Singletons.EMPTY_LUA_OBJ_ARRAY);
        backing = (vm,args) -> {
            c.accept(vm,args);
            return Singletons.EMPTY_LUA_OBJ_ARRAY;
        };
    }

    public AtomicLuaFunction(BiFunction<LuaVM_RT, LuaObject[], LuaObject[]> c) {
        super(Singletons.EMPTY_LUA_OBJ_ARRAY);
        backing = c;
    }

    public AtomicLuaFunction(Function<LuaVM_RT, LuaObject[]> c) {
        super(Singletons.EMPTY_LUA_OBJ_ARRAY);
        backing = (vm, args) -> c.apply(vm);
    }

    // helpers for lua functions of type F(a); F(a,b); F(a,b,...)
    public static AtomicLuaFunction forZeroResults(BiConsumer<LuaVM_RT,LuaObject> c) {
        return new AtomicLuaFunction((BiConsumer<LuaVM_RT, LuaObject[]>) (vm, args) -> c.accept(vm, args[0]));
    }

//    public static AtomicLuaFunction forZeroResults(TriConsumer<LuaVM_RT,LuaObject, LuaObject> c) {
//        return new AtomicLuaFunction((BiConsumer<LuaVM_RT, LuaObject[]>) (vm, args) -> c.accept(vm, args[0], args[1]));
//    }
    // ... more if necessary

    // helpers for lua functions of type F(a)->r; F(a,b)->r; F(a,b,...)->r
    public static AtomicLuaFunction forOneResult(BiFunction<LuaVM_RT,LuaObject, LuaObject> c) {
        return new AtomicLuaFunction((vm,args) -> new LuaObject[]{c.apply(vm,args[0])});
    }

//    public static AtomicLuaFunction forOneResult(TriFunction<LuaVM_RT, LuaObject, LuaObject, LuaObject> c) {
//        return new AtomicLuaFunction((vm,args) -> new LuaObject[]{c.apply(vm,args[0], args[1])});
//    }
    // ... more if necessary

    // helpers for lua functions of type F(a)->r[]; F(a,b)->r[]; F(a,b,...)->r[]
    public static AtomicLuaFunction forManyResults(BiFunction<LuaVM_RT, LuaObject, LuaObject[]> c) {
        return new AtomicLuaFunction((BiFunction<LuaVM_RT, LuaObject[], LuaObject[]>) (vm, args) -> c.apply(vm, args[0]));
    }

//    public static AtomicLuaFunction forManyResults(TriFunction<LuaVM_RT, LuaObject, LuaObject, LuaObject[]> c) {
//        return new AtomicLuaFunction((TriFunction<LuaVM_RT,LuaObject[], LuaObject[]>) (vm, args) -> c.apply(vm, args[0], args[1]));
//    }
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
        return false;
    }

    public LuaObject obj(){
        return LuaObject.of(this);
    }
}

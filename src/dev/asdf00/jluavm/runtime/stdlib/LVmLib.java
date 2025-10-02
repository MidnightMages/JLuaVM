package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.api.functions.AtomicLuaFunction;
import dev.asdf00.jluavm.api.functions.MixedStateFunctionRegistry;
import dev.asdf00.jluavm.api.userdata.LuaUserData;
import dev.asdf00.jluavm.exceptions.LuaJavaError;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Arrays;

public class LVmLib {

    private static final String VM_LIB_PREFIX = "vm.";

    public static void registerStdVm(MixedStateFunctionRegistry registry) {
        registry.register(VM_LIB_PREFIX + "pause",
                AtomicLuaFunction.forZeroResults(registry, LuaVM::requestStop));

        registry.register(VM_LIB_PREFIX + "listReadableUDKeys",
                AtomicLuaFunction.forOneResult(registry, (vm, userDataObj) -> {
                    if (!userDataObj.isUserData())
                        throw new LuaJavaError("Second argument must be of type userdata but was %s.".formatted(userDataObj.getTypeAsString()));

                    //noinspection unchecked
                    var udClass = (Class<? extends LuaUserData>) userDataObj.refVal.getClass();
                    var readableKeys = LuaVM_RT.getDescriptor(udClass).getReadableKeys();
                    return LuaObject.tableFromArray(Arrays.stream(readableKeys).map(LuaObject::of).toArray(LuaObject[]::new));
                }));

        registry.register(VM_LIB_PREFIX + "listWritableUDKeys",
                AtomicLuaFunction.forOneResult(registry, (vm, userDataObj) -> {
                    if (!userDataObj.isUserData())
                        throw new LuaJavaError("Second argument must be of type userdata but was %s.".formatted(userDataObj.getTypeAsString()));

                    //noinspection unchecked
                    var udClass = (Class<? extends LuaUserData>) userDataObj.refVal.getClass();
                    var readableKeys = LuaVM_RT.getDescriptor(udClass).getWritableKeys();
                    return LuaObject.tableFromArray(Arrays.stream(readableKeys).map(LuaObject::of).toArray(LuaObject[]::new));
                }));
    }
}

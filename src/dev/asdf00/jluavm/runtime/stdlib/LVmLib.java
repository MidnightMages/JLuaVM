package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.api.functions.AtomicLuaFunction;
import dev.asdf00.jluavm.api.functions.MixedStateFunctionRegistry;
import dev.asdf00.jluavm.api.userdata.LuaUserData;
import dev.asdf00.jluavm.exceptions.LuaJavaError;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

public class LVmLib {

    private static final String VM_LIB_PREFIX = "vm.";

    public static void registerStdVm(MixedStateFunctionRegistry registry) {
        registry.register(VM_LIB_PREFIX + "pause",
                AtomicLuaFunction.forZeroResults(registry, LuaVM::requestStop));

        registry.register(VM_LIB_PREFIX + "listUDKeys",
                AtomicLuaFunction.forOneResult(registry, (vm, userDataObj) -> {
                    if (!userDataObj.isUserData())
                        throw new LuaJavaError("Argument must be of type userdata but was %s.".formatted(userDataObj.getTypeAsString()));

                    var udObject = (LuaUserData) userDataObj.refVal;
                    var udClass = udObject.getClass();
                    var readableKeys = distinctArrayConcat(
                            LuaVM_RT.getDescriptor(udClass).getReadableKeys(),
                            udObject.getExtraReadableUdKeys()
                    );
                    var writableKeys = distinctArrayConcat(
                            LuaVM_RT.getDescriptor(udClass).getWritableKeys(),
                            udObject.getExtraWritableUdKeys()
                    );
                    var rv = Arrays.stream(readableKeys).collect(Collectors.toMap(x -> x, x -> "r"));
                    for (var item : writableKeys)
                        rv.compute(item, (k, v) -> v == null ? "w" : "rw");
                    var lrv = LuaObject.table();
                    for (var key : rv.keySet())
                        lrv.set(key, LuaObject.of(rv.get(key)));

                    return lrv;
                }));
    }

    // joins two arrays, keeping order stable but omitting any additional duplicate entries.
    // Duplicates in collection1 are _not_ removed as there shouldnt be any
    private static String[] distinctArrayConcat(String[] collection1, String[] collection2) {
        var rv = new ArrayList<>(Arrays.asList(collection1)); // we take all elements of collection1
        var seen = new HashSet<>(rv);
        for (var candidate : collection2) {
            if (seen.add(candidate)) { // and then we add all the ones that we havent seen yet
                rv.add(candidate);
            }
        }
        return rv.toArray(String[]::new);
    }
}

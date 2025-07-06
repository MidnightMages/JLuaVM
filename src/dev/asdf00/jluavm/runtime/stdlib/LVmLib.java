package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.api.functions.AtomicLuaFunction;
import dev.asdf00.jluavm.api.functions.MixedStateFunctionRegistry;

public class LVmLib {

    private static final String VM_LIB_PREFIX = "vm.";

    public static void registerStdVm(MixedStateFunctionRegistry registry) {
        registry.register(VM_LIB_PREFIX + "pause",
                AtomicLuaFunction.forZeroResults(registry, vm -> vm.requestStop()));
    }
}

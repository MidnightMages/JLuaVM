package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.api.functions.AtomicLuaFunction;
import dev.asdf00.jluavm.api.functions.MixedStateFunctionRegistry;
import dev.asdf00.jluavm.runtime.types.LuaObject;

public class LDebug {
    private static final String MATH_PREFIX = "debug.";


    public static void registerStdDebug(MixedStateFunctionRegistry registry, boolean includeUnconstrainedFunctions) {

        // we skip the function frame of the traceback function
        registry.register(MATH_PREFIX + "traceback",
                AtomicLuaFunction.forOneResult(registry, (vm) -> LuaObject.of(vm.printStacktrace(1))));

        if (!includeUnconstrainedFunctions) {
            return;
        }

        // DANGEROUS FUNCTIONS STARTING HERE!!!

    }
}

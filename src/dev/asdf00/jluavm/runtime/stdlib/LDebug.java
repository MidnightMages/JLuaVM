package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.api.functions.AtomicLuaFunction;
import dev.asdf00.jluavm.api.functions.MixedStateFunctionRegistry;
import dev.asdf00.jluavm.internals.Coroutine;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.RTUtils;

public class LDebug {
    private static final String MATH_PREFIX = "debug.";


    public static void registerStdDebug(MixedStateFunctionRegistry registry, boolean includeUnconstrainedFunctions) {

        // we skip the function frame of the traceback function
        registry.register(MATH_PREFIX + "traceback",
                AtomicLuaFunction.vaForOneResult(registry, (vm, args) -> {
                    Coroutine thread = null;
                    LuaObject message = LuaObject.NIL;
                    int level;

                    int argStartIdx = 0;
                    if (args.length > 0 && args[0].isThread()) {
                        thread = args[0].asCoroutine();
                        argStartIdx++;
                    }
                    if (argStartIdx < args.length) {
                        // nil number and string are valid, rest will cause an immediate return after args are processed
                        message = args[argStartIdx];
                        argStartIdx++;
                    }

                    if (!message.isType(LuaObject.Types.NIL | LuaObject.Types.NUMBER | LuaObject.Types.STRING)) {
                        return message;
                    }

                    // use default level value 1 for printing stacktrace of current coroutine; or 0 for other coroutine
                    var defaultLevel = thread == null ? 1 : 0;
                    if (argStartIdx < args.length) {
                        var lArg = RTUtils.checkPositionalArgError(vm, args, "debug.traceback", argStartIdx, x -> x.isNil() || x.isIntCoercible(),
                                LuaObject.NIL, new String[]{"integer", "nil"});
                        if (lArg == null) return null;

                        if (lArg.isNil()) {
                            level = defaultLevel;
                        } else {
                            level = (int) (lArg.asLong() & 0x0000_0000_8FFF_FFFFL); // mask off signbit
                        }
                        argStartIdx++;
                    } else {
                        level = defaultLevel;
                    }


                    var stacktrace = LuaVM_RT.getStacktrace((thread != null ? thread : vm.getCurrentCoroutine()).luaCallStack, level);
                    return LuaObject.of((message.isNil() ? "" : message.asString()+"\n") + stacktrace);
                }));

        if (!includeUnconstrainedFunctions) {
        }

        // DANGEROUS FUNCTIONS STARTING HERE!!!

    }
}

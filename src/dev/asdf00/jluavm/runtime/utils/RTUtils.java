package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Arrays;
import java.util.function.Function;

public class RTUtils {
    public static LuaObject tryCoerceFloatToInt(LuaObject value) {
        if (value == null) {
            // pass null for table construction
            return null;
        }
        if (value.isDouble()) {
            double dn = value.dVal;
            if ((double) ((long) dn) == dn) {
                value = LuaObject.of((long) dn);
            }
        }
        return value;
    }

    public static boolean isTruthy(LuaObject value) {
        return !value.isBoolean() && !value.isNil() || value.isBoolean() && value.getBool();
    }

    public static LuaObject argTypeError(int ardIdx, String expectedType, LuaObject actual) {
        return LuaObject.of("Expected argument #%s to be of type '%s', but it was '%s'!".formatted(
                ardIdx + 1, expectedType, actual == null ? "nothing" : actual.getTypeAsString()));
    }

    public static LuaObject funcArgTypeError(String funcName, int argIdx, LuaObject actual, String expectedType) {
        return LuaObject.of("In '%s': Expected argument #%s to be of type '%s', but it was '%s'!".formatted(
                funcName, argIdx + 1, expectedType, actual == null ? "nothing" : actual.getTypeAsString()));
    }

    public static LuaObject funcArgAnyTypeError(String funcName, int argIdx, LuaObject actual, String... expectedTypes) {
        return LuaObject.of("In '%s': Expected argument #%s to be any type of %s, but it was '%s'!".formatted(
                funcName, argIdx + 1, Arrays.toString(expectedTypes), actual == null ? "nothing" : actual.getTypeAsString()));
    }

    public static LuaObject funcBadArgError(String funcName, int argIdx, String reason) {
        return LuaObject.of("In '%s': Bad argument #%s (%s)".formatted(funcName, argIdx + 1, reason));
    }

    public static LuaObject checkPositionalArgError(LuaVM_RT vm, LuaObject[] args, String funcName, int argIdx, Function<LuaObject, Boolean> isArgValid,
                                                    LuaObject defaultValueOrThrow, String[] expectedTypes) {
        var arg = args.length < argIdx + 1 ? defaultValueOrThrow : args[argIdx];
        if (arg == null || !isArgValid.apply(arg)) {
            vm.error(funcArgAnyTypeError(funcName, argIdx, arg, expectedTypes));
            return null;
        }
        return arg;
    }

    public static LuaObject checkPositionalArgError(LuaVM_RT vm, LuaObject[] args, String funcName, int argIdx, Function<LuaObject, Boolean> isArgValid,
                                                    LuaObject defaultValueOrThrow, String expectedType) {
        return checkPositionalArgError(vm, args, funcName, argIdx, isArgValid, defaultValueOrThrow, new String[]{expectedType});
    }

    public static LuaObject checkPositionalArgError(LuaVM_RT vm, LuaObject[] args, String funcName, int argIdx, Function<LuaObject, Boolean> isArgValid,
                                                    String[] expectedTypes) {
        return checkPositionalArgError(vm, args, funcName, argIdx, isArgValid, null, expectedTypes);
    }

    public static LuaObject checkPositionalArgError(LuaVM_RT vm, LuaObject[] args, String funcName, int argIdx, Function<LuaObject, Boolean> isArgValid,
                                                    String expectedType) {
        return checkPositionalArgError(vm, args, funcName, argIdx, isArgValid, null, new String[]{expectedType});
    }
}

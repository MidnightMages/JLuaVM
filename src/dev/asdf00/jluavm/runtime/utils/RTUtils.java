package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.runtime.types.LuaObject;

public class RTUtils {
    public static LuaObject tryCoerceFloatToInt(LuaObject value) {
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
}

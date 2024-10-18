package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.runtime.types.ILuaVariable;
import dev.asdf00.jluavm.runtime.types.LuaDouble;
import dev.asdf00.jluavm.runtime.types.LuaLong;
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

    public static ILuaVariable[] pack() {
        return Singletons.EMPTY_VARS;
    }

    public static ILuaVariable[] pack(ILuaVariable... p) {
        return p;
    }
}

package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.runtime.types.ILuaVariable;
import dev.asdf00.jluavm.runtime.types.LuaDouble;
import dev.asdf00.jluavm.runtime.types.LuaLong;

public class RTUtils {
    public static ILuaVariable tryCoerceFloatToInt(ILuaVariable value) {
        if (value instanceof LuaDouble dbl) {
            double dn = dbl.value;
            if ((double) ((long) dn) == dn) {
                value = LuaLong.of((long) dn);
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

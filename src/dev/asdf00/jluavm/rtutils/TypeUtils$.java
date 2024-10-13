package dev.asdf00.jluavm.rtutils;

import dev.asdf00.jluavm.exceptions.runtime.LuaTypeError$;
import dev.asdf00.jluavm.internals.LuaVM_RT$;
import dev.asdf00.jluavm.types.*;

public class TypeUtils$ {
    public static <T extends LuaVariable$> T asType(LuaVM_RT$ vmHandle, Class<T> type, LuaVariable$ value) {
        if (value.getType() == LuaVariable$.LuaType.fromClass(type)) {
            return (T) value;
        }
        throw vmHandle.yeet(new LuaTypeError$("expected '%s', got '%s'".formatted(LuaVariable$.LuaType.fromClass(type).fancyName, value.getType().fancyName)));
    }

    public static LuaVariable$ resolveResult(LuaVariable$[] result) {
        if (result.length < 1) {
            return LuaNil$.singleton;
        }
        return result[1];
    }

    public static ILuaIndexable$ asIndexable(LuaVM_RT$ vmHandle, LuaVariable$ value) {
        if (value instanceof LuaTable$ tbl) {
            return tbl;
        }
        throw vmHandle.yeet(new LuaTypeError$("expected '%s' to be an indexable value".formatted(value.getType().fancyName)));
    }
}

package dev.asdf00.jluavm.rtutils;

import dev.asdf00.jluavm.exceptions.runtime.LuaTypeError$;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.typesOLD.ILuaIndexableOLD;
import dev.asdf00.jluavm.runtime.typesOLD.LuaNilOLD;
import dev.asdf00.jluavm.runtime.typesOLD.LuaTableOLD;
import dev.asdf00.jluavm.runtime.typesOLD.LuaVariableOLD;
import dev.asdf00.jluavm.types.*;

public class TypeUtils$ {
    public static <T extends LuaVariableOLD> T asType(LuaVM_RT vmHandle, Class<T> type, LuaVariableOLD value) {
        if (value.getType() == LuaVariableOLD.LuaType.fromClass(type)) {
            return (T) value;
        }
        throw vmHandle.yeet(new LuaTypeError$("expected '%s', got '%s'".formatted(LuaVariableOLD.LuaType.fromClass(type).fancyName, value.getType().fancyName)));
    }

    public static LuaVariableOLD resolveResult(LuaVariableOLD[] result) {
        if (result.length < 1) {
            return LuaNilOLD.singleton;
        }
        return result[1];
    }

    public static ILuaIndexableOLD asIndexable(LuaVM_RT vmHandle, LuaVariableOLD value) {
        if (value instanceof LuaTableOLD tbl) {
            return tbl;
        }
        throw vmHandle.yeet(new LuaTypeError$("expected '%s' to be an indexable value".formatted(value.getType().fancyName)));
    }
}

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
}

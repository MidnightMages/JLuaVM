package dev.asdf00.jluavm.rtutils;

import dev.asdf00.jluavm.exceptions.runtime.LuaTypeError;
import dev.asdf00.jluavm.types.LuaVariable$;

public class LuaTypeChecker$ {

    public static <T extends LuaVariable$> T check(LuaVariable$ val, Class<T> type) {
        if (type.isAssignableFrom(val.getClass())) {
            return (T) val;
        } else {
            // TODO: add msg
            throw new LuaTypeError(null);
        }
    }


}

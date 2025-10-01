package dev.asdf00.jluavm.api.userdata;

import dev.asdf00.jluavm.exceptions.LuaJavaError;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.List;
import java.util.Map;

public interface LuaUserData {
    /**
     * If the key is not a string, or if the key has not been already found in the set of methods, fields and properties
     * exposed to LUA for the given type, this getter is called as a fallback. If this method returns {@code null}, the
     * LUA VM interprets this as "key not found" and triggers a meta call to {@code __index}.
     * <p/>
     * If for example a type error is triggered, or reading from this key is not permitted, the user may throw a
     * {@link LuaJavaError}, which the LUA VM wraps into a standard LUA error.
     */
    default LuaObject luaGeneralGet(LuaObject key) throws LuaJavaError {
        return null;
    }

    /**
     * If the key is not a string, or if the key has not been already found in the set of methods, fields and properties
     * exposed to LUA for the given type, this setter is called as a fallback. The return value indicates if the given
     * key was found. If {@code false} is returned, a meta call to {@code __newindex} is returned.
     * <p/>
     * If for example a type error is triggered, or writing to this key is not permitted, the user may throw a
     * {@link LuaJavaError}, which the LUA VM wraps into a standard LUA error.
     */
    default boolean luaGeneralSet(LuaObject key, LuaObject value) throws LuaJavaError {
        return false;
    }

    /**
     * This method is called when the LUA VM attempts to serialize its state. The input parameters are meant to be
     * <b>READ-ONLY</b>! These parameters are only passed to allow this method to serialize inner LuaObjects.
     *
     * @return a non-null serial byte[] representation of this userdata object.
     */
    byte[] luaSerialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs);
}

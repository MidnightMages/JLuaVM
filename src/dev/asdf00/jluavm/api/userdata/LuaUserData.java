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
     * This guard method is called each time a name of this object is accessed by LUA. This could be a SET operation, in
     * which case the name of the target field/property is passed as an argument as well as the value which will be
     * assigned. On the other hand, this could be a GET operation requesting the value of a field/property or a method
     * wrapped into a LUA function, in which case {@code null} is passed as the second parameter.
     *
     * @param key   name of the affected field/property/method
     * @param value {@code null} for GET operations, the value to be set for SET operations
     * @return {@code true} if LUA is allowed to access this field/property/method
     */
    default boolean luaFieldGuard(LuaObject key, LuaObject value) {
        return true;
    }

    /**
     * This guard method is called each time a method wrapped into a LUA function is called. Since this might be
     * significantly later than the GET access retrieving the LUA function, another check is done here. The function
     * name as well as the arguments passed to the function (including {@code this} as the first argument) are passed
     * to this guard. Changing any arguments other than the first one will change what is passed to the method invoked
     * after this guard.
     *
     * @param name      name of the method to be called
     * @param arguments arguments passed to the LUA function (including {@code this} as the first argument)
     * @return {@code true} if LUA is allowed to call this method with these parameters
     */
    default boolean luaCallGuard(String name, LuaObject[] arguments) {
        return true;
    }

    /**
     * To ensure equality for USERDATA objects in the LUA VM, a userdata object may provide a back reference to its own
     * LUA object. This method is queried on {@link LuaObject#of(LuaUserData)}, to possibly provide its identity. If no
     * LUA object is returned here, a new one is created and {@link LuaUserData#setSelfAsLuaObject(LuaObject)} is called
     * with the newly created identity.
     *
     * @return itself as a LuaObject or {@code null}.
     */
    default LuaObject getSelfAsLuaObject() {
        return null;
    }

    /**
     * On creation of a new LUA object for this userdata object, the new object is passed to this method. If equality is
     * important, please store this value to be returned on the next call to {@link LuaUserData#getSelfAsLuaObject()}.
     *
     * @param self its own LUA object.
     */
    default void setSelfAsLuaObject(LuaObject self) {
        // empty default implementation to make this method optional
    }

    /**
     * This method is called when the LUA VM attempts to serialize its state. The input parameters are meant to be
     * <b>READ-ONLY</b>! These parameters are only passed to allow this method to serialize inner LuaObjects.
     *
     * @return a non-null serial byte[] representation of this userdata object.
     */
    byte[] luaSerialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, Object additionalData);
}

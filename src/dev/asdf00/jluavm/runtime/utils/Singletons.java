package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.runtime.types.LuaObject;

public class Singletons {
    // basic meta functions
    public static final LuaObject __add = LuaObject.of("__add");
    public static final LuaObject __index = LuaObject.of("__index");
    public static final LuaObject __newindex = LuaObject.of("__newindex");

    // advanced meta function
    public static final LuaObject __close = LuaObject.of("__close");

    // other singletons
    public static final LuaObject[] EMPTY_LUA_OBJ_ARRAY = new LuaObject[0];
}

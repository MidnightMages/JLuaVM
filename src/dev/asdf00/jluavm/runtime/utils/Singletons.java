package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.runtime.types.LuaObject;

public class Singletons {
    public static final LuaObject emptyReturnValues = LuaObject.of(new LuaObject[0]);
    public static final LuaObject __add = LuaObject.of("__add");
    public static final LuaObject __index = LuaObject.of("__index");
    public static final LuaObject __newindex = LuaObject.of("__newindex");
}

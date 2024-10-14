package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.runtime.types.ILuaVariable;
import dev.asdf00.jluavm.runtime.types.LuaNil;
import dev.asdf00.jluavm.runtime.types.LuaString;

public class Singletons {
    public static final LuaNil NIL = new LuaNil();
    public static final ILuaVariable[] EMPTY_VARS = new ILuaVariable[0];
    public static class Meta {
        public static final LuaString __add = LuaString.of("__add");
        public static final LuaString __index = LuaString.of("__index");
        public static final LuaString __newindex = LuaString.of("__newindex");
    }
}

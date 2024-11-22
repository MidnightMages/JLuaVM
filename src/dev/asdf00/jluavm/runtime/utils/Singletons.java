package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.runtime.types.LuaObject;

public class Singletons {
    // meta functions
    public static final LuaObject __add = LuaObject.of("__add");
    public static final LuaObject __sub = LuaObject.of("__sub");
    public static final LuaObject __mul = LuaObject.of("__mul");
    public static final LuaObject __div = LuaObject.of("__div");
    public static final LuaObject __unm = LuaObject.of("__unm");
    public static final LuaObject __mod = LuaObject.of("__mod");
    public static final LuaObject __pow = LuaObject.of("__pow");
    public static final LuaObject __idiv = LuaObject.of("__idiv");
    public static final LuaObject __band = LuaObject.of("__band");
    public static final LuaObject __bor = LuaObject.of("__bor");
    public static final LuaObject __bxor = LuaObject.of("__bxor");
    public static final LuaObject __bnot = LuaObject.of("__bnot");
    public static final LuaObject __shl = LuaObject.of("__shl");
    public static final LuaObject __shr = LuaObject.of("__shr");
    public static final LuaObject __eq = LuaObject.of("__eq");
    public static final LuaObject __lt = LuaObject.of("__lt");
    public static final LuaObject __le = LuaObject.of("__le");
    public static final LuaObject __concat = LuaObject.of("__concat");
    public static final LuaObject __len = LuaObject.of("__len");
    public static final LuaObject __index = LuaObject.of("__index");
    public static final LuaObject __newindex = LuaObject.of("__newindex");
    public static final LuaObject __call = LuaObject.of("__call");
    public static final LuaObject __mode = LuaObject.of("__mode");
    public static final LuaObject __close = LuaObject.of("__close");
    public static final LuaObject __tostring = LuaObject.of("__tostring");

    public static final LuaObject __ipairs = LuaObject.of("__ipairs");
    public static final LuaObject __pairs = LuaObject.of("__pairs");

    // other singletons
    public static final LuaObject[] EMPTY_LUA_OBJ_ARRAY = new LuaObject[0];
}

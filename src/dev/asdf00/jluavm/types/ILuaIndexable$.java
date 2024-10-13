package dev.asdf00.jluavm.types;

import dev.asdf00.jluavm.internals.LuaVM_RT$;

public sealed interface ILuaIndexable$ permits LuaTable$ {

    LuaFunction$ _luaGetMtFunc(LuaVM_RT$ vmHandle, String funcName);

    /**
     * This method checks for metatable values
     */
    LuaVariable$ _luaGet(LuaVM_RT$ vmHandle, LuaVariable$ key);

    /**
     * This method checks for metatable values
     */
    void _luaGet(LuaVM_RT$ vmHandle, LuaVariable$ key, LuaVariable$ value);
}

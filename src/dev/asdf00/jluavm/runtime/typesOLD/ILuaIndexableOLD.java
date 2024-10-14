package dev.asdf00.jluavm.runtime.typesOLD;

import dev.asdf00.jluavm.internals.LuaVM_RT;

public sealed interface ILuaIndexableOLD permits LuaTableOLD {

    LuaFunctionOLD _luaGetMtFunc(LuaVM_RT vmHandle, String funcName);

    /**
     * This method checks for metatable values
     */
    LuaVariableOLD _luaGet(LuaVM_RT vmHandle, LuaVariableOLD key);

    /**
     * This method checks for metatable values
     */
    void _luaGet(LuaVM_RT vmHandle, LuaVariableOLD key, LuaVariableOLD value);
}

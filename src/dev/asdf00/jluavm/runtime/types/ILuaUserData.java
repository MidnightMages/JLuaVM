package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.exceptions.LuaRuntimeError$;

public interface ILuaUserData {
    ILuaVariable _luaGet(ILuaVariable key) throws LuaRuntimeError$;
    ILuaVariable _luaSet(ILuaVariable key, ILuaVariable value);
}

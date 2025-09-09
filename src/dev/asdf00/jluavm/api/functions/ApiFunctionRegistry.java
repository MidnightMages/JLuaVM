package dev.asdf00.jluavm.api.functions;

import dev.asdf00.jluavm.runtime.types.LuaJavaApiFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;

public abstract class ApiFunctionRegistry {
    public abstract String registryID();

    public abstract String getSerialName(LuaJavaApiFunction function);

    public LuaJavaApiFunction getFunction(String serialName) {
        return getFunction(serialName, LuaObject.box(LuaObject.nil()));
    }

    public LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV) {
        return getFunction(serialName, _ENV, Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV, LuaObject[] closures) {
        return getFunction(serialName, _ENV, closures, null);
    }

    public abstract LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV, LuaObject[] closures, byte[] additional);
}

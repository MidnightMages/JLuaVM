package dev.asdf00.jluavm.api.functions;

import dev.asdf00.jluavm.runtime.types.LuaJavaApiFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;

public interface ApiFunctionRegistry {
    String registryID();

    String getSerialName(LuaJavaApiFunction function);

    default LuaJavaApiFunction getFunction(String serialName) {
        return getFunction(serialName, LuaObject.box(LuaObject.nil()));
    }

    default LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV) {
        return getFunction(serialName, _ENV, Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    default LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV, LuaObject[] closures) {
        return getFunction(serialName, _ENV, closures, null);
    }

    LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV, LuaObject[] closures, byte[] additional);
}

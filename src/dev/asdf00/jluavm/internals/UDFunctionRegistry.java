package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.api.functions.ApiFunctionRegistry;
import dev.asdf00.jluavm.runtime.types.LuaJavaApiFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.concurrent.ConcurrentHashMap;

public class UDFunctionRegistry implements ApiFunctionRegistry {
    public static final ConcurrentHashMap<LuaJavaApiFunction, String> NAME_LOOKUP = new ConcurrentHashMap<>();

    @Override
    public String registryID() {
        return LuaVM.USERDATA_REG_ID;
    }

    @Override
    public String getSerialName(LuaJavaApiFunction function) {
        return NAME_LOOKUP.get(function);
    }

    @Override
    public LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV, LuaObject[] closures, byte[] additional) {
        int hashtagIdx = serialName.indexOf('#');
        assert hashtagIdx >= 0;
        String clName = serialName.substring(0, hashtagIdx);
        var desc = LuaVM_RT.getDescriptor(LuaVM_RT.getUserdataClass(clName));
        return desc.getFunc(serialName.substring(hashtagIdx + 1));
    }
}

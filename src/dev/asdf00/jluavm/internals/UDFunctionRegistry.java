package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.api.functions.ApiFunctionRegistry;
import dev.asdf00.jluavm.api.userdata.LuaUserData;
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
        /*
         * Here we could allow some class loading magic to find the correct instance of the class.
         */
        int hashtagIdx = serialName.indexOf('#');
        assert hashtagIdx >= 0;
        try {
            Class<?> clazz = getClass().getClassLoader().loadClass(serialName.substring(0, hashtagIdx));
            if (!LuaUserData.class.isAssignableFrom(clazz)) {
                throw new RuntimeException("Failed to load userdata class on deserialization, %s is no userdata".formatted(clazz.getName()));
            }
            @SuppressWarnings("unchecked")
            Class<? extends LuaUserData> udClass = (Class<? extends LuaUserData>) clazz;
            var desc = LuaVM_RT.getDescriptor(udClass);
            return desc.getFunc(serialName.substring(hashtagIdx + 1));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load userdata class on deserialization", e);
        }
    }
}

package dev.asdf00.jluavm.api;

import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.HashMap;
import java.util.Set;

public class StatelessFunctionRegistry extends ApiFunctionRegistry {
    protected final String id;
    protected final HashMap<LuaJavaApiFunction, String> reverseMap = new HashMap<>();
    protected final HashMap<String, LuaJavaApiFunction> funcMap = new HashMap<>();

    public StatelessFunctionRegistry(String id) {
        this.id = id;
    }

    @Override
    public String registryID() {
        return id;
    }

    @Override
    public String getSerialName(LuaJavaApiFunction function) {
        return reverseMap.get(function);
    }

    @Override
    public LuaJavaApiFunction getFunction(String serialName, LuaObject[] _ENV, LuaObject[] closures, byte[] additional) {
        if (funcMap.containsKey(serialName)) {
            return funcMap.get(serialName);
        } else {
            throw new IllegalArgumentException("Stateless function %s was not found");
        }
    }

    public void register(String name, LuaJavaApiFunction apiFunction) {
        funcMap.put(name, apiFunction);
        reverseMap.put(apiFunction, name);
    }

    public Set<String> getAllNames() {
        return funcMap.keySet();
    }
}

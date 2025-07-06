package dev.asdf00.jluavm.api.functions;

import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * This function registry supports both, stateless, and statefull lua functions. statefull lua functions shall have
 * their state inscribed into _ENV. Each time a statefull function is requested, its instanciator function that was
 * passed at registration time is executed to produce a new instance of the given function.
 *
 * During operation of the LuaVM, no API function should ever be instanciated by simply calling its constructor, but
 * rather by calling {@link ApiFunctionRegistry#getFunction} and, by proxy, calling the instanciator function.
 */
public class MixedStateFunctionRegistry extends ApiFunctionRegistry {
    protected final String id;
    protected final HashMap<LuaJavaApiFunction, String> lessReverseMap = new HashMap<>();
    protected final HashMap<String, LuaJavaApiFunction> lessFuncMap = new HashMap<>();
    protected final HashMap<Class<? extends LuaJavaApiFunction>, String> statefullReverseMap = new HashMap<>();
    protected final HashMap<String, Function<LuaObject, LuaJavaApiFunction>> statefullFuncMap = new HashMap<>();

    public MixedStateFunctionRegistry(String id) {
        this.id = id;
    }

    @Override
    public String registryID() {
        return id;
    }

    @Override
    public String getSerialName(LuaJavaApiFunction function) {
        var rv = lessReverseMap.get(function);
        return rv != null ? rv : statefullReverseMap.get(function.getClass());
    }

    @Override
    public LuaJavaApiFunction getFunction(String serialName) {
        if (lessFuncMap.containsKey(serialName)) {
            return lessFuncMap.get(serialName);
        } else {
            throw new IllegalArgumentException("Stateless function %s was not found in registry %s".formatted(serialName, id));
        }
    }

    @Override
    public LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV) {
        if (_ENV == null && lessFuncMap.containsKey(serialName)) {
            return getFunction(serialName);
        }
        if (statefullFuncMap.containsKey(serialName)) {
            return statefullFuncMap.get(serialName).apply(_ENV);
        } else {
            throw new IllegalArgumentException("Statefull function %s was not found in registry".formatted(serialName, id));
        }
    }

    @Override
    public LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV, LuaObject[] closures) {
        if (closures == null || closures.length == 0) {
            return getFunction(serialName, _ENV);
        } else {
            throw new UnsupportedOperationException("MixedStateFunctionRegistry does not support functions like %s with state outside of _ENV".formatted(serialName));
        }
    }

    @Override
    public LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV, LuaObject[] closures, byte[] additional) {
        if ((closures == null || closures.length == 0) && additional == null) {
            return getFunction(serialName, _ENV);
        } else {
            throw new UnsupportedOperationException("MixedStateFunctionRegistry does not support functions like %s with state outside of _ENV".formatted(serialName));
        }
    }

    public void register(String name, LuaJavaApiFunction apiFunction) {
        lessFuncMap.put(name, apiFunction);
        lessReverseMap.put(apiFunction, name);
    }

    public void register(String name, Class<? extends LuaJavaApiFunction> clazz, Function<LuaObject, LuaJavaApiFunction> instanciator) {
        statefullFuncMap.put(name, instanciator);
        statefullReverseMap.put(clazz, name);
    }

    public Set<String> getAllNames() {
        var names = new HashSet<>(lessFuncMap.keySet());
        names.addAll(statefullFuncMap.keySet());
        return Collections.unmodifiableSet(names);
    }
}

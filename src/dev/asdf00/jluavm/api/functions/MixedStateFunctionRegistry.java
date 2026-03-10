package dev.asdf00.jluavm.api.functions;

import dev.asdf00.jluavm.runtime.types.LuaJavaApiFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This function registry supports both, stateless, and stateful lua functions. stateful lua functions shall have
 * their state inscribed into _ENV. Each time a stateful function is requested, its instanciator function that was
 * passed at registration time is executed to produce a new instance of the given function.
 *
 * During operation of the LuaVM, no API function should ever be instanciated by simply calling its constructor, but
 * rather by calling {@link ApiFunctionRegistry#getFunction} and, by proxy, calling the instanciator function.
 */
public class MixedStateFunctionRegistry implements ApiFunctionRegistry {
    protected final String id;
    protected final HashMap<LuaJavaApiFunction, String> lessReverseMap = new HashMap<>();
    protected final HashMap<String, LuaJavaApiFunction> lessFuncMap = new HashMap<>();
    protected final HashMap<Class<? extends LuaJavaApiFunction>, String> statefulReverseMap = new HashMap<>();
    protected final HashMap<String, BiFunction<LuaObject, LuaObject[], LuaJavaApiFunction>> statefulFuncMap = new HashMap<>();

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
        return rv != null ? rv : statefulReverseMap.get(function.getClass());
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
        return getFunction(serialName, _ENV, null);
    }

    @Override
    public LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV, LuaObject[] closures) {
        if (_ENV == null && lessFuncMap.containsKey(serialName)) {
            return getFunction(serialName);
        }
        if (statefulFuncMap.containsKey(serialName)) {
            if (closures == null || closures.length == 0) {
                return statefulFuncMap.get(serialName).apply(_ENV, Singletons.EMPTY_LUA_OBJ_ARRAY);
            } else {
                return statefulFuncMap.get(serialName).apply(_ENV, closures);
            }
        } else {
            throw new IllegalArgumentException("Stateful function %s was not found in registry %s".formatted(serialName, id));
        }
    }

    @Override
    public LuaJavaApiFunction getFunction(String serialName, LuaObject _ENV, LuaObject[] closures, byte[] additional) {
        if (additional != null) {
            throw new UnsupportedOperationException("MixedStateFunctionRegistry does not support additional data currently. Name: %s ".formatted(serialName));
        }

        return getFunction(serialName, _ENV, closures);
    }

    public void register(String name, LuaJavaApiFunction apiFunction) {
        lessFuncMap.put(name, apiFunction);
        lessReverseMap.put(apiFunction, name);
    }

    public void register(String name, Class<? extends LuaJavaApiFunction> clazz, Function<LuaObject, LuaJavaApiFunction> instantiator) {
        register(name, clazz, (env, closures) -> instantiator.apply(env));
    }

    public void register(String name, Class<? extends LuaJavaApiFunction> clazz, BiFunction<LuaObject, LuaObject[], LuaJavaApiFunction> instantiator) {
        statefulFuncMap.put(name, instantiator);
        statefulReverseMap.put(clazz, name);
    }

    public Set<String> getAllNames() {
        var names = new HashSet<>(lessFuncMap.keySet());
        names.addAll(statefulFuncMap.keySet());
        return Collections.unmodifiableSet(names);
    }
}

package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.api.functions.ApiFunctionRegistry;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract non-sealed class LuaJavaApiFunction extends LuaFunction {
    public final ApiFunctionRegistry registry;

    public LuaJavaApiFunction(ApiFunctionRegistry registry) {
        super();
        this.registry = registry;
    }

    public LuaJavaApiFunction(ApiFunctionRegistry registry, LuaObject _ENV, LuaObject[] closures) {
        super(_ENV, closures);
        this.registry = registry;
    }

    @Override
    public String getCompilationUnit() {
        return "[Java]";
    }

    @Override
    public final void serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, ByteArrayBuilder bb) {
        var funcName = registry.getSerialName(this).getBytes(StandardCharsets.UTF_8);
        bb.append(true) // serialized with registry
                .append(_ENV == null ? -1 : _ENV.serialize(serialData, mappedObjs))
                .append(closures.length)
                .appendAll(Arrays.stream(closures).mapToInt(c -> c.serialize(serialData, mappedObjs)).toArray())
                .append(LuaObject.of(registry.registryID()).serialize(serialData, mappedObjs))
                .append(funcName.length)
                .appendAll(funcName);
        var extra = serialize(serialData, mappedObjs);
        if (extra != null) {
            bb.appendAll(extra);
        }
    }

    /**
     * Override this method to serialize additional data belonging to this function instance. This data will be passed
     * to {@link ApiFunctionRegistry#getFunction(String, LuaObject, LuaObject[], byte[])} as its last argument when
     * deserializing this instance.
     */
    public byte[] serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs) {
        return null;
    }
}

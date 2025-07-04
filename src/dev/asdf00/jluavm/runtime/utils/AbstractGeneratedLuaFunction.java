package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.exceptions.InternalLuaSerializationError;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public abstract class AbstractGeneratedLuaFunction extends LuaFunction {
    public AbstractGeneratedLuaFunction() {
        super();
    }

    public AbstractGeneratedLuaFunction(LuaObject _ENV, LuaObject[] closures) {
        super(_ENV, closures);
    }

    @Override
    public void serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, ByteArrayBuilder bb) {
        // reserve space
        serialData.add(null);
        Class<? extends AbstractGeneratedLuaFunction> clazz = getClass();
        try {
            var cudField = clazz.getDeclaredField("compilationUnitDept");
            var codeField = clazz.getDeclaredField("luaCode");
            int dpt = cudField.getInt(null);
            byte[] code = ((String) codeField.get(null)).getBytes(StandardCharsets.UTF_8);
            bb.append(false) // not serialized with registry
                    .append(_ENV.serialize(serialData, mappedObjs))
                    .append(LuaObject.of(closures).serialize(serialData, mappedObjs))
                    .append(dpt)
                    .appendAll(code);
        } catch (ReflectiveOperationException e) {
            throw new InternalLuaSerializationError("Reflection error while serializing a generated lua function", e);
        }
    }
}

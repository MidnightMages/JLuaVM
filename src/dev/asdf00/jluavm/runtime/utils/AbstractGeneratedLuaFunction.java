package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.exceptions.InternalLuaSerializationError;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;

import java.util.List;
import java.util.Map;

public abstract class AbstractGeneratedLuaFunction extends LuaFunction {
    public AbstractGeneratedLuaFunction(LuaObject _ENV, LuaObject[] closures) {
        super(_ENV, closures);
    }

    @Override
    public void serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, ByteArrayBuilder bb) {
        Class<? extends AbstractGeneratedLuaFunction> clazz = getClass();
        try {
            var cudField = clazz.getDeclaredField("compilationUnitDept");
            var codeField = clazz.getDeclaredField("luaCode");
            int dpt = cudField.getInt(null);
            bb.append(false) // not serialized with registry
                    .append(LuaObject.of((String) codeField.get(null)).serialize(serialData, mappedObjs))
                    .append(_ENV.serialize(serialData, mappedObjs))
                    .append(closures.length);
            for (var c : closures) {
                bb.append(c == null ? -1 : c.serialize(serialData, mappedObjs));
            }
            bb.append(dpt);
        } catch (ReflectiveOperationException e) {
            throw new InternalLuaSerializationError("Reflection error while serializing a generated lua function", e);
        }
    }
}

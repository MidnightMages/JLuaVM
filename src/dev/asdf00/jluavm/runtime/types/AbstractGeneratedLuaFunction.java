package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.InternalLuaSerializationError;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

public abstract non-sealed class AbstractGeneratedLuaFunction extends LuaFunction {
    public final String compilationUnit;
    public final int lineNum;
    // TODO serialize unit

    public AbstractGeneratedLuaFunction(String compilationUnit, int lineNum, LuaObject _ENV, LuaObject[] closures) {
        super(_ENV, closures);
        this.compilationUnit = compilationUnit;
        this.lineNum = lineNum;
    }

    protected <T extends LuaFunction> T newInnerFunction(int lineNum, Constructor<T> ctor, LuaObject... closures) {
        try {
            return ctor.newInstance(compilationUnit, lineNum, _ENV, closures);
        } catch (ReflectiveOperationException e) {
            throw new InternalLuaRuntimeError("error on generating inner function reference (%s)".formatted(e));
        }
    }

    @Override
    public String getCompilationUnit() {
        return compilationUnit;
    }

    @Override
    public void serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, ByteArrayBuilder bb) {
        Class<? extends AbstractGeneratedLuaFunction> clazz = getClass();
        try {
            var cudField = clazz.getDeclaredField("compilationUnitDept");
            var codeField = clazz.getDeclaredField("luaCode");
            int dpt = cudField.getInt(null);
            bb.append(false) // not serialized with registry
                    // TODO insert compilationUnit
                    .append(LuaObject.of(compilationUnit).serialize(serialData, mappedObjs))
                    .append(lineNum)
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

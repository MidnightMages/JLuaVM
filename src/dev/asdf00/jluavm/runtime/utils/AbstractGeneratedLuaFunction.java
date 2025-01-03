package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.List;
import java.util.Map;

public abstract class AbstractGeneratedLuaFunction extends LuaFunction {
    @Override
    public byte[] serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs) {
        throw new UnsupportedOperationException("unimplemented");
    }
}

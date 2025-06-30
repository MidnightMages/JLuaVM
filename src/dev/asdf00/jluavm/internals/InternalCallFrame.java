package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class InternalCallFrame extends AbstractCallStackFrame {
    private final LFunc callable;
    private final String funcName;
    public LuaObject[] arguments;

    public InternalCallFrame(LuaObject[] locals, int startLocals, LFunc callable, String funcName, LuaObject[] arguments) {
        super(locals, startLocals);
        this.callable = callable;
        this.funcName = funcName;
        this.arguments = arguments;
    }

    @Override
    public void execute(LuaVM_RT vm) {
        callable.invoke(vm, locals, arguments, resume, expressionStack, rvals);
        rvals = null;
        arguments = null;
    }

    @Override
    public void reset() {
        init();
    }

    public byte[] serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs) {
        var bb = new ByteArrayBuilder();
        bb.append(LuaObject.of(arguments).serialize(serialData, mappedObjs))
                .appendAll(funcName.getBytes(StandardCharsets.UTF_8));
        return bb.toArray();
        // TODO
    }
}

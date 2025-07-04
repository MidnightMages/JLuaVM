package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;
import dev.asdf00.jluavm.utils.ByteArrayReader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InternalCallFrame extends AbstractCallStackFrame {
    private final LFunc callable;
    private final String funcName;
    public LuaObject[] arguments;

    private InternalCallFrame(DataContainer container, LFunc callable, String funcName, LuaObject[] arguments) {
        super(container);
        this.callable = callable;
        this.funcName = funcName;
        this.arguments = arguments;
    }

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
        serialize(serialData, mappedObjs, bb);
        bb.append(LuaObject.of(arguments).serialize(serialData, mappedObjs))
                .appendAll(funcName.getBytes(StandardCharsets.UTF_8));
        return bb.toArray();
    }

    public static InternalCallFrame deserialize(LuaFunction parentFunc, LuaObject[] objs, ByteArrayReader rdr) {
        DataContainer container = abstractDeserialize(objs, rdr);
        LuaObject[] arguments = objs[rdr.readInt()].asArray();
        String funcName = new String(rdr.readArray(rdr.remaining()), StandardCharsets.UTF_8);

        LFunc callable = LuaFunction.staticLFuncs.get(funcName);
        if (callable == null) {
            // recover internal scopes using reflection
            Class<? extends LuaFunction> clazz = parentFunc.getClass();
            try {
                var m = Objects.requireNonNull(clazz.getMethod(funcName, LuaVM_RT.class, LuaObject[].class, LuaObject[].class, int.class, LuaObject[].class, LuaObject[].class));
                callable = (vm, stackFrame, args, resume, expressionStack, returned) -> {
                    try {
                        m.invoke(parentFunc, vm, stackFrame, args, resume, expressionStack, returned);
                    } catch (ReflectiveOperationException e) {
                        throw new InternalLuaRuntimeError("reflection error while executing deserialized internal scope");
                    }
                };

            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }

        return new InternalCallFrame(container, callable, funcName, arguments);
    }
}

package dev.asdf00.jluavm;

import dev.asdf00.jluavm.api.lambdas.*;
import dev.asdf00.jluavm.api.userdata.*;
import dev.asdf00.jluavm.exceptions.LuaJavaError;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;
import dev.asdf00.jluavm.runtime.utils.UDTranslators;
import dev.asdf00.jluavm.utils.ByteArrayReader;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static dev.asdf00.jluavm.runtime.utils.UDTranslators.lo2i;
import static dev.asdf00.jluavm.runtime.utils.UDTranslators.lo2ud;

public class Sandbox implements LuaUserData {
    @LuaExposed
    public int haha;

    @LuaExposed(LuaExposed.Policy.READ)
    public float nono;

    @LuaCallable
    public long function(boolean a, String b) {
        return -1;
    }

    @LuaCallable
    public void method(LuaObject a) {

    }

    @LuaCallable
    public void method(LuaObject a, int b) {

    }

    @LuaCallable
    public void method(LuaObject a, int b, LuaObject... c) {

    }

    @LuaCallable
    public LuaObject[] myVarargsMultiReturn(LuaObject a, double b, LuaObject... varargs) {
        return Singletons.EMPTY_LUA_OBJ_ARRAY;
    }

    private boolean backing;
    @LuaExposed
    public final LuaProperty test = LuaProperty.ofBoolean(
            () -> backing,
            b -> backing = b
    );

    @LuaMetaFunction("__add")
    public LuaObject mySillyAddition(LuaObject other) {
        return LuaObject.nil();
    }

    @Override
    public byte[] luaSerialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs) {
        return new byte[0];
    }

    @LuaDeserializer
    public static Sandbox luaDeserialize(LuaObject[] objs, ByteArrayReader reader) {
        return new Sandbox();
    }

    // more code
}

class GlueFunc$_dev$_asdf00$_jluavm$_Sandbox {
    public static final BiFunction<LuaObject[], ByteArrayReader, LuaUserData> serializer = dev.asdf00.jluavm.Sandbox::luaDeserialize;

    public static final Map<String, LLVaMultiFunction> functions = Map.of(
            "method", (vm, params) -> {
                if (params.length < 1) {
                    throw new LuaJavaError("expected a method call to a userdata object (you may use the LUA method syntax)");
                }
                Sandbox dyn = lo2ud(Sandbox.class, params[0]);
                LuaObject[] res = Singletons.EMPTY_LUA_OBJ_ARRAY;
                if (params.length == 2) {
                    dyn.method(params[1]);
                } else if (params.length == 3) {
                    dyn.method(params[1], lo2i(params[2]));
                } else if (params.length >= 3) {
                    dyn.method(params[1], lo2i(params[2]), Arrays.copyOfRange(params, 3, params.length));
                } else {
                    throw new LuaJavaError("no overload found for %d arguments (available: Sandbox#method(LuaObject), Sandbox#method(LuaObject, int), Sandbox#method(LuaObject, int, LuaObject...))".formatted(params.length - 1));
                }
                return res;
            },
            // deprecated format
            "myVarargsMultiReturn", (vm, params) -> {
                if (params.length < 3) {
                    throw new LuaJavaError("expected userdata instance + at least 1 argument (you may use the LUA method syntax), got " + params.length);
                }
                Sandbox dyn = lo2ud(Sandbox.class, params[0]);
                if (dyn == null) {
                    throw new LuaJavaError("userdata object required as first argument (you may use the LUA method syntax)");
                }
                // no array wrapping because multi-return already
                return dyn.myVarargsMultiReturn(params[0], UDTranslators.lo2d(params[1]), Arrays.copyOfRange(params, 3, params.length));
            }
    );

    public static final Map<String, Function<Sandbox, LuaObject>> getters = Map.of(
            "haha", ud -> LuaObject.of(ud.haha),
            "nono", ud -> LuaObject.of(ud.nono),
            "test", ud -> ud.test.get()
    );

    public static final Map<String, BiConsumer<Sandbox, LuaObject>> setters = Map.of(
            "haha", (ud, val) -> ud.haha = UDTranslators.lo2i(val),
            "test", (ud, val) -> ud.test.set(val)
    );

    public static final LLBiFunction __add = (vm, ud, lo) -> lo2ud(Sandbox.class, ud).mySillyAddition(lo);
    public static final LLBiFunction __sub = null;
    public static final LLBiFunction __mul = null;
    public static final LLBiFunction __div = null;
    public static final LLBiFunction __mod = null;
    public static final LLBiFunction __pow = null;
    public static final LLBiFunction __unm = null;
    public static final LLBiFunction __idiv = null;
    public static final LLBiFunction __band = null;
    public static final LLBiFunction __bor = null;
    public static final LLBiFunction __bxor = null;
    public static final LLBiFunction __bnot = null;
    public static final LLBiFunction __shl = null;
    public static final LLBiFunction __shr = null;
    public static final LLBiFunction __concat = null;
    public static final LLBiFunction __eq = null;
    public static final LLBiFunction __lt = null;
    public static final LLBiFunction __le = null;
    public static final LLBiFunction __index = null;

    public static final LLFunction __len = null;
    public static final LLTriConsumer __newindex = null;
    public static final LLVaMultiFunction __call = null;
    public static final LLFunction __name = null;
    public static final LLBiConsumer __close = null;  // has to be dynamic, gets error obj as param
    // public static final LLBiFunction __gc = null;  // ignored
    // public static final LLBiFunction __mode = null;  // ignored
}

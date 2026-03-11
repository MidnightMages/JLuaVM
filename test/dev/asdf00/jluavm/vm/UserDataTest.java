package dev.asdf00.jluavm.vm;

import dev.asdf00.jluavm.api.userdata.*;
import dev.asdf00.jluavm.exceptions.LuaJavaError;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.ByteArrayReader;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class UserDataTest extends BaseVmTest {
    @Test
    void anonymousCallSideEffects() {
        loadAssertSuccessAndRv("""
                        local ud = ...
                        ud.wrt = 2.5
                        ud:init()
                        ud[0] = 6
                        ud.myIntRW = ud.adder
                        return ud.myIntRW + ud[0], ud:testCall("hi", "a", "b", "c")
                        """,
                new LuaObject[]{LuaObject.of(new HotMess())},
                new LuaObject[]{LuaObject.of(10), LuaObject.of("hi: string: a,string: b,string: c")});
    }

    @Test
    void overloadTest() {
        loadAssertSuccessAndRv("""
                        local ud = ...
                        return ud:overMethod() .. tostring(ud:overMethod(1, nil)) .. tostring(ud:overMethod(1, nil, false))
                        """,
                new LuaObject[]{LuaObject.of(new HotMess())},
                new LuaObject[]{LuaObject.of("empty10011")});
    }

    public static class HotMess implements LuaUserData {
        private LuaObject[] array;

        @LuaExposed
        public int myIntRW = 0;

        @LuaExposed(LuaExposed.Policy.WRITE)
        public double wrt = 0.0;

        @LuaExposed(LuaExposed.Policy.READ)
        public final LuaProperty adder = LuaProperty.ofDouble(
                () -> {
                    wrt += 1.5;
                    return wrt;
                },
                null
        );

        @LuaCallable
        public void init() {
            array = new LuaObject[5];
            Arrays.fill(array, LuaObject.nil());
        }

        @LuaCallable
        public String testCall(String a, LuaObject[] varargs) {
            return a + ": " + String.join(",", Arrays.stream(varargs).map(lo -> lo.toString()).toList());
        }

        @LuaCallable
        public String overMethod() {
            return "empty";
        }


        @LuaCallable
        public int overMethod(int a, LuaObject b) {
            return 1000 + overMethod(a, b, false);
        }

        @LuaCallable
        public int overMethod(int a, LuaObject b, boolean c, LuaObject... d) {
            return a;
        }

        @Override
        public LuaObject luaGeneralGet(LuaObject key) throws LuaJavaError {
            if (!key.isLong()) {
                return null;
            }
            if (Long.compareUnsigned(key.asLong(), array.length) < 1) {
                return array[(int) key.asLong()];
            } else {
                throw new LuaJavaError("array index " + key.asLong() + " out of bounds");
            }
        }

        @Override
        public boolean luaGeneralSet(LuaObject key, LuaObject value) throws LuaJavaError {
            if (!key.isLong()) {
                return false;
            }
            if (Long.compareUnsigned(key.asLong(), array.length) < 1) {
                array[(int) key.asLong()] = value;
                return true;
            } else {
                throw new LuaJavaError("array index " + key.asLong() + " out of bounds");
            }
        }

        @Override
        public byte[] luaSerialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, Object additionalData) {
            // TODO actually provide serializaion
            return null;
        }

        @LuaDeserializer
        public static HotMess todoDeserializer(LuaObject[] objs, ByteArrayReader reader, Queue<Runnable> postActions, Object additionalData) {
            // TODO actually provide serializaion
            return null;
        }
    }
}

package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.api.functions.ApiFunctionRegistry;
import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.internals.Coroutine;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.ByteArrayReader;
import dev.asdf00.jluavm.utils.Triple;
import dev.asdf00.jluavm.utils.Tuple;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

public class StateDeserializer {

    /**
     * @return a pair of coroutines, the first one being the root coroutine, the second one being the current coroutine
     * of the given state. Additionally, the isErroring flag of the vm state is returned.
     */
    public static Triple<Coroutine, Coroutine, Boolean> deserialize(LuaVM vm, Map<String, ApiFunctionRegistry> registries, byte[] rawState) {
        var reader = new ByteArrayReader(rawState);
        if (reader.readInt() != LuaVM_RT.STATE_SERIALIZATION_VERSION) {
            throw new IllegalArgumentException("mismatch in serialization version");
        }
        int rootCoIdx = reader.readInt();
        int curCoIdx = reader.readInt();
        boolean isErroring = reader.readBool();
        var objs = new LuaObject[reader.readInt()];
        var delayed = new ByteArrayReader[objs.length];

        for (int i = 0; i < objs.length; i++) {
            var cur = reader.slice(reader.readInt());
            int type = cur.readInt();
            /*
             * Deserialize "primitives" immediately.
             * For types containing references to other lua objects, we just generate an empty
             * lua object of the correct type and set refVal and metaTable later.
             */
            switch (type) {
                case 1 -> {
                    // nil
                    objs[i] = LuaObject.nil();
                }
                case 0b10 -> {
                    // boolean
                    objs[i] = LuaObject.of(cur.readBool());
                }
                case 0b100 -> {
                    // double
                    objs[i] = LuaObject.of(Double.longBitsToDouble(cur.readLong()));
                }
                case 0b1000 -> {
                    // long
                    objs[i] = LuaObject.of(cur.readLong());
                }
                case 0b1_0000 -> {
                    // string
                    objs[i] = LuaObject.of(new String(cur.readArray(cur.remaining()), StandardCharsets.UTF_8));
                }
                case 0b10_0000 -> {
                    // function
                    objs[i] = LuaObject.of((LuaFunction) null);
                    delayed[i] = cur;
                }
                case 0b100_0000 -> {
                    // userdata
                    throw new UnsupportedOperationException("serializing userdata is not implemented");
                }
                case 0b1000_0000 -> {
                    // thread
                    objs[i] = LuaObject.of((Coroutine) null);
                    delayed[i] = cur;
                }
                case 0b1_0000_0000 -> {
                    // table
                    objs[i] = LuaObject.wrapMap(null);
                    delayed[i] = cur;
                }
                case 0b10_0000_0000 -> {
                    // array
                    objs[i] = LuaObject.of((LuaObject[]) null);
                    delayed[i] = cur;
                }
                case 0b100_0000_0000 -> {
                    // box
                    objs[i] = LuaObject.box(null);
                    delayed[i] = cur;
                }
                default -> {
                    throw new IllegalArgumentException("malformed type %s in serialized state".formatted(Integer.toHexString(type)));
                }
            }
        }

        for (int i = 0; i < objs.length; i++) {
            if (delayed[i] == null) {
                continue;
            }
            var lobj = objs[i];
            if (lobj.type == 0b1000_0000) {
                // threads need to be deserialized LAST (after all functions)
                continue;
            }
            var rdr = delayed[i];
            switch (lobj.type) {
                case 0b10_0000 -> {
                    // function
                    if (rdr.readByte() == 1) {
                        // API function with registry
                        var env = objs[rdr.readInt()];
                        var closures = objs[rdr.readInt()].asArray();
                        var regName = new String(rdr.readArray(rdr.readInt()), StandardCharsets.UTF_8);
                        var funcName = new String(rdr.readArray(rdr.readInt()), StandardCharsets.UTF_8);
                        var additional = rdr.remaining() > 0 ? rdr.readArray(rdr.remaining()) : null;
                        var func = registries.get(regName).getFunction(funcName, env, closures, additional);
                        func.selfLuaObj = lobj;
                        lobj.refVal = func;
                    } else {
                        // generated lua function
                        var env = objs[rdr.readInt()];
                        var closures = objs[rdr.readInt()].asArray();
                        int unitIdx = rdr.readInt();
                        String code = new String(rdr.readArray(rdr.remaining()), StandardCharsets.UTF_8);
                        try {
                            var func = vm.compile(code)[unitIdx].newInstance(env, closures);
                            func.selfLuaObj = lobj;
                            lobj.refVal = func;
                        } catch (LuaLoadingException | ReflectiveOperationException | ArrayIndexOutOfBoundsException e) {
                            throw new IllegalStateException("error deserializing generated function", e);
                        }
                    }
                }
                case 0b100_0000 -> {
                    // userdata
                    throw new UnsupportedOperationException("serializing userdata is not implemented");
                }
                // thread handled later
                case 0b1_0000_0000 -> {
                    // table
                    int mtblIdx = rdr.readInt();
                    if (mtblIdx >= 0) {
                        // valid metatable
                        lobj.metaTable = objs[mtblIdx];
                    }
                    int kvPairs = rdr.remaining() / 8;
                    var tbl = lobj.asMap();
                    for (int j = 0; j < kvPairs; j++) {
                        tbl.put(objs[rdr.readInt()], objs[rdr.readInt()]);
                    }
                }
                case 0b10_0000_0000 -> {
                    // array
                    var internal = new LuaObject[rdr.remaining() / 4];
                    for (int j = 0; j < internal.length; j++) {
                        internal[j] = objs[rdr.readInt()];
                    }
                    lobj.refVal = internal;
                }
                case 0b100_0000_0000 -> {
                    // box
                    lobj.refVal = objs[rdr.readInt()];
                }
                default -> {
                    throw new IllegalArgumentException("malformed type %s in serialized state".formatted(Integer.toHexString(lobj.type)));
                }
            }
            assert rdr.remaining() == 0;
            delayed[i] = null;
        }

        ArrayList<Tuple<Coroutine, LuaObject>> finalResolution = new ArrayList<>();
        for (int i = 0; i < objs.length; i++) {
            if (delayed[i] == null) {
                continue;
            }
            var lobj = objs[i];
            assert lobj.type == 0b1000_0000;
            var res = Coroutine.deserialize(objs, lobj, delayed[i]);
            lobj.refVal = res.x();
            if (res.y() != null) {
                finalResolution.add(res);
            }
        }

        for (var fr : finalResolution) {
            fr.x().yieldTo = fr.y().asCoroutine();
        }

        return new Triple<>(objs[rootCoIdx].asCoroutine(), objs[curCoIdx].asCoroutine(), isErroring);
    }
}

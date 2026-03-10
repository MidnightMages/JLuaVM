package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.api.functions.ApiFunctionRegistry;
import dev.asdf00.jluavm.api.userdata.LuaUserData;
import dev.asdf00.jluavm.exceptions.LuaLoadingException;
import dev.asdf00.jluavm.internals.Coroutine;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.AbstractGeneratedLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaHashMap;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.ByteArrayReader;
import dev.asdf00.jluavm.utils.Quadruple;
import dev.asdf00.jluavm.utils.Tuple;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class StateDeserializer {

    /**
     * @return a pair of coroutines, the first one being the root coroutine, the second one being the current coroutine
     * of the given state. Additionally, the isErroring flag of the vm state is returned.
     */
    public static Quadruple<Coroutine, Coroutine, Boolean, Boolean> deserialize(Map<String, ApiFunctionRegistry> registries, byte[] rawState, Object additionalData) {
        var reader = new ByteArrayReader(rawState);
        if (reader.readInt() != LuaVM_RT.STATE_SERIALIZATION_VERSION) {
            throw new IllegalArgumentException("mismatch in serialization version");
        }
        int rootCoIdx = reader.readInt();
        int curCoIdx = reader.readInt();
        boolean isErroring = reader.readBool();
        boolean stopRequested = reader.readBool();
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
                case LuaObject.Types.NIL -> {
                    // nil
                    objs[i] = LuaObject.nil();
                }
                case LuaObject.Types.BOOLEAN -> {
                    // boolean
                    objs[i] = LuaObject.of(cur.readBool());
                }
                case LuaObject.Types.DOUBLE -> {
                    // double
                    objs[i] = LuaObject.of(Double.longBitsToDouble(cur.readLong()));
                }
                case LuaObject.Types.LONG -> {
                    // long
                    objs[i] = LuaObject.of(cur.readLong());
                }
                case LuaObject.Types.STRING -> {
                    // string
                    objs[i] = LuaObject.of(new String(cur.readArray(cur.remaining()), StandardCharsets.UTF_8));
                }
                case LuaObject.Types.FUNCTION -> {
                    // function
                    objs[i] = LuaObject.of((LuaFunction) null);
                    delayed[i] = cur;
                }
                case LuaObject.Types.USERDATA -> {
                    // userdata
                    objs[i] = LuaObject.of((LuaUserData) null);
                    delayed[i] = cur;
                }
                case LuaObject.Types.THREAD -> {
                    // thread
                    objs[i] = LuaObject.of((Coroutine) null);
                    delayed[i] = cur;
                }
                case LuaObject.Types.TABLE -> {
                    // table
                    objs[i] = LuaObject.wrapMap(null);
                    delayed[i] = cur;
                }
                case LuaObject.Types.ARRAY -> {
                    // array
                    objs[i] = LuaObject.of((LuaObject[]) null);
                    delayed[i] = cur;
                }
                case LuaObject.Types.BOX -> {
                    // box
                    objs[i] = LuaObject.box(null);
                    delayed[i] = cur;
                }
                default -> {
                    throw new IllegalArgumentException("malformed type %s in serialized state".formatted(Integer.toHexString(type)));
                }
            }
        }

        var compilationResults = new HashMap<String, Constructor<? extends AbstractGeneratedLuaFunction>[]>();
        for (int i = 0; i < objs.length; i++) {
            if (delayed[i] == null) {
                continue;
            }
            var lobj = objs[i];
            if (lobj.type == LuaObject.Types.THREAD || lobj.type == LuaObject.Types.USERDATA) {
                // threads need to be deserialized LAST of the internal types (after all functions)
                // userdata will be deserialized after all internal types
                continue;
            }
            var rdr = delayed[i];
            switch (lobj.type) {
                case LuaObject.Types.FUNCTION -> {
                    // function
                    if (rdr.readBool()) {
                        // API function with registry
                        var env = maybeNull(objs, rdr.readInt());
                        var closures = new LuaObject[rdr.readInt()];
                        for (int j = 0; j < closures.length; j++) {
                            closures[j] = objs[rdr.readInt()];
                        }
                        var regName = objs[rdr.readInt()].asString();
                        var funcName = new String(rdr.readArray(rdr.readInt()), StandardCharsets.UTF_8);
                        var additional = rdr.remaining() > 0 ? rdr.readArray(rdr.remaining()) : null;
                        var func = registries.get(regName).getFunction(funcName, env, closures, additional);
                        func.selfLuaObj = lobj;
                        lobj.refVal = func;
                    } else {
                        // generated lua function
                        String compilationUnit = objs[rdr.readInt()].asString();
                        int lineNum = rdr.readInt();
                        String code = objs[rdr.readInt()].asString();
                        var env = objs[rdr.readInt()];
                        var closures = new LuaObject[rdr.readInt()];
                        for (int j = 0; j < closures.length; j++) {
                            closures[j] = maybeNull(objs, rdr.readInt());
                        }
                        int unitIdx = rdr.readInt();
                        try {
                            var func = compilationResults.computeIfAbsent(code, key -> LuaVM.compile(key))[unitIdx]
                                    .newInstance(compilationUnit, lineNum, env, closures);
                            func.selfLuaObj = lobj;
                            lobj.refVal = func;
                        } catch (LuaLoadingException | ReflectiveOperationException |
                                 ArrayIndexOutOfBoundsException e) {
                            throw new IllegalStateException("error deserializing generated function", e);
                        }
                    }
                }
                // thread handled later
                case LuaObject.Types.TABLE -> {
                    // table
                    int mtblIdx = rdr.readInt();
                    if (mtblIdx >= 0) {
                        // valid metatable
                        lobj.metaTable = objs[mtblIdx];
                    }
                    int kvPairs = rdr.remaining() / 8;
                    var tbl = new LuaHashMap();
                    for (int j = 0; j < kvPairs; j++) {
                        tbl.put(objs[rdr.readInt()], objs[rdr.readInt()]);
                    }
                    lobj.refVal = tbl;
                }
                case LuaObject.Types.ARRAY -> {
                    // array
                    var internal = new LuaObject[rdr.remaining() / 4];
                    for (int j = 0; j < internal.length; j++) {
                        internal[j] = maybeNull(objs, rdr.readInt());
                    }
                    lobj.refVal = internal;
                }
                case LuaObject.Types.BOX -> {
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
            if (delayed[i] == null || objs[i].type == LuaObject.Types.USERDATA) {
                continue;
            }
            var lobj = objs[i];
            assert lobj.type == LuaObject.Types.THREAD;
            var res = Coroutine.deserialize(objs, lobj, delayed[i]);
            lobj.refVal = res.x();
            if (res.y() != null) {
                finalResolution.add(res);
            }
        }

        for (var fr : finalResolution) {
            fr.x().yieldTo = fr.y().asCoroutine();
        }

        // resolve userdata last
        // TODO allow post actions
        Queue<Runnable> postActions = new ArrayDeque<>();
        for (int i = 0; i < delayed.length; i++) {
            var lobj = objs[i];
            if (lobj.type != LuaObject.Types.USERDATA) {
                continue;
            }
            var rdr = delayed[i];
            String clName = objs[rdr.readInt()].asString();
            var clazz = LuaVM_RT.getUserdataClass(clName);
            var instance = LuaVM_RT.getDescriptor(clazz).deserialize(objs, rdr, postActions, additionalData);
            lobj.refVal = instance;
            instance.setSelfAsLuaObject(lobj);
        }

        // second round of userdata deserialization
        for (var action : postActions) {
            action.run();
        }

        return new Quadruple<>(objs[rootCoIdx].asCoroutine(), objs[curCoIdx].asCoroutine(), isErroring, stopRequested);
    }

    // =================================================================================================================
    // public helpers
    // =================================================================================================================

    public static LuaObject maybeNull(LuaObject[] objs, int idx) {
        return idx >= 0 ? objs[idx] : null;
    }

    public static <T> T maybeNull(LuaObject[] objs, int idx, Function<LuaObject, T> transform) {
        return idx >= 0 ? transform.apply(objs[idx]) : null;
    }
}

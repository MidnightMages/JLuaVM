package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.api.functions.MixedStateFunctionRegistry;
import dev.asdf00.jluavm.api.functions.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import static dev.asdf00.jluavm.runtime.types.LuaObject.Types.*;
import static dev.asdf00.jluavm.runtime.utils.RTUtils.*;

public class LTable {

    private static final String TABLE_PREFIX = "table.";

    // https://www.lua.org/manual/5.4/manual.html#6.6
    public static void registerStdTable(MixedStateFunctionRegistry registry) {
        registry.register(TABLE_PREFIX + "concat",
                AtomicLuaFunction.vaForOneResult(registry, (vm, args) -> {
                    var tbl = args.length < 1 ? null : args[0];
                    if (tbl == null || !tbl.isTable()) {
                        vm.error(funcArgTypeError("table.concat", 0, tbl, "table"));
                        return null;
                    }
                    assert tbl != null;

                    var sep = args.length < 2 ? null : args[1];
                    if (sep != null && (sep.getType() & (STRING | NUMBER | NIL)) == 0) {
                        vm.error(funcArgAnyTypeError("table.concat", 1, sep, "string", "number", "nil", "nothing"));
                        return null;
                    }
                    var seps = sep == null ? "" : sep.asString();

                    var i = args.length < 3 ? 1 : (args[2].hasLongRepr() ? args[2].asLong() : -1);
                    if (i < 0) {
                        vm.error(funcArgAnyTypeError("table.concat", 2, args.length > 2 ? args[2] : null, "number", "nil", "nothing"));
                        return null;
                    }

                    // TODO call the __len metamethod instead if it exists
                    var j = args.length < 4 ? tbl.len().asLong() : (args[3].hasLongRepr() ? args[3].asLong() : -1);
                    if (j < 0) {
                        vm.error(funcArgAnyTypeError("table.concat", 3, args.length > 3 ? args[3] : null, "number", "nil", "nothing"));
                        return null;
                    }

                    var sb = new StringBuilder();
                    for (long k = i; k <= j; k++) {
                        if (k != i)
                            sb.append(seps);
                        var elem = tbl.get(LuaObject.of(k));
                        if (elem.isNil()) {
                            vm.error(LuaObject.of("invalid value (nil) at index %d in table for 'concat'".formatted(k)));
                            return null;
                        }
                        sb.append(elem.asString());
                    }

                    return LuaObject.of(sb.toString());
                }));

        registry.register(TABLE_PREFIX + "insert",
                AtomicLuaFunction.vaForManyResults(registry, (vm, args) -> {
                    // LuaC deviation (as the lua way seems inconsistent across sequences and dicts):
                    // take all elements tbl[pos] until, excluding the next hole (or nil) and shift them up by 1 (all raw, no metatable interaction)
                    // then check if tbl has a metamethod __newindex and, if so, call __newindex, else rawset tbl[pos] to element

                    var tbl = args.length < 1 ? null : args[0];
                    if (tbl == null || !tbl.isTable()) {
                        vm.error(funcArgTypeError("table.insert", 0, tbl, "table"));
                        return null;
                    }

                    assert tbl != null;

                    var pos = args.length > 2 ? args[1] : null;
                    var value = args.length > 2 ? args[2] : args[1];
                    if (pos != null && !pos.hasLongRepr()) {
                        vm.error(funcArgAnyTypeError("table.insert", 1, pos, "integer", "nothing"));
                        return null;
                    }
                    if (value == null) {
                        vm.error(funcArgAnyTypeError("table.insert", args.length > 2 ? 2 : 1, value, "any value"));
                        return null;
                    }

                    var map = tbl.asMap();
                    // TODO call the __len metamethod instead if it exists maybe?
                    // the max insert position is __len +1
                    // this function must not modify any elements at position "> __len+1".
                    // If the target pos is in this forbidden error, throw an error. If this occurs during up-shifting, simply stop shifting
                    var idx = LuaObject.of(pos != null ? pos.asLong() : (map.luaLen() + 1));

                    var elementBuffer = value;
                    while (true) {
                        var replaceCandidate = map.getOrDefault(idx, LuaObject.nil());
                        if (!replaceCandidate.isNil()) { // shift it up
                            map.put(idx, elementBuffer);
                            elementBuffer = replaceCandidate;
                            idx = LuaObject.of(idx.lVal + 1);
                        } else { // insert this one and stop
                            var mtf = tbl.getMetaValueOrNil("__newindex");
                            if (mtf.isNil()) { // no mt func, do a direct insert and we are done
                                map.put(idx, elementBuffer);
                                return Singletons.EMPTY_LUA_OBJ_ARRAY;
                            } else {
                                // TODO call mt func
                                throw new UnsupportedOperationException("table insert mt operation is not yet implemented.");
                            }
                        }
                    }
                }));

        // TODO add table.move

        registry.register(TABLE_PREFIX + "pack",
                AtomicLuaFunction.vaForOneResult(registry, (vm, args) -> {
                    var t = LuaObject.table();
                    for (int i = 0; i < args.length; i++) {
                        t.set(LuaObject.of(i + 1), args[i]);
                    }
                    t.set("n", LuaObject.of(args.length));
                    return t;
                }));

        // LUAC DEVIATION. Behaves slightly differently to LuaC, as the table-length operator behaves in a more
        // consistent manner. Still behaves according to spec however.
        registry.register(TABLE_PREFIX + "remove", AtomicLuaFunction.vaForOneResult(registry, (vm, args) -> {
            var tbl = args.length < 1 ? null : args[0];
            if (tbl == null || !tbl.isTable()) {
                vm.error(funcArgTypeError("table.remove", 0, tbl, "table"));
                return null;
            }
            assert tbl != null;

            var tLen = tbl.len().lVal;
            var pos = args.length < 2 ? tLen : (args[1].hasLongRepr() ? args[1].asLong() : -1);

            if (pos == 0 && tLen == 0 || pos == tLen + 1) // origin: spec https://www.lua.org/manual/5.4/manual.html#pdf-table.remove
                return LuaObject.NIL;

            if (pos < 0) {
                vm.error(funcArgAnyTypeError("table.remove", 1, args.length < 2 ? null : args[1], "non-negative number", "nil", "nothing"));
                return null;
            }

            if (pos > tLen) {
                vm.error(funcBadArgError("table.remove", 1, "position out of bounds"));
                return null;
            }

            var lHashMap = tbl.asMap();
            var prevVal = LuaObject.NIL;
            for (long i = tLen; i >= pos; i--) {
                prevVal = lHashMap.put(LuaObject.of(i), prevVal);
            }
            return prevVal;
        }));

        // TODO add table.sort

        registry.register(TABLE_PREFIX + "unpack",
                AtomicLuaFunction.vaForManyResults(registry, (vm, args) -> {
                    var tbl = args.length < 1 ? null : args[0];
                    if (tbl == null || !tbl.isTable()) {
                        vm.error(funcArgTypeError("table.unpack", 0, tbl, "table"));
                        return null;
                    }
                    assert tbl != null;

                    var i = args.length < 2 ? 1 : (args[1].hasLongRepr() ? args[1].asLong() : -1);
                    if (i < 0) {
                        vm.error(funcArgAnyTypeError("table.unpack", 1, args.length < 2 ? null : args[1], "non-negative number", "nil", "nothing"));
                        return null;
                    }

                    // TODO call the __len metamethod instead if it exists
                    var j = args.length < 3 ? tbl.len().asLong() : (args[2].hasLongRepr() ? args[2].asLong() : -1);
                    if (j < 0) {
                        vm.error(funcArgAnyTypeError("table.unpack", 2, args.length < 3 ? null : args[1], "non-negative number", "nil", "nothing"));
                        return null;
                    }

                    var t = new LuaObject[(int) (j - i + 1)];
                    int arrIdx = 0;
                    for (int k = ((int) i); k <= j; k++) {
                        t[arrIdx++] = k >= 1 && k <= tbl.len().asLong() ? tbl.get(LuaObject.of(k)) : LuaObject.NIL;
                    }
                    return t;
                }));
    }
}

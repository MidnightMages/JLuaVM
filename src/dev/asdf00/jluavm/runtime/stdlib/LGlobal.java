package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.errors.LuaArgumentError;
import dev.asdf00.jluavm.runtime.errors.LuaUserError;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Arrays;

public class LGlobal {

    private static boolean errorIfTableIndexNilOrNaN(LuaVM_RT vm, LuaObject x) {
        if (x.isNil()) {
            vm.error(new LuaUserError("table index is nil"));
            return true;
        }
        if (x.isDouble() && Double.isNaN(x.asDouble())) {
            vm.error(new LuaUserError("table index is NaN"));
            return true;
        }
        return false;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static LuaObject getTable(boolean includeUnconstrainedFunctions) {
        var rv = LuaObject.table();

        if (includeUnconstrainedFunctions) {
        /* TODO, add the following ones: https://www.lua.org/manual/5.4/manual.html#6.1
            dofile
            loadfile
            require? (could be considered safe, depending on the implementation)
         */
        }

        /* TODO, add the following ones: https://www.lua.org/manual/5.4/manual.html#6.1
        collectgarbage
        error
        ipairs
        load
        next
        pairs
        pcall
        print
        tonumber
        warn
        xpcall
         */
        rv.set("assert", LuaObject.of(AtomicLuaFunction.vaForManyResults((vm, params) -> {
            if (params.length == 0) {
                vm.error(new LuaArgumentError(0, "assert", "value expected"));
                return null;
            }

            if (params[0].isTruthy())
                return params;

            // LUAC DEVIATION. Our assertion returns tostring(msg) instead of just the type of that argument for nil and boolean
            vm.error(new LuaUserError(params.length < 2 ? "assertion failed!" : params[1].asString()));
            return null;
        })));
        rv.set("getmetatable", LuaObject.of(AtomicLuaFunction.forOneResult((vm, t) -> {
            if (!t.isTable())
                return LuaObject.NIL;

            var mt = t.getMetaTable();
            if (mt.isNil())
                return mt;

            return mt.get(LuaObject.of("__metatable"));
        })));
        rv.set("rawequal", LuaObject.of(AtomicLuaFunction.forOneResult((vm, v1, v2) -> {
            var t1 = v1.getType();
            if (t1 == LuaObject.Types.LONG || t1 == LuaObject.Types.DOUBLE)
                t1 = LuaObject.Types.NUMBER;

            var t2 = v2.getType();
            if (t2 == LuaObject.Types.LONG || t2 == LuaObject.Types.DOUBLE)
                t2 = LuaObject.Types.NUMBER;

            return LuaObject.of(t1 == t2 && ((t1 == LuaObject.Types.NUMBER || t1 == LuaObject.Types.STRING) ? v1.eq(v2).getBool() : v1 == v2));
        })));
        rv.set("rawget", LuaObject.of(AtomicLuaFunction.forOneResult((vm, tbl, k) -> {
            if (!tbl.isTable()) {
                vm.error(new LuaArgumentError(0, "rawget", "table expected, got %s".formatted(tbl.getTypeAsString())));
                return null;
            }
            if (k.isNil() || k.isDouble() && Double.isNaN(k.asDouble()))
                return null;

            tbl.get(k); // dont call a metamethod, this is a 'raw' function
            return tbl;
        })));
        rv.set("rawlen", LuaObject.of(AtomicLuaFunction.forOneResult((vm, v) -> {
            if (!v.isTable() && !v.isString()) {
                vm.error(new LuaArgumentError(0, "rawlen", "table or string expected, got %s".formatted(v.getTypeAsString())));
                return null;
            }
            return v.len();
        })));
        rv.set("rawset", LuaObject.of(AtomicLuaFunction.forOneResult((vm, tbl, k, v) -> {
            if (!tbl.isTable()) {
                vm.error(new LuaArgumentError(0, "rawset", "table expected, got %s".formatted(tbl.getTypeAsString())));
                return null;
            }
            if (errorIfTableIndexNilOrNaN(vm, k))
                return null;

            tbl.set(k, v); // dont call a metamethod, this is a 'raw' function
            return tbl;
        })));
        rv.set("select", LuaObject.of(AtomicLuaFunction.vaForManyResults((vm, args) -> {
            if (args.length == 0){
                vm.error(new LuaArgumentError(0, "select", "number or \"#\" expected, got no value"));
            }

            var idx = args[0];
            if (idx.isNumber())
            {
                if (!idx.isLong()){
                    vm.error(new LuaArgumentError(0, "select", "number has no integer representation"));
                    return null;
                }
                return Arrays.stream(args).skip(idx.asLong()).toArray(LuaObject[]::new);
            }
            else {
                if (!(idx.isString()  && idx.asString().equals("#"))){
                    vm.error(new LuaArgumentError(0, "select", "number or \"#\" expected, got %s".formatted(idx.getTypeAsString())));
                    return null;
                }
                // must be "#" then
                return new LuaObject[]{LuaObject.of(args.length-1)};
            }
        })));
        rv.set("setmetatable", LuaObject.of(AtomicLuaFunction.forOneResult((vm, tbl, mt) -> {
            if (!tbl.isTable()) {
                vm.error(new LuaArgumentError(0, "setmetatable", "table expected, got %s".formatted(tbl.getTypeAsString())));
                return null;
            }
            if (!mt.isTable()) {
                vm.error(new LuaArgumentError(1, "setmetatable", "table or nil expected, got %s".formatted(tbl.getTypeAsString())));
                return null;
            }

            if(!tbl.getMetaTableValueOrNull("__metatable").isNil()){
                vm.error(new LuaUserError("cannot change a protected metatable"));
            }

            var existingMt = tbl.getMetaTable();
            if (!existingMt.isNil())
                return mt;
            tbl.setMetatable(mt);

            return tbl;
        })));
//        rv.set("tostring", LuaObject.of(AtomicLuaFunction.forOneResult((vm, x) -> {
//            var mtFunc = x.getMetaTableValueOrNull("__tostring");
//            return LuaObject.of(mtFunc != null ? mtFunc.invoke() : x.asString());
//        }))); // TODO rewrite this to make it work with the vm logic as it is not an atomic function
        rv.set("type", LuaObject.of(AtomicLuaFunction.forOneResult((vm, x) -> LuaObject.of(x.getTypeAsString()))));

        rv.set("_VERSION", LuaObject.of("Lua 5.4"));
        rv.set("_G", rv);
        return rv;
    }
}

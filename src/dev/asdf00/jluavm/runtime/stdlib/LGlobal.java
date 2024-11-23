package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.errors.LuaArgumentError;
import dev.asdf00.jluavm.runtime.errors.LuaUserError;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;

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
        load
        next (partially implemented)
        pairs
        pcall
        print
        tonumber
        warn
        xpcall
         */
        rv.set("ipairs", ipairs);
        rv.set("pairs", pairs);
        rv.set("next", next);

        rv.set("assert", AtomicLuaFunction.vaForManyResults((vm, params) -> {
            if (params.length == 0) {
                vm.error(new LuaArgumentError(0, "assert", "value expected"));
                return null;
            }

            if (params[0].isTruthy())
                return params;

            // LUAC DEVIATION. Our assertion returns tostring(msg) instead of just the type of that argument for nil and boolean
            vm.error(new LuaUserError(params.length < 2 ? "assertion failed!" : params[1].asString()));
            return null;
        }).obj());
        rv.set("getmetatable", AtomicLuaFunction.forOneResult((vm, t) -> {
            if (!t.isTable())
                return LuaObject.NIL;

            var mt = t.getMetaTable();
            if (mt.isNil())
                return mt;

            return mt.get(LuaObject.of("__metatable"));
        }).obj());
        rv.set("rawequal", AtomicLuaFunction.forOneResult((vm, v1, v2) -> {
            var t1 = v1.getType();
            if (t1 == LuaObject.Types.LONG || t1 == LuaObject.Types.DOUBLE)
                t1 = LuaObject.Types.NUMBER;

            var t2 = v2.getType();
            if (t2 == LuaObject.Types.LONG || t2 == LuaObject.Types.DOUBLE)
                t2 = LuaObject.Types.NUMBER;

            return LuaObject.of(t1 == t2 && ((t1 == LuaObject.Types.NUMBER || t1 == LuaObject.Types.STRING) ? v1.eq(v2).getBool() : v1 == v2));
        }).obj());
        rv.set("rawget", AtomicLuaFunction.forOneResult((vm, tbl, k) -> {
            if (!tbl.isTable()) {
                vm.error(new LuaArgumentError(0, "rawget", "table expected, got %s".formatted(tbl.getTypeAsString())));
                return null;
            }
            if (k.isNil() || k.isDouble() && Double.isNaN(k.asDouble()))
                return null;

            tbl.get(k); // dont call a metamethod, this is a 'raw' function
            return tbl;
        }).obj());
        rv.set("rawlen", AtomicLuaFunction.forOneResult((vm, v) -> {
            if (!v.isTable() && !v.isString()) {
                vm.error(new LuaArgumentError(0, "rawlen", "table or string expected, got %s".formatted(v.getTypeAsString())));
                return null;
            }
            return v.len();
        }).obj());
        rv.set("rawset", AtomicLuaFunction.forOneResult((vm, tbl, k, v) -> {
            if (!tbl.isTable()) {
                vm.error(new LuaArgumentError(0, "rawset", "table expected, got %s".formatted(tbl.getTypeAsString())));
                return null;
            }
            if (errorIfTableIndexNilOrNaN(vm, k))
                return null;

            tbl.set(k, v); // dont call a metamethod, this is a 'raw' function
            return tbl;
        }).obj());
        rv.set("select", AtomicLuaFunction.vaForManyResults((vm, args) -> {
            if (args.length == 0) {
                vm.error(new LuaArgumentError(0, "select", "number or \"#\" expected, got no value"));
            }

            var idx = args[0];
            if (idx.isNumber()) {
                if (!idx.isLong()) {
                    vm.error(new LuaArgumentError(0, "select", "number has no integer representation"));
                    return null;
                }
                return Arrays.stream(args).skip(idx.asLong()).toArray(LuaObject[]::new);
            } else {
                if (!(idx.isString() && idx.asString().equals("#"))) {
                    vm.error(new LuaArgumentError(0, "select", "number or \"#\" expected, got %s".formatted(idx.getTypeAsString())));
                    return null;
                }
                // must be "#" then
                return new LuaObject[]{LuaObject.of(args.length - 1)};
            }
        }).obj());
        rv.set("setmetatable", AtomicLuaFunction.forOneResult((vm, tbl, mt) -> {
            if (!tbl.isTable()) {
                vm.error(new LuaArgumentError(0, "setmetatable", "table expected, got %s".formatted(tbl.getTypeAsString())));
                return null;
            }
            if (!mt.isTable()) {
                vm.error(new LuaArgumentError(1, "setmetatable", "table or nil expected, got %s".formatted(tbl.getTypeAsString())));
                return null;
            }

            if (!tbl.getMetaTableValueOrNil("__metatable").isNil()) {
                vm.error(new LuaUserError("cannot change a protected metatable"));
            }

            var existingMt = tbl.getMetaTable();
            if (existingMt != null && !existingMt.isNil())
                return mt;
            tbl.setMetatable(mt);

            return tbl;
        }).obj());
        rv.set("tostring", tostring);
        rv.set("type", AtomicLuaFunction.forOneResult((vm, x) -> LuaObject.of(x.getTypeAsString())).obj());

        rv.set("_VERSION", LuaObject.of("Lua 5.4"));
        rv.set("_G", rv);
        return rv;
    }

    // =================================================================================================================
    //  static function definitions
    // =================================================================================================================

    private static final LuaObject tostring = LuaObject.of(new LuaFunction() {
        @Override
        public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
            LuaObject t0 = null;
            if (resume == -1) {
                vm.registerLocals(1);
            }
            var arg = stackFrame[0];
            switch (resume) {
                case -1:
                    if (!arg.isTable() || arg.getMetaTable() == null) { // if this is not a table that also has a metatable attached, simply return the easy .asString()
                        vm.returnValue(LuaObject.of(arg.asString()));
                        return;
                    }
                    // otherwise, try to find a __tostring value and then call it, whatever it is, including a table that has a metatable attached that has __call defined
                    // otherwise, **if __tostring is undefined or nil** and __name is defined, return $"{__name}: {arg.asString()}"
                    // otherwise, if there is also no __name, return the easy .asString()

                    var tostring = arg.getMetaTableValueOrNil("__tostring");
                    if (tostring.isNil()) { // we dont have a tostring field --> cook something up using __name, or without that
                        var name = arg.getMetaTableValueOrNil("__name");
                        vm.returnValue(LuaObject.of(!name.isNil() && name.isString() ? name.getString() + ": " + arg.asString() : arg.asString()));
                        return;
                    }
                    // otherwise simply call it and see what happens
                    if (tostring.isFunction())
                        vm.tailCall(tostring.getFunc(), arg);
                    else
                        vm.callInternal(0, LuaFunction::callWithMeta, tostring, arg);
                    return;
                case 0:
                    var rval = returned.length > 0 ? returned[0] : LuaObject.nil();;
                    vm.returnValue(rval);
                    return;
                default:
                    throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
        }

        @Override
        public int getMaxLocalsSize() {
            return 1;
        }

        @Override
        public int getArgCount() {
            return 1;
        }

        @Override
        public boolean hasParamsArg() {
            return false;
        }
    });

    private static final LuaObject ipairs = LuaObject.of(new LuaFunction() {
        @Override
        public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
            LuaObject t0 = null;
            if (resume == -1) {
                vm.registerLocals(1);
            } else if (resume == 0) {
                t0 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
            switch (resume) {
                case -1:
                    var tbl = stackFrame[0];
                    if (!tbl.isTable() || tbl.getMetaTable() == null) {
                        vm.returnValue(standardReturn, stackFrame[0], LuaObject.of(0));
                        return;
                    }
                    var mtbl = tbl.getMetaTable();
                    t0 = indexedGet(vm, 0, mtbl, Singletons.__ipairs);
                    if (t0 == null) {
                        return;
                    }
                case 0:
                    if (!t0.isFunction()) {
                        vm.returnValue(standardReturn, stackFrame[0], LuaObject.of(0));
                        return;
                    }
                    vm.tailCall(t0.getFunc(), stackFrame[0]);
                    return;
                default:
                    throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
        }

        @Override
        public int getMaxLocalsSize() {
            return 1;
        }

        @Override
        public int getArgCount() {
            return 1;
        }

        @Override
        public boolean hasParamsArg() {
            return false;
        }

        private final static LuaObject standardReturn = LuaObject.of(AtomicLuaFunction.forManyResults((itrVm, myTbl, ctrl) -> {
            if (!myTbl.isTable()) {
                // LUAC DEVIATION. We exclusively allow tables here, luac just behaves normally on tables, returns nil on
                // strings and errors on all other types. We error on all types but tables.
                itrVm.error(new LuaArgumentError(0, "ipairs$iterator", "table expected"));
                return null;
            }
            if (!ctrl.isNumberCoercible()) {
                itrVm.error(new LuaArgumentError(0, "ipairs$iterator", "number expected"));
                return null;
            }
            var nextIdx = ctrl.add(LuaObject.of(1));
            return myTbl.hasKey(nextIdx) && !myTbl.get(nextIdx).isNil() ? new LuaObject[]{nextIdx, myTbl.get(nextIdx)} : new LuaObject[]{LuaObject.nil()};
        }));
    });

    private static final LuaObject pairs = LuaObject.of(new LuaFunction() {
        @Override
        public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
            LuaObject t0 = null;
            if (resume == -1) {
                vm.registerLocals(1);
            } else if (resume == 0) {
                t0 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
            switch (resume) {
                case -1:
                    var tbl = stackFrame[0];
                    if (!tbl.isTable() || tbl.getMetaTable() == null) {
                        vm.returnValue(next, stackFrame[0], LuaObject.nil());
                        return;
                    }
                    var mtbl = tbl.getMetaTable();
                    t0 = indexedGet(vm, 0, mtbl, Singletons.__pairs);
                    if (t0 == null) {
                        return;
                    }
                case 0:
                    if (!t0.isFunction()) {
                        vm.returnValue(next, stackFrame[0], LuaObject.nil());
                        return;
                    }
                    vm.tailCall(t0.getFunc(), stackFrame[0]);
                    return;
                default:
                    throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
        }

        @Override
        public int getMaxLocalsSize() {
            return 1;
        }

        @Override
        public int getArgCount() {
            return 1;
        }

        @Override
        public boolean hasParamsArg() {
            return false;
        }
    });

    private static final LuaObject next = AtomicLuaFunction.forManyResults(((vm, table, index) -> {
        if (!table.isTable()) {
            vm.error(new LuaArgumentError(0, "ipairs$iterator", "table expected"));
            return null;
        }
        var tbl = table.asMap();
        LuaObject nidx;
        if (index.isNil()) {
            nidx = tbl.getFirstKey();
        } else {
            if (!tbl.containsKey(index)) {
                vm.error(new LuaArgumentError());
                return null;
            }
            nidx = tbl.getKeyAfter(index);
        }
        return nidx.isNil() ? new LuaObject[]{LuaObject.nil(), LuaObject.nil()} : new LuaObject[]{nidx, table.get(nidx)};
    })).obj();
}

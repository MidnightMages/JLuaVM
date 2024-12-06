package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.runtime.errors.LuaArgumentError;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import static dev.asdf00.jluavm.runtime.types.LuaObject.Types.*;
import static dev.asdf00.jluavm.runtime.utils.RTUtils.funcArgAnyTypeError;
import static dev.asdf00.jluavm.runtime.utils.RTUtils.funcArgTypeError;

public class LTable {

    // https://www.lua.org/manual/5.4/manual.html#6.6
    public static LuaObject getTable() {
        var rv = LuaObject.table();
        rv.set("concat", AtomicLuaFunction.vaForOneResult((vm, args) -> {
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
                vm.error(new LuaArgumentError(3, "concat", "number, nil or nothing"));
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
                sb.append(elem);
            }

            return LuaObject.of(sb.toString());
        }).obj());

        // TODO add table.insert

        // TODO add table.move

        rv.set("pack", AtomicLuaFunction.vaForOneResult((vm, args) -> {
            var t = LuaObject.table();
            for (int i = 0; i < args.length; i++) {
                t.set(LuaObject.of(i + 1), args[i]);
            }
            t.set("n", LuaObject.of(args.length));
            return t;
        }).obj());

        // TODO add table.remove

        // TODO add table.sort

        rv.set("unpack", AtomicLuaFunction.vaForManyResults((vm, args) -> {
            var tbl = args.length < 1 ? null : args[0];
            if (tbl == null || !tbl.isTable()) {
                vm.error(funcArgTypeError("table.unpack", 0, tbl, "table"));
                return null;
            }
            assert tbl != null;

            var i = args.length < 2 ? 0 : (args[2].hasLongRepr() ? args[2].asLong() : -1);
            if (i < 0) {
                vm.error(funcArgAnyTypeError("table.unpack", 1, args.length > 1 ? args[1] : null, "number", "nil", "nothing"));
                return null;
            }

            // TODO call the __len metamethod instead if it exists
            var j = args.length < 3 ? tbl.len().asLong() : (args[3].hasLongRepr() ? args[3].asLong() : -1);
            if (j < 0) {
                vm.error(funcArgAnyTypeError("table.unpack", 2, args.length > 2 ? args[2] : null, "number", "nil", "nothing"));
                return null;
            }

            var t = new LuaObject[(int) (j - i + 1)];
            int arrIdx = 0;
            for (int k = ((int) i); k <= j; k++) {
                var idx = k - 1;
                t[arrIdx++] = idx >= 0 && idx < args.length ? args[idx] : LuaObject.NIL;
            }
            return t;
        }).obj());

        return rv;
    }
}

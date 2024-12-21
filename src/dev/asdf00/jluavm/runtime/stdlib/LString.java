package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Locale;

import static dev.asdf00.jluavm.runtime.types.LuaObject.Types.*;
import static dev.asdf00.jluavm.runtime.utils.RTUtils.funcArgAnyTypeError;
import static dev.asdf00.jluavm.runtime.utils.RTUtils.funcArgTypeError;

public class LString {
    public static LuaObject getTable() {
        var rv = LuaObject.table();

        /* TODO, add the following ones: https://www.lua.org/manual/5.4/manual.html#6.4
        string.dump(function [, strip])
        string.find(s, pattern [, init [, plain]])
        string.format(formatstring, ···)
        string.gmatch(s, pattern [, init])
        string.gsub(s, pattern, repl [, n])
        string.match(s, pattern [, init])
        string.pack(fmt, v1, v2, ···)
        string.packsize(fmt)
        string.unpack(fmt, s [, pos])
         */
        rv.set("byte", AtomicLuaFunction.vaForOneResult((vm, va) -> {
            if (va.length < 1 || !va[0].isType(ARITHMETIC)) {
                vm.error(funcArgTypeError("string.byte", 0, va.length > 0 ? va[0] : null, "string"));
                return null;
            }

            if (va.length > 1 && !va[1].hasLongRepr()) {
                vm.error(funcArgTypeError("string.byte", 1, va[1], "integer"));
                return null;
            }

            if (va.length > 2 && !va[2].isNil() && !va[2].hasLongRepr()) {
                vm.error(funcArgAnyTypeError("string.byte", 2, va[2], "integer", "nil", "nothing"));
                return null;
            }
            return sub(vm, va);
        }).obj());
        rv.set("char", AtomicLuaFunction.vaForOneResult((vm, va) -> {
            var r2 = new StringBuilder(va.length);
            for (int i = 0; i < va.length; i++) {
                if (va[i].isNumberCoercible()) {
                    if (va[i].hasLongRepr()) {
                        r2.append(va[i].asLong());
                    } else {
                        vm.error(funcArgTypeError("string.char", i, va[i], "integer"));
                        return null;
                    }
                } else {
                    vm.error(funcArgTypeError("string.char", i, va[i], "integer"));
                    return null;
                }
            }
            return LuaObject.of(r2.toString());
        }).obj());
        rv.set("len", AtomicLuaFunction.forOneResult((vm, s) -> {
            if (!s.isType(ARITHMETIC)) {
                vm.error(funcArgTypeError("string.len", 0, s, "string"));
                return null;
            }
            return LuaObject.of(s.asString().length());
        }).obj());
        rv.set("lower", AtomicLuaFunction.forOneResult((vm, s) -> {
            if (!s.isType(ARITHMETIC)) {
                vm.error(funcArgTypeError("string.lower", 0, s, "string"));
                return null;
            }
            return LuaObject.of(s.asString().toLowerCase(Locale.US));
        }).obj());
        rv.set("rep", AtomicLuaFunction.vaForOneResult((vm, va) -> {
            if (va.length < 1 || !va[0].isType(ARITHMETIC)) {
                vm.error(funcArgTypeError("string.rep", 0, va.length > 0 ? va[0] : null, "string"));
                return null;
            }

            if (va.length < 2 || !va[1].hasLongRepr()) {
                vm.error(funcArgTypeError("string.rep", 1, va[1], "integer"));
                return null;
            }

            //noinspection All
            assert va.length >= 2;

            var sep = va.length > 2 ? va[2].asString() : "";
            if (va.length > 2 && !va[2].isType(ARITHMETIC | BOOLEAN | NIL)) {
                vm.error(funcArgAnyTypeError("string.rep", 2, va[2],
                        "string", "boolean", "integer", "nil", "nothing"));
                return null;
            }
            var cnt = (int) (va[1].asLong());
            if (cnt <= 0)
                return LuaObject.of("");
            var s = va[0].asString();
            return LuaObject.of(s + (sep + s).repeat(cnt - 1));
        }).obj());
        rv.set("reverse", AtomicLuaFunction.forOneResult((vm, s) -> {
            if (!s.isType(ARITHMETIC)) {
                vm.error(funcArgTypeError("string.reverse", 0, s, "string"));
                return null;
            }
            return LuaObject.of(new StringBuilder(s.asString()).reverse().toString());
        }).obj());
        rv.set("sub", AtomicLuaFunction.vaForOneResult(LString::sub).obj());
        rv.set("upper", AtomicLuaFunction.forOneResult((vm, s) -> {
            if (!s.isType(ARITHMETIC)) {
                vm.error(funcArgTypeError("string.upper", 0, s, "string"));
                return null;
            }
            return LuaObject.of(s.asString().toUpperCase(Locale.US));
        }).obj());
        return rv;
    }

    public static LuaObject getExtTable() {
        var rv = getTable();
        rv.set("char", LuaObject.NIL);
        return rv;
    }

    // =================================================================================================================
    //  static function definitions
    // =================================================================================================================
    private static LuaObject sub(LuaVM_RT vm, LuaObject[] va) {// TODO add unittests
        if (va.length < 1 || !va[0].isType(ARITHMETIC)) {
            vm.error(funcArgTypeError("string.sub", 0, va.length > 0 ? va[0] : null, "string"));
            return null;
        }

        if (va.length < 2 || !va[1].hasLongRepr()) {
            vm.error(funcArgTypeError("string.sub", 1, va[1], "integer"));
            return null;
        }

        //noinspection All
        assert va.length >= 2;

        if (va.length > 2 && !va[2].isNil() && !va[2].hasLongRepr()) {
            vm.error(funcArgAnyTypeError("string.sub", 2, va[2], "integer", "nil", "nothing"));
            return null;
        }
        var s = va[0].asString();
        var i = va[1].asLong();

        if (i < 0) i += s.length() + 1; // i=-1 must be same as string length, i.e (-1)+1+s.length() = s.length()
        if (i < 1) i = 1;

        var j = va.length > 2 && !va[2].isNil() ? va[2].asLong() : -1;
        if (j < 0) j += s.length() + 1; // i=-1 must be same as string length, i.e (-1)+1+s.length() = s.length()
        if (j > s.length()) j = s.length();
        if (i > j)
            return LuaObject.of("");

        return LuaObject.of(s.substring((int) i - 1, (int) j));
    }

}

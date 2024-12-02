package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.errors.LuaArgumentError;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Locale;

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
            if (va.length < 1 || !va[0].isType(LuaObject.Types.ARITHMETIC)) {
                vm.error(new LuaArgumentError(0, "byte", "string expected, got %s".formatted(va.length < 1 ? "no value" : va[0].getTypeAsString())));
                return null;
            }

            if (va.length > 1 && !va[1].hasLongRepr()) {
                vm.error(new LuaArgumentError(1, "byte", "integer expected, got %s".formatted(va[1].getTypeAsString())));
                return null;
            }

            if (va.length > 2 && !va[2].isNil() && !va[2].hasLongRepr()) {
                vm.error(new LuaArgumentError(2, "byte", "integer, nil or no value expected, got %s".formatted(va[2].getTypeAsString())));
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
                        vm.error(new LuaArgumentError(i, "char", "number has no integer representation"));
                        return null;
                    }
                } else {
                    vm.error(new LuaArgumentError(i, "char", "number expected, got %s".formatted(va[i].getTypeAsString())));
                    return null;
                }
            }
            return LuaObject.of(r2.toString());
        }).obj());
        rv.set("len", AtomicLuaFunction.forOneResult((vm, s) -> {
            if (!s.isType(LuaObject.Types.ARITHMETIC)) {
                vm.error(new LuaArgumentError(0, "len", "string expected, got %s".formatted(s.getTypeAsString())));
                return null;
            }
            return LuaObject.of(s.asString().length());
        }).obj());
        rv.set("lower", AtomicLuaFunction.forOneResult((vm, s) -> {
            if (!s.isType(LuaObject.Types.ARITHMETIC)) {
                vm.error(new LuaArgumentError(0, "lower", "string expected, got %s".formatted(s.getTypeAsString())));
                return null;
            }
            return LuaObject.of(s.asString().toLowerCase(Locale.US));
        }).obj());
        rv.set("rep", AtomicLuaFunction.vaForOneResult((vm, va) -> {
            if (va.length < 1 || !va[0].isType(LuaObject.Types.ARITHMETIC)) {
                vm.error(new LuaArgumentError(0, "rep", "string expected, got %s".formatted(va.length < 1 ? "no value" : va[0].getTypeAsString())));
                return null;
            }

            if (va.length < 2 || !va[1].hasLongRepr()) {
                vm.error(new LuaArgumentError(1, "rep", "integer expected, got %s".formatted(va.length < 2 ? "no value" : va[1].getTypeAsString())));
                return null;
            }

            //noinspection All
            assert va.length >= 2;

            if (va.length > 2 && !va[2].isNil() && !va[2].hasLongRepr()) {
                vm.error(new LuaArgumentError(2, "rep", "string, nil or no value expected, got %s".formatted(va[2].getTypeAsString())));
                return null;
            }
            var cnt = (int) (va[1].asLong());
            if (cnt <= 0)
                return LuaObject.of("");
            var s = va[0].asString();
            var sep = va[2] == null || !va[2].isNil() ? "" : va[2].asString();
            return LuaObject.of(s + (sep + s).repeat(cnt - 1));
        }).obj());
        rv.set("reverse", AtomicLuaFunction.forOneResult((vm, s) -> {
            if (!s.isType(LuaObject.Types.ARITHMETIC)) {
                vm.error(new LuaArgumentError(0, "reverse", "string expected, got %s".formatted(s.getTypeAsString())));
                return null;
            }
            return LuaObject.of(new StringBuilder(s.asString()).reverse().toString());
        }).obj());
        rv.set("sub", AtomicLuaFunction.vaForOneResult(LString::sub).obj());
        rv.set("upper", AtomicLuaFunction.forOneResult((vm, s) -> {
            if (!s.isType(LuaObject.Types.ARITHMETIC)) {
                vm.error(new LuaArgumentError(0, "upper", "string expected, got %s".formatted(s.getTypeAsString())));
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
        if (va.length < 1 || !va[0].isType(LuaObject.Types.ARITHMETIC)) {
            vm.error(new LuaArgumentError(0, "sub", "string expected, got %s".formatted(va.length < 1 ? "no value" : va[0].getTypeAsString())));
            return null;
        }

        if (va.length < 2 || !va[1].hasLongRepr()) {
            vm.error(new LuaArgumentError(1, "sub", "integer expected, got %s".formatted(va.length < 2 ? "no value" : va[1].getTypeAsString())));
            return null;
        }

        //noinspection All
        assert va.length >= 2;

        if (va.length > 2 && !va[2].isNil() && !va[2].hasLongRepr()) {
            vm.error(new LuaArgumentError(2, "sub", "integer, nil or no value expected, got %s".formatted(va[2].getTypeAsString())));
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

        return LuaObject.of(s.substring((int) i-1, (int) j));
    }

}

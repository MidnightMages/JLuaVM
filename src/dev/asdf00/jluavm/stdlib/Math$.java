package dev.asdf00.jluavm.stdlib;

import dev.asdf00.jluavm.exceptions.runtime.LuaTypeError$;
import dev.asdf00.jluavm.internals.LuaVM_RT$;
import dev.asdf00.jluavm.types.LuaBoolean$;
import dev.asdf00.jluavm.types.LuaNil$;
import dev.asdf00.jluavm.types.LuaNumber$;
import dev.asdf00.jluavm.types.LuaVariable$;

public class Math$ {
    private static LuaNumber$ toNumberOrFail(LuaVM_RT$ vm, LuaVariable$ xx) {
        return toNumberOrFail(vm, xx, 1);
    }

    private static LuaNumber$ toNumberOrFail(LuaVM_RT$ vm, LuaVariable$ xx, int argIdx) {
        if (xx.isNumber()) {
            return (LuaNumber$) xx;
        }
        throw vm.yeet(new LuaTypeError$("Argument at pos %s must be a number but was %s.".formatted(argIdx, xx.getType().fancyName)));
    }

    private static LuaNumber$ toIntOrFail(LuaVM_RT$ vm, LuaVariable$ xx, int argIdx) {
        var x2 = toNumberOrFail(vm, xx, argIdx);
        var x2v = x2.getValue();
        if (x2v == Math.round(x2v)) {
            return x2;
        }
        throw vm.yeet(new LuaTypeError$("Argument at pos %s must contain an integer number, but contained the value %s.".formatted(argIdx, x2v)));
    }

    public static LuaNumber$ abs(LuaVM_RT$ vm, LuaVariable$ x) {
        var val = toNumberOrFail(vm, x);
        return val.getValue() < 0 ? LuaNumber$.of(-val.getValue()) : val;
    }

    public static LuaNumber$ acos(LuaVM_RT$ vm, LuaVariable$ x) {
        return LuaNumber$.of(Math.acos(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumber$ asin(LuaVM_RT$ vm, LuaVariable$ x) {
        return LuaNumber$.of(Math.asin(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumber$ atan(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        var y2 = y.isNil() ? 1 : toNumberOrFail(vm, y, 2).getValue();
        return LuaNumber$.of(Math.atan2(y2, toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumber$ ceil(LuaVM_RT$ vm, LuaVariable$ x) {
        var res = Math.ceil(toNumberOrFail(vm, x).getValue());
        return LuaNumber$.of(res == -0d ? +0d : res);
    }

    public static LuaNumber$ cos(LuaVM_RT$ vm, LuaVariable$ x) {
        return LuaNumber$.of(Math.cos(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumber$ deg(LuaVM_RT$ vm, LuaVariable$ x) {
        return LuaNumber$.of(Math.toDegrees(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumber$ exp(LuaVM_RT$ vm, LuaVariable$ x) {
        return LuaNumber$.of(Math.exp(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumber$ floor(LuaVM_RT$ vm, LuaVariable$ x) {
        var res = Math.floor(toNumberOrFail(vm, x).getValue());
        return LuaNumber$.of(res == -0d ? +0d : res);
    }

//    public static LuaNumber$ fmod(LuaVM_RT$ vm, LuaVariable$ x) { // todo return int if args are int, else float
//        return LuaNumber$.of(Math.floorMod(toNumberOrFail(vm, x).getValue()));
//    }

    public static LuaNumber$ huge = LuaNumber$.of(Float.POSITIVE_INFINITY);

    public static LuaNumber$ log(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ base) {
        var customBase = base.isNil();
        var lres = Math.log(toNumberOrFail(vm, x).getValue());
        return LuaNumber$.of(customBase ? lres / Math.log(toNumberOrFail(vm, base, 2).getValue()) : lres);
    }

    public static LuaNumber$ max(LuaVM_RT$ vm, LuaVariable$... x) { // todo figure this one out with varargs
        throw new UnsupportedOperationException("not implemented");
    }

    public static LuaNumber$ maxinteger = LuaNumber$.of(Integer.MAX_VALUE); // todo maybe have int-lua-numbers?

    public static LuaNumber$ min(LuaVM_RT$ vm, LuaVariable$... x) { // todo figure this one out with varargs
        throw new UnsupportedOperationException("not implemented");
    }

    public static LuaNumber$ mininteger = LuaNumber$.of(Integer.MIN_VALUE); // todo maybe have int-lua-numbers?

    public static LuaNumber$[] modf(LuaVM_RT$ vm, LuaVariable$ x) {
        var x2 = toNumberOrFail(vm, x).getValue();
        var i = x2 < 0 ? Math.ceil(x2) : Math.floor(x2); // round towards 0
        return new LuaNumber$[]{LuaNumber$.of(i), LuaNumber$.of(x2 - i)}; // todo make first returnvalue an integer if x is an integer. (2nd is always float)
    }

    public static LuaNumber$ pi = LuaNumber$.of(Math.PI);

    public static LuaNumber$ rad(LuaVM_RT$ vm, LuaVariable$ x) {
        return LuaNumber$.of(Math.toRadians(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumber$ random(LuaVM_RT$ vm, LuaVariable$ intmin, LuaVariable$ intmax) { // todo figure this one with different arg counts as technically nil is different from unsupplied
        throw new UnsupportedOperationException("not implemented");
    }

    public static LuaNumber$ randomseed(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) { // todo figure this one with different arg counts as technically nil is different from unsupplied
        throw new UnsupportedOperationException("not implemented");
    }

    public static LuaNumber$ sin(LuaVM_RT$ vm, LuaVariable$ x) {
        return LuaNumber$.of(Math.sin(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumber$ sqrt(LuaVM_RT$ vm, LuaVariable$ x) {
        return LuaNumber$.of(Math.sqrt(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumber$ tan(LuaVM_RT$ vm, LuaVariable$ x) {
        return LuaNumber$.of(Math.tan(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaVariable$ tointeger(LuaVM_RT$ vm, LuaVariable$ x) {
        var x2 = toNumberOrFail(vm, x).getValue();
        return Math.round(x2) == x2 ? LuaNumber$.of(x2) : LuaNil$.singleton;
    }

    public static LuaVariable$ type(LuaVM_RT$ vm, LuaVariable$ x) { // todo figure this one with different number types
        throw new UnsupportedOperationException("not implemented"); // return "integer" if int, "float" if float, else nil if it is not a number
    }

    public static LuaBoolean$ ult(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) { // return true iff m<n when treating as unsigned int. Throw if either are floats
        var x2 = toIntOrFail(vm, x, 1);
        var y2 = toIntOrFail(vm, y, 2);
        return LuaBoolean$.fromState(Long.compareUnsigned((long) x2.getValue(), (long) y2.getValue()) < 0); // todo write tests for this lol
    }
}

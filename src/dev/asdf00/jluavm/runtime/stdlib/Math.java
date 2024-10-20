package dev.asdf00.jluavm.runtime.stdlib;

// TODO: rewrite for new LuaFunction class compatibility
/*-

import dev.asdf00.jluavm.exceptions.runtime.LuaTypeError$;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.typesOLD.LuaBooleanOLD;
import dev.asdf00.jluavm.runtime.typesOLD.LuaNilOLD;
import dev.asdf00.jluavm.runtime.typesOLD.LuaNumberOLD;
import dev.asdf00.jluavm.runtime.typesOLD.LuaVariableOLD;

public class Math {
    private static LuaNumberOLD toNumberOrFail(LuaVM_RT vm, LuaVariableOLD xx) {
        return toNumberOrFail(vm, xx, 1);
    }

    private static LuaNumberOLD toNumberOrFail(LuaVM_RT vm, LuaVariableOLD xx, int argIdx) {
        if (xx.isNumber()) {
            return (LuaNumberOLD) xx;
        }
        throw vm.yeet(new LuaTypeError$("Argument at pos %s must be a number but was %s.".formatted(argIdx, xx.getType().fancyName)));
    }

    private static LuaNumberOLD toIntOrFail(LuaVM_RT vm, LuaVariableOLD xx, int argIdx) {
        var x2 = toNumberOrFail(vm, xx, argIdx);
        var x2v = x2.getValue();
        if (x2v == Math.round(x2v)) {
            return x2;
        }
        throw vm.yeet(new LuaTypeError$("Argument at pos %s must contain an integer number, but contained the value %s.".formatted(argIdx, x2v)));
    }

    public static LuaNumberOLD abs(LuaVM_RT vm, LuaVariableOLD x) {
        var val = toNumberOrFail(vm, x);
        return val.getValue() < 0 ? LuaNumberOLD.of(-val.getValue()) : val;
    }

    public static LuaNumberOLD acos(LuaVM_RT vm, LuaVariableOLD x) {
        return LuaNumberOLD.of(Math.acos(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumberOLD asin(LuaVM_RT vm, LuaVariableOLD x) {
        return LuaNumberOLD.of(Math.asin(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumberOLD atan(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        var y2 = y.isNil() ? 1 : toNumberOrFail(vm, y, 2).getValue();
        return LuaNumberOLD.of(Math.atan2(y2, toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumberOLD ceil(LuaVM_RT vm, LuaVariableOLD x) {
        var res = Math.ceil(toNumberOrFail(vm, x).getValue());
        return LuaNumberOLD.of(res == -0d ? +0d : res);
    }

    public static LuaNumberOLD cos(LuaVM_RT vm, LuaVariableOLD x) {
        return LuaNumberOLD.of(Math.cos(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumberOLD deg(LuaVM_RT vm, LuaVariableOLD x) {
        return LuaNumberOLD.of(Math.toDegrees(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumberOLD exp(LuaVM_RT vm, LuaVariableOLD x) {
        return LuaNumberOLD.of(Math.exp(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumberOLD floor(LuaVM_RT vm, LuaVariableOLD x) {
        var res = Math.floor(toNumberOrFail(vm, x).getValue());
        return LuaNumberOLD.of(res == -0d ? +0d : res);
    }

//    public static LuaNumber$ fmod(LuaVM_RT$ vm, LuaVariable$ x) { // todo return int if args are int, else float
//        return LuaNumber$.of(Math.floorMod(toNumberOrFail(vm, x).getValue()));
//    }

    public static LuaNumberOLD huge = LuaNumberOLD.of(Float.POSITIVE_INFINITY);

    public static LuaNumberOLD log(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD base) {
        var customBase = base.isNil();
        var lres = Math.log(toNumberOrFail(vm, x).getValue());
        return LuaNumberOLD.of(customBase ? lres / Math.log(toNumberOrFail(vm, base, 2).getValue()) : lres);
    }

    public static LuaNumberOLD max(LuaVM_RT vm, LuaVariableOLD... x) { // todo figure this one out with varargs
        throw new UnsupportedOperationException("not implemented");
    }

    public static LuaNumberOLD maxinteger = LuaNumberOLD.of(Integer.MAX_VALUE); // todo maybe have int-lua-numbers?

    public static LuaNumberOLD min(LuaVM_RT vm, LuaVariableOLD... x) { // todo figure this one out with varargs
        throw new UnsupportedOperationException("not implemented");
    }

    public static LuaNumberOLD mininteger = LuaNumberOLD.of(Integer.MIN_VALUE); // todo maybe have int-lua-numbers?

    public static LuaNumberOLD[] modf(LuaVM_RT vm, LuaVariableOLD x) {
        var x2 = toNumberOrFail(vm, x).getValue();
        var i = x2 < 0 ? Math.ceil(x2) : Math.floor(x2); // round towards 0
        return new LuaNumberOLD[]{LuaNumberOLD.of(i), LuaNumberOLD.of(x2 - i)}; // todo make first returnvalue an integer if x is an integer. (2nd is always float)
    }

    public static LuaNumberOLD pi = LuaNumberOLD.of(Math.PI);

    public static LuaNumberOLD rad(LuaVM_RT vm, LuaVariableOLD x) {
        return LuaNumberOLD.of(Math.toRadians(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumberOLD random(LuaVM_RT vm, LuaVariableOLD intmin, LuaVariableOLD intmax) { // todo figure this one with different arg counts as technically nil is different from unsupplied
        throw new UnsupportedOperationException("not implemented");
    }

    public static LuaNumberOLD randomseed(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) { // todo figure this one with different arg counts as technically nil is different from unsupplied
        throw new UnsupportedOperationException("not implemented");
    }

    public static LuaNumberOLD sin(LuaVM_RT vm, LuaVariableOLD x) {
        return LuaNumberOLD.of(Math.sin(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumberOLD sqrt(LuaVM_RT vm, LuaVariableOLD x) {
        return LuaNumberOLD.of(Math.sqrt(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaNumberOLD tan(LuaVM_RT vm, LuaVariableOLD x) {
        return LuaNumberOLD.of(Math.tan(toNumberOrFail(vm, x).getValue()));
    }

    public static LuaVariableOLD tointeger(LuaVM_RT vm, LuaVariableOLD x) {
        var x2 = toNumberOrFail(vm, x).getValue();
        return Math.round(x2) == x2 ? LuaNumberOLD.of(x2) : LuaNilOLD.singleton;
    }

    public static LuaVariableOLD type(LuaVM_RT vm, LuaVariableOLD x) { // todo figure this one with different number types
        throw new UnsupportedOperationException("not implemented"); // return "integer" if int, "float" if float, else nil if it is not a number
    }

    public static LuaBooleanOLD ult(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) { // return true iff m<n when treating as unsigned int. Throw if either are floats
        var x2 = toIntOrFail(vm, x, 1);
        var y2 = toIntOrFail(vm, y, 2);
        return LuaBooleanOLD.fromState(Long.compareUnsigned((long) x2.getValue(), (long) y2.getValue()) < 0); // todo write tests for this lol
    }
}

 */

package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.api.lambdas.LLBiFunction;
import dev.asdf00.jluavm.api.lambdas.LLFunction;
import dev.asdf00.jluavm.api.lambdas.LLMultiFunction;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.function.Function;

public class LMath {
    private static LuaObject getIntNumberIfFits(double x) {
        return (double) ((long) x) == x ? LuaObject.of((long) x) : LuaObject.of(x);
    }

    public static LuaObject abs(LuaVM_RT vm, LuaObject x) {
        if (x.isNumber()) return x.asDouble() < 0 ? x.unm() : x;
        vm.errorArgType(0, "number", x);
        return null;
    }

    public static LuaObject atan(LuaVM_RT vm, LuaObject y, LuaObject xOrNil) {
        if (!y.isNumber()) {
            vm.errorArgType(0, "number", y);
            return null;
        }
        if (!(xOrNil.isNumber() || xOrNil.isNil())) {
            vm.errorArgType(1, "number or nil", xOrNil);
            return null;
        }

        return LuaObject.of(Math.atan2(y.asDouble(), xOrNil.isNil() ? 1 : xOrNil.asDouble()));
    }

    public static LuaObject ceil(LuaVM_RT vm, LuaObject x) {
        if (!x.isNumber()) {
            vm.errorArgType(0, "number", x);
            return null;
        }
        return getIntNumberIfFits(Math.ceil(x.asDouble()));
    }

    public static LuaObject floor(LuaVM_RT vm, LuaObject x) {
        if (!x.isNumber()) {
            vm.errorArgType(0, "number", x);
            return null;
        }
        return getIntNumberIfFits(Math.floor(x.asDouble()));
    }

    public static LuaObject fmod(LuaVM_RT vm, LuaObject x, LuaObject y) {
        if (!x.isNumber()) {
            vm.errorArgType(0, "number", x);
            return null;
        }
        if (!y.isNumber()) {
            vm.errorArgType(1, "number", y);
            return null;
        }

        var anyDouble = x.isDouble() || y.isDouble();
        return LuaObject.of(anyDouble ? (x.asDouble() % y.asDouble()) : (x.asLong() % y.asLong()));
    }

    private static LuaObject log(LuaVM_RT vm, LuaObject x, LuaObject baseOrNil) {
        if (!x.isNumber()) {
            vm.errorArgType(0, "number", x);
            return null;
        }
        if (!(baseOrNil.isNumber() | baseOrNil.isNil())) {
            vm.errorArgType(1, "number or nil", baseOrNil);
            return null;
        }

        return LuaObject.of(baseOrNil.isNil() ? Math.log(x.asDouble()) : (Math.log(x.asDouble()) / (Math.log(baseOrNil.asDouble()))));
    }

    public static LuaObject[] modf(LuaVM_RT vm, LuaObject x) {
        if (x.isNumber()) {
            var dx = x.asDouble();
            var fullPart = dx < 0 ? Math.ceil(dx) : Math.floor(dx);
            var fracPart = dx - fullPart;
            return new LuaObject[]{getIntNumberIfFits(fullPart), LuaObject.of(fracPart)};
        }
        vm.errorArgType(0, "number", x);
        return null;
    }

    public static LuaObject ult(LuaVM_RT vm, LuaObject m, LuaObject n) {
        if (!m.isIntCoercible()) {
            vm.errorArgType(0, "integer", m);
            return null;
        }
        if (!n.isIntCoercible()) {
            vm.errorArgType(0, "integer", n);
            return null;
        }

        return LuaObject.of(m.asLong() < n.asLong());
    }

    private static LuaObject func1d1d(LuaVM_RT vm, LuaObject x, Function<Double, Double> f) {
        if (x.isNumber()) return LuaObject.of(f.apply(x.asDouble()));
        vm.errorArgType(0, "number", x);
        return null;
    }

    private static void addSingleRvFunc1d1d(LuaObject table, String name, Function<Double, Double> f) {
        table.set(name, AtomicLuaFunction.forOneResult((vm, x) -> func1d1d(vm, x, f)).obj());
    }

    private static void addSingleRvFunc(LuaObject table, String name, LLFunction f) {
        table.set(name, AtomicLuaFunction.forOneResult(f).obj());
    }

    private static void addSingleRvFunc2(LuaObject table, String name, LLBiFunction f) {
        table.set(name, AtomicLuaFunction.forOneResult(f).obj());
    }

    private static void addDualRvFunc(LuaObject table, String name, LLMultiFunction f) {
        table.set(name, AtomicLuaFunction.forManyResults(f).obj());
    }

    // https://www.lua.org/manual/5.4/manual.html#6.7
    public static LuaObject getTable() {
        var rv = LuaObject.table();
        addSingleRvFunc(rv, "abs", LMath::abs);
        addSingleRvFunc1d1d(rv, "acos", Math::acos);
        addSingleRvFunc1d1d(rv, "asin", Math::asin);
        addSingleRvFunc2(rv,"atan", LMath::atan);
        addSingleRvFunc(rv, "ceil", LMath::ceil);
        addSingleRvFunc1d1d(rv, "cos", Math::cos);
        addSingleRvFunc1d1d(rv, "deg", Math::toDegrees);
        addSingleRvFunc1d1d(rv, "exp", Math::exp);
        addSingleRvFunc(rv, "floor", LMath::floor);
        addSingleRvFunc2(rv,"fmod", LMath::fmod);
        rv.set("huge", LuaObject.of(Double.POSITIVE_INFINITY));
        addSingleRvFunc2(rv, "log", LMath::log);
        // TODO add max()
        rv.set("maxinteger", LuaObject.of(Long.MAX_VALUE));
        // TODO add min()
        rv.set("mininteger", LuaObject.of(Long.MIN_VALUE));
        addDualRvFunc(rv, "modf", LMath::modf);
        rv.set("pi", LuaObject.of(Math.PI));
        addSingleRvFunc1d1d(rv, "rad", Math::toRadians);
        // TODO add random
        // TODO add randomseed
        addSingleRvFunc1d1d(rv, "sin", Math::sin);
        addSingleRvFunc1d1d(rv, "sqrt", Math::sqrt);
        addSingleRvFunc1d1d(rv, "tan", Math::tan);
        addSingleRvFunc(rv, "tointeger", (vm, x) -> x.isIntCoercible() ? LuaObject.of(x.asLong()) : LuaObject.NIL);
        addSingleRvFunc(rv, "type", (vm, x) -> x.isLong() ? LuaObject.of("integer") : (x.isDouble() ? LuaObject.of("float") : LuaObject.NIL));
        addSingleRvFunc2(rv, "ult", LMath::ult);

        return rv;
    }
}

package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.api.lambdas.LLFunction;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.function.Function;

public class LMath {
    public static LuaObject abs(LuaVM_RT vm, LuaObject x) {
        if (x.isNumber()) return x.asDouble() < 0 ? x.unm() : x;
        vm.errorArgType(1, "number", x);
        return null;
    }

    private static LuaObject func1d1d(LuaVM_RT vm, LuaObject x, Function<Double, Double> f) {
        if (x.isNumber()) return LuaObject.of(f.apply(x.asDouble()));
        vm.errorArgType(1, "number", x);
        return null;
    }

    private static void addSingleRvFunc1d1d(LuaObject table, String name, Function<Double, Double> f) {
        table.set(name, AtomicLuaFunction.forOneResult((vm, x) -> func1d1d(vm, x, f)).obj());
    }

    private static void addSingleRvFunc(LuaObject table, String name, LLFunction f) {
        table.set(name, AtomicLuaFunction.forOneResult(f).obj());
    }

    // https://www.lua.org/manual/5.4/manual.html#6.7
    public static LuaObject getTable() {
        var rv = LuaObject.table();
        addSingleRvFunc(rv, "abs", LMath::abs);
        addSingleRvFunc1d1d(rv, "acos", Math::acos);
        addSingleRvFunc1d1d(rv, "asin", Math::asin);
        //addSingleRvFunc(rv,"atan", Math::atan2);
        addSingleRvFunc1d1d(rv, "ceil", Math::ceil); // TODO return int if it fits and double otherwise
        addSingleRvFunc1d1d(rv, "cos", Math::cos);
        addSingleRvFunc1d1d(rv, "deg", Math::toDegrees);
        addSingleRvFunc1d1d(rv, "exp", Math::exp);
        addSingleRvFunc1d1d(rv, "floor", Math::floor);
        //addSingleRvFunc(rv,"fmod", Math::floorMod); // TODOO return int/double
        rv.set("huge", LuaObject.of(Double.POSITIVE_INFINITY));
        addSingleRvFunc1d1d(rv, "log", Math::log); // TODO support arg2 (bases)
        // TODO add max()
        // TODO add maxinteger
        // TODO add min()
        // TODO add mininteger
        //addSingleRvFunc(rv,"modf", Math::toDegrees);
        rv.set("pi", LuaObject.of(Math.PI));
        addSingleRvFunc1d1d(rv, "rad", Math::toRadians);
        // TODO add random, randomseed
        addSingleRvFunc1d1d(rv, "sin", Math::sin);
        addSingleRvFunc1d1d(rv, "sqrt", Math::sqrt);
        addSingleRvFunc1d1d(rv, "tan", Math::tan);
        //addSingleRvFunc(rv, "tointeger", LMath::tointeger);
        addSingleRvFunc(rv, "type", (vm, x) -> x.isLong() ? LuaObject.of("integer") : (x.isDouble() ? LuaObject.of("float") : LuaObject.NIL));
        // TODO add ult

        return rv;
    }
}

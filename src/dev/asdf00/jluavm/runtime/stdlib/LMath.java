package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.api.lambdas.LLBiFunction;
import dev.asdf00.jluavm.api.lambdas.LLFunction;
import dev.asdf00.jluavm.api.lambdas.LLMultiFunction;
import dev.asdf00.jluavm.api.lambdas.LLVaFunction;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.errors.LuaArgumentError;
import dev.asdf00.jluavm.runtime.errors.LuaUserError;
import dev.asdf00.jluavm.runtime.types.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.Singletons;

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

    public static LuaObject random(LuaVM_RT vm, LuaObject[] args) {
        var rnd = vm.lMathRandom;
        if (args.length == 0) {
            return LuaObject.of(rnd.nextDouble()); // [0d, 1d)
        }
        // all args must be int coercible
        for (int i = 0; i < args.length; i++) {
            if (!args[i].isIntCoercible()) {
                vm.errorArgType(i, "integer", args[i]);
                return null;
            }
        }

        long m = -1;
        long n = -1;

        if (args.length == 1) { // math.random(n) == math.random(1,n)
            m = 1;
            n = args[0].asLong();
            if (n == 0) {
                return LuaObject.of(rnd.nextLong());
            } else if (n < 0) {
                vm.error(new LuaArgumentError(0, "random", "interval is empty"));
                return null;
            }
        } else if (args.length == 2) {
            m = args[0].asLong();
            n = args[1].asLong();
        } else {
            vm.error(new LuaUserError("too many arguments"));
            return null;
        }
        return LuaObject.of(rnd.nextLong(m, n + 1)); // [m, n]
    }

    // SPEC DEVIATION: we just have one 64bit seed, not (1 to 2)*64-bit
    public static LuaObject randomseed(LuaVM_RT vm, LuaObject[] args) {
        var rnd = vm.lMathRandom;
        long seed;
        if (args.length == 0) { // no seed given, so generate a new one, seed it and return the seed
            seed = rnd.nextLong();
        } else { // extra args (2nd and beyond) are just ignored in luac 5.4 it seems
            if (!args[0].isIntCoercible()) {
                vm.errorArgType(0, "integer", args[0]);
                return null;
            }
            seed = args[0].asLong();
        }
        rnd.setSeed(seed);
        return LuaObject.of(seed);
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

    private static void addMultiRvVaFunc(LuaObject table, String name, LLMultiFunction f) {
        table.set(name, AtomicLuaFunction.forManyResults(f).obj());
    }

    private static void addSingleRvVaFunc(LuaObject table, String name, LLVaFunction f) {
        table.set(name, AtomicLuaFunction.vaForOneResult(f).obj());
    }

    private static LuaObject getMinMaxFunc(boolean isMax) {
        return LuaObject.of(new LuaFunction(Singletons.EMPTY_LUA_OBJ_ARRAY, Singletons.EMPTY_LUA_OBJ_ARRAY) {
            @Override
            public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
                LuaObject min;
                int i = resume;
                var params = stackFrame[0].asArray();
                if (resume == -1) { // initial call
                    i = 1;
                    min = params[0];
                } else {
                    assert resume > 0; // should never be 0 and also not negative except -1
                    min = stackFrame[1];

                    // if we were resumed, we must have a bool inside returned[0] representing whether arg[resume-1] was better than min
                    assert returned.length == 1;
                    assert returned[0].isBoolean();
                    if (returned[0].getBool())
                        min = params[resume - 1];
                }
                while (i < params.length) {
                    var arg = params[i];
                    if (min.isString() && arg.isString() || min.isNumber() && arg.isNumber()) {
                        if ((isMax ? min.lt(arg) : arg.lt(min)).isTruthy()) {
                            min = arg;
                        }
                    } else if (min.isTable() || arg.isTable()) {
                        var mtf = isMax ? (min.isTable() ? min : arg) : (arg.isTable() ? arg : min);
                        var ltf = mtf.getMetaValueOrNil("__lt");
                        if (ltf == null) {
                            vm.error(new LuaUserError("attempt to compare number with table"));
                            return;
                        } else if (!ltf.isFunction()) {
                            vm.error(new LuaUserError("attempt to call a %s value".formatted(ltf.getTypeAsString())));
                            return;
                        }
                        expressionStack[0] = min;
                        vm.callExternal(i + 1, ltf.getFunc(), isMax ? new LuaObject[]{min, arg} : new LuaObject[]{arg, min});
                        return;
                    }
                    i++;
                }
                vm.returnValue(min);
            }

            @Override
            public int getMaxLocalsSize() {
                return 1 + 1; // storedValue + params arg
            }

            @Override
            public int getArgCount() {
                return 0;
            }

            @Override
            public boolean hasParamsArg() {
                return true;
            }
        });
    }

    // https://www.lua.org/manual/5.4/manual.html#6.7
    public static LuaObject getTable() {
        var rv = LuaObject.table();
        addSingleRvFunc(rv, "abs", LMath::abs);
        addSingleRvFunc1d1d(rv, "acos", Math::acos);
        addSingleRvFunc1d1d(rv, "asin", Math::asin);
        addSingleRvFunc2(rv, "atan", LMath::atan);
        addSingleRvFunc(rv, "ceil", LMath::ceil);
        addSingleRvFunc1d1d(rv, "cos", Math::cos);
        addSingleRvFunc1d1d(rv, "deg", Math::toDegrees);
        addSingleRvFunc1d1d(rv, "exp", Math::exp);
        addSingleRvFunc(rv, "floor", LMath::floor);
        addSingleRvFunc2(rv, "fmod", LMath::fmod);
        rv.set("huge", LuaObject.of(Double.POSITIVE_INFINITY));
        addSingleRvFunc2(rv, "log", LMath::log);
        rv.set("max", getMinMaxFunc(true));
        rv.set("maxinteger", LuaObject.of(Long.MAX_VALUE));
        rv.set("min", getMinMaxFunc(false));
        rv.set("mininteger", LuaObject.of(Long.MIN_VALUE));
        addMultiRvVaFunc(rv, "modf", LMath::modf);
        rv.set("pi", LuaObject.of(Math.PI));
        addSingleRvFunc1d1d(rv, "rad", Math::toRadians);
        addSingleRvVaFunc(rv, "random", LMath::random);
        addSingleRvVaFunc(rv, "randomseed", LMath::randomseed);
        addSingleRvFunc1d1d(rv, "sin", Math::sin);
        addSingleRvFunc1d1d(rv, "sqrt", Math::sqrt);
        addSingleRvFunc1d1d(rv, "tan", Math::tan);
        addSingleRvFunc(rv, "tointeger", (vm, x) -> x.isIntCoercible() ? LuaObject.of(x.asLong()) : LuaObject.NIL);
        addSingleRvFunc(rv, "type", (vm, x) -> x.isLong() ? LuaObject.of("integer") : (x.isDouble() ? LuaObject.of("float") : LuaObject.NIL));
        addSingleRvFunc2(rv, "ult", LMath::ult);

        return rv;
    }
}

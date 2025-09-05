package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.runtime.types.LuaJavaApiFunction;
import dev.asdf00.jluavm.api.functions.MixedStateFunctionRegistry;
import dev.asdf00.jluavm.api.lambdas.LLBiFunction;
import dev.asdf00.jluavm.api.lambdas.LLFunction;
import dev.asdf00.jluavm.api.lambdas.LLMultiFunction;
import dev.asdf00.jluavm.api.lambdas.LLVaFunction;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.api.functions.AtomicLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.function.Function;

import static dev.asdf00.jluavm.runtime.utils.RTUtils.*;

public class LMath {

    private static final String MATH_PREFIX = "math.";

    private static LuaObject getIntNumberIfFits(double x) {
        return (double) ((long) x) == x ? LuaObject.of((long) x) : LuaObject.of(x);
    }

    public static LuaObject abs(LuaVM_RT vm, LuaObject x) {
        if (x.isNumber()) return x.asDouble() < 0 ? x.unm() : x;
        vm.error(funcArgTypeError("math.abs", 0, x, "number"));
        return null;
    }

    public static LuaObject atan(LuaVM_RT vm, LuaObject y, LuaObject xOrNil) {
        if (!y.isNumber()) {
            vm.error(funcArgTypeError("math.atan", 0, y, "number"));
            return null;
        }
        if (!(xOrNil.isNumber() || xOrNil.isNil())) {
            vm.error(funcArgAnyTypeError("math.atan", 1, xOrNil, "number", "nil"));
            return null;
        }

        return LuaObject.of(Math.atan2(y.asDouble(), xOrNil.isNil() ? 1 : xOrNil.asDouble()));
    }

    public static LuaObject ceil(LuaVM_RT vm, LuaObject x) {
        if (!x.isNumber()) {
            vm.error(funcArgTypeError("math.ceil", 0, x, "number"));
            return null;
        }
        return getIntNumberIfFits(Math.ceil(x.asDouble()));
    }

    public static LuaObject floor(LuaVM_RT vm, LuaObject x) {
        if (!x.isNumber()) {
            vm.error(funcArgTypeError("math.floor", 0, x, "number"));
            return null;
        }
        return getIntNumberIfFits(Math.floor(x.asDouble()));
    }

    public static LuaObject fmod(LuaVM_RT vm, LuaObject x, LuaObject y) {
        if (!x.isNumber()) {
            vm.error(funcArgTypeError("math.fmod", 0, x, "number"));
            return null;
        }
        if (!y.isNumber()) {
            vm.error(funcArgTypeError("math.fmod", 1, y, "number"));
            return null;
        }

        if (y.isLong() && y.asLong() == 0 || y.isDouble() && y.asDouble() == 0) {
            vm.error(funcBadArgError("math.fmod", 1, "expected non-zero value"));
            return null;
        }

        var anyDouble = x.isDouble() || y.isDouble();
        return LuaObject.of(anyDouble ? (x.asDouble() % y.asDouble()) : (x.asLong() % y.asLong()));
    }

    private static LuaObject log(LuaVM_RT vm, LuaObject x, LuaObject baseOrNil) {
        if (!x.isNumber()) {
            vm.error(funcArgTypeError("math.log", 0, x, "number"));
            return null;
        }
        if (!(baseOrNil.isNumber() | baseOrNil.isNil())) {
            vm.error(funcArgAnyTypeError("math.log", 1, baseOrNil, "number", "nil"));
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
        vm.error(funcArgTypeError("math.modf", 0, x, "number"));
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
                vm.error(funcArgTypeError("math.random", i, args[i], "integer"));
                return null;
            }
        }

        long m;
        long n;

        if (args.length == 1) { // math.random(n) == math.random(1,n)
            m = 1;
            n = args[0].asLong();
            if (n == 0) {
                return LuaObject.of(rnd.nextLong());
            } else if (n < 0) {
                vm.error(funcBadArgError("math.random", 0, "interval is empty"));
                return null;
            }
        } else if (args.length == 2) {
            m = args[0].asLong();
            n = args[1].asLong();
        } else {
            vm.error(LuaObject.of("In 'math.random': Too many arguments"));
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
                vm.error(funcArgTypeError("math.randomseed", 0, args[0], "integer"));
                return null;
            }
            seed = args[0].asLong();
        }
        rnd.setSeed(seed);
        return LuaObject.of(seed);
    }

    public static LuaObject ult(LuaVM_RT vm, LuaObject m, LuaObject n) {
        if (!m.isIntCoercible()) {
            vm.error(funcArgTypeError("math.ult", 0, m, "integer"));
            return null;
        }
        if (!n.isIntCoercible()) {
            vm.error(funcArgTypeError("math.ult", 1, n, "integer"));
            return null;
        }

        return LuaObject.of(m.asLong() < n.asLong());
    }

    // =================================================================================================================
    // function registration helpers
    // =================================================================================================================

    private static void addSingleRvFunc1d1d(MixedStateFunctionRegistry registry, String name, Function<Double, Double> f) {
        registry.register(MATH_PREFIX + name,
                AtomicLuaFunction.forOneResult(registry, (vm, x) -> {
                    if (x.isNumber()) {
                        return LuaObject.of(f.apply(x.asDouble()));
                    }
                    vm.error(funcArgTypeError(name, 1, x, "number"));
                    return null;
                }));
    }

    private static void addSingleRvFunc(MixedStateFunctionRegistry registry, String name, LLFunction f) {
        registry.register(MATH_PREFIX + name, AtomicLuaFunction.forOneResult(registry, f));
    }

    private static void addSingleRvFunc2(MixedStateFunctionRegistry registry, String name, LLBiFunction f) {
        registry.register(MATH_PREFIX + name, AtomicLuaFunction.forOneResult(registry, f));
    }

    @SuppressWarnings("SameParameterValue")
    private static void addMultiRvVaFunc(MixedStateFunctionRegistry registry, String name, LLMultiFunction f) {
        registry.register(MATH_PREFIX + name, AtomicLuaFunction.forManyResults(registry, f));
    }

    private static void addSingleRvVaFunc(MixedStateFunctionRegistry registry, String name, LLVaFunction f) {
        registry.register(MATH_PREFIX + name, AtomicLuaFunction.vaForOneResult(registry, f));
    }

    private static void addMinMaxFunc(MixedStateFunctionRegistry registry, boolean isMax) {
        registry.register(MATH_PREFIX + (isMax ? "max" : "min"),
                new LuaJavaApiFunction(registry) {
                    @Override
                    public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
                        LuaObject min;
                        int i = resume;
                        var params = stackFrame[0].asArray();
                        if (params.length < 1) {
                            vm.error(funcBadArgError(isMax ? "math.max" : "math.min", 0, "no value given"));
                            return;
                        }
                        if (resume == -1) { // initial call
                            i = 1;
                            min = params[0];
                            vm.registerLocals(2);
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
                                    vm.error(LuaObject.of("Attempt to compare number with table"));
                                    return;
                                } else if (!ltf.isFunction()) {
                                    vm.error(LuaObject.of("attempt to call a %s value".formatted(ltf.getTypeAsString())));
                                    return;
                                }
                                stackFrame[1] = min;
                                vm.callExternal(i + 1, ltf.getFunc(), isMax ? new LuaObject[]{min, arg} : new LuaObject[]{arg, min});
                                return;
                            } else {
                                vm.error(LuaObject.of("Attempt to compare %s with %s".formatted(min.getTypeAsString(), arg.getTypeAsString())));
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
                        return 1;
                    }

                    @Override
                    public boolean hasParamsArg() {
                        return true;
                    }
                });
    }


    public static void registerStdMath(MixedStateFunctionRegistry registry) {
        addSingleRvFunc(registry, "abs", LMath::abs);
        addSingleRvFunc1d1d(registry, "acos", Math::acos);
        addSingleRvFunc1d1d(registry, "asin", Math::asin);
        addSingleRvFunc2(registry, "atan", LMath::atan);
        addSingleRvFunc(registry, "ceil", LMath::ceil);
        addSingleRvFunc1d1d(registry, "cos", Math::cos);
        addSingleRvFunc1d1d(registry, "deg", Math::toDegrees);
        addSingleRvFunc1d1d(registry, "exp", Math::exp);
        addSingleRvFunc(registry, "floor", LMath::floor);
        addSingleRvFunc2(registry, "fmod", LMath::fmod);
        addSingleRvFunc2(registry, "log", LMath::log);
        addMinMaxFunc(registry, true);
        addMinMaxFunc(registry, false);
        addMultiRvVaFunc(registry, "modf", LMath::modf);
        addSingleRvFunc1d1d(registry, "rad", Math::toRadians);
        addSingleRvVaFunc(registry, "random", LMath::random);
        addSingleRvVaFunc(registry, "randomseed", LMath::randomseed);
        addSingleRvFunc1d1d(registry, "sin", Math::sin);
        addSingleRvFunc1d1d(registry, "sqrt", Math::sqrt);
        addSingleRvFunc1d1d(registry, "tan", Math::tan);
        addSingleRvFunc(registry, "tointeger", (vm, x) -> x.isIntCoercible() ? LuaObject.of(x.asLong()) : LuaObject.NIL);
        addSingleRvFunc(registry, "type", (vm, x) -> x.isLong() ? LuaObject.of("integer") : (x.isDouble() ? LuaObject.of("float") : LuaObject.NIL));
        addSingleRvFunc2(registry, "ult", LMath::ult);
    }

    public static void addMathConstants(LuaObject tbl) {
        tbl.set("huge", LuaObject.of(Double.POSITIVE_INFINITY));
        tbl.set("maxinteger", LuaObject.of(Long.MAX_VALUE));
        tbl.set("mininteger", LuaObject.of(Long.MIN_VALUE));
        tbl.set("pi", LuaObject.of(Math.PI));
    }
}

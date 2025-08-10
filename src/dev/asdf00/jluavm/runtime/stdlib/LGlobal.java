package dev.asdf00.jluavm.runtime.stdlib;

import dev.asdf00.jluavm.api.functions.AtomicLuaFunction;
import dev.asdf00.jluavm.api.functions.LuaJavaApiFunction;
import dev.asdf00.jluavm.api.functions.MixedStateFunctionRegistry;
import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.loading.LuaParserException;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.parsing.Lexer;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.types.LuaObject.Types;
import dev.asdf00.jluavm.runtime.utils.RTUtils;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.Arrays;

import static dev.asdf00.jluavm.runtime.utils.RTUtils.*;

public class LGlobal {

    public static void registerStdGlobal(MixedStateFunctionRegistry registry, boolean includeUnconstrainedFunctions) {

        //noinspection StatementWithEmptyBody
        if (includeUnconstrainedFunctions) {
        /* TODO, add the following ones: https://www.lua.org/manual/5.4/manual.html#6.1
            dofile
            loadfile
            require? (could be considered safe, depending on the implementation)
         */
        }

        // errors

        /* TODO, add the following ones: https://www.lua.org/manual/5.4/manual.html#6.1
        collectgarbage
        print
        warn
         */

        registry.register("pcall",
                new LuaJavaApiFunction(registry) {
                    @Override
                    public int getMaxLocalsSize() {
                        return 2;
                    }

                    @Override
                    public int getArgCount() {
                        return 2;
                    }

                    @Override
                    public boolean hasParamsArg() {
                        return true;
                    }

                    @Override
                    public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
                        if (resume == -1) {
                            vm.registerLocals(1);
                        }
                        switch (resume) {
                            case -1:
                                if (!stackFrame[0].isFunction()) {
                                    vm.error(funcArgTypeError("pcall", 0, stackFrame[0], "function"));
                                    return;
                                }
                                vm.setProtected(null);
                                vm.callExternal(0, stackFrame[0].getFunc(), stackFrame[1].asArray());
                                return;
                            case 0:
                                if (vm.isFailed()) {
                                    vm.returnValue(LuaObject.of(false), returned.length > 0 ? returned[0] : LuaObject.nil());
                                    return;
                                } else {
                                    var flattened = new LuaObject[returned.length + 1];
                                    flattened[0] = LuaObject.of(true);
                                    System.arraycopy(returned, 0, flattened, 1, returned.length);
                                    vm.returnValue(flattened);
                                    return;
                                }
                            default:
                                throw new InternalLuaRuntimeError("unknown resume point " + resume);
                        }
                    }
                });

        registry.register("xpcall",
                new LuaJavaApiFunction(registry) {
                    @Override
                    public int getMaxLocalsSize() {
                        return 3;
                    }

                    @Override
                    public int getArgCount() {
                        return 3;
                    }

                    @Override
                    public boolean hasParamsArg() {
                        return true;
                    }

                    @Override
                    public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
                        if (resume == -1) {
                            vm.registerLocals(1);
                        }
                        switch (resume) {
                            case -1:
                                if (!stackFrame[1].isFunction()) {
                                    vm.error(funcArgTypeError("xpcall", 1, stackFrame[1], "function"));
                                    return;
                                }
                                vm.setProtected(stackFrame[1].getFunc());
                                if (!stackFrame[0].isFunction()) {
                                    vm.error(funcArgTypeError("xpcall", 0, stackFrame[0], "function"));
                                    return;
                                }
                                vm.callExternal(0, stackFrame[0].getFunc(), stackFrame[2].asArray());
                                return;
                            case 0:
                                if (vm.isFailed()) {
                                    vm.returnValue(LuaObject.of(false), returned.length > 0 ? returned[0] : LuaObject.nil());
                                    return;
                                } else {
                                    vm.returnValue(LuaObject.of(true), LuaObject.of(returned));
                                    return;
                                }
                            default:
                                throw new InternalLuaRuntimeError("unknown resume point " + resume);
                        }
                    }
                });

        registry.register("error", AtomicLuaFunction.forZeroResults(registry, LuaVM_RT::error));

        registry.register("tonumber", AtomicLuaFunction.forOneResult(registry, (vm, x) -> {
            if (x.isNumber())
                return x;
            if (!x.isString())
                return LuaObject.NIL;
            try {
                var chars = x.asString().strip();
                Integer[] currCharPtr = new Integer[]{0};
                var res = Lexer.parseNumber(new Position(0, 0, 0),
                        () -> currCharPtr[0] >= chars.length() ? (char) -1 : chars.charAt(currCharPtr[0]),
                        () -> currCharPtr[0]++);
                if (res.consumedString().equals(chars))
                    return res.dVal() < 0 ? LuaObject.of(res.lVal()) : LuaObject.of(res.dVal());
                else
                    return LuaObject.NIL;
            } catch (LuaParserException e) {
                return LuaObject.NIL;
            }
        }));

        registry.register("$inner.ipairs",
                AtomicLuaFunction.forManyResults(registry, (itrVm, myTbl, ctrl) -> {
                    if (!myTbl.isTable()) {
                        // LUAC DEVIATION. We exclusively allow tables here, luac just behaves normally on tables, returns nil on
                        // strings and errors on all other types. We error on all types but tables.
                        itrVm.error(funcArgTypeError("ipairs$iterator", 0, myTbl, "table"));
                        return null;
                    }
                    if (!ctrl.isNumberCoercible()) {
                        itrVm.error(funcArgTypeError("ipairs$iterator", 1, ctrl, "number"));
                        return null;
                    }
                    var nextIdx = ctrl.add(LuaObject.of(1));
                    return myTbl.hasKey(nextIdx) && !myTbl.get(nextIdx).isNil() ? new LuaObject[]{nextIdx, myTbl.get(nextIdx)} : new LuaObject[]{LuaObject.nil()};
                }));
        registry.register("ipairs",
                new LuaJavaApiFunction(registry) {
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
                                    vm.returnValue(LuaObject.of(this.registry.getFunction("$inner.ipairs")), stackFrame[0], LuaObject.of(0));
                                    return;
                                }
                                var mtbl = tbl.getMetaTable();
                                t0 = indexedGet(vm, 0, mtbl, Singletons.__ipairs);
                                if (t0 == null) {
                                    return;
                                }
                            case 0:
                                if (!t0.isFunction()) {
                                    vm.returnValue(LuaObject.of(this.registry.getFunction("$inner.ipairs")), stackFrame[0], LuaObject.of(0));
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

        registry.register("pairs",
                new LuaJavaApiFunction(registry) {
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
                                    vm.returnValue(LuaObject.of(this.registry.getFunction("next")), stackFrame[0], LuaObject.nil());
                                    return;
                                }
                                var mtbl = tbl.getMetaTable();
                                t0 = indexedGet(vm, 0, mtbl, Singletons.__pairs);
                                if (t0 == null) {
                                    return;
                                }
                            case 0:
                                if (!t0.isFunction()) {
                                    vm.returnValue(LuaObject.of(this.registry.getFunction("next")), stackFrame[0], LuaObject.nil());
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

        registry.register("next",
                AtomicLuaFunction.forManyResults(registry, (vm, table, index) -> {
                    if (!table.isTable()) {
                        vm.error(funcArgTypeError("ipairs$iterator", 0, table, "table"));
                        return null;
                    }
                    var tbl = table.asMap();
                    LuaObject nidx;
                    if (index.isNil()) {
                        nidx = tbl.getFirstKey();
                    } else {
                        // this might break when setting the next index to nil before the next iteration
                        nidx = tbl.containsKey(index) ? tbl.getKeyAfter(index) : LuaObject.nil();
                    }
                    return nidx.isNil() ? new LuaObject[]{LuaObject.nil(), LuaObject.nil()} : new LuaObject[]{nidx, table.get(nidx)};
                }));

        registry.register("load",
                AtomicLuaFunction.vaForManyResults(registry, (vm, args) -> {
                    // https://www.lua.org/manual/5.4/manual.html#pdf-load
                    // TODO support function for args[0];
                    var chunk = args.length > 0 ? args[0] : null;
                    var chunkName = args.length > 1 ? args[1] : null; // TODO make use of chunkname
                    var mode = args.length > 2 ? args[2] : null;
                    var env = args.length > 3 ? args[3] : null;

                    if (chunk == null || !chunk.isString()) {
                        vm.error(funcArgTypeError("load", 0, chunk, "string"));
                        return null;
                    }
                    if (chunkName != null && !chunkName.isType(Types.ARITHMETIC | Types.NIL)) {
                        vm.error(funcArgAnyTypeError("load", 1, chunkName,"string", "number", "nil", "nothing"));
                        return null;
                    }
                    if (mode != null && !(mode.isString() && (mode.asString().equals("t"))) && !mode.isNil()) {
                        vm.error(funcArgAnyTypeError("load", 2, mode, "string: \"t\"", "nil", "nothing"));
                        return null;
                    }
                    if (env != null && !env.isTable()) {
                        vm.error(funcArgAnyTypeError("load", 3, env, "table", "nothing"));
                        return null;
                    }

                    //noinspection all
                    assert chunk.isString();


                    try {
                        var rv2 = vm.load(chunk.getString(), env == null ? vm.getCallerEnv() : env);
                        return new LuaObject[]{LuaObject.of(rv2), LuaObject.NIL};
                    } catch (LuaParserException ex) {
                        return new LuaObject[]{LuaObject.NIL, LuaObject.of("Compilation error: "+ex.getMessage())};
                    }
                }));

        registry.register("assert",
                AtomicLuaFunction.vaForManyResults(registry, (vm, params) -> {
                    if (params.length == 0) {
                        vm.error(funcArgTypeError("assert", 0, null, "any"));
                        return null;
                    }

                    if (params[0].isTruthy())
                        return params;

                    // LUAC DEVIATION. Our assertion returns tostring(msg) instead of just the type of that argument for nil and boolean
                    vm.error(LuaObject.of(params.length < 2 ? "assertion failed!" : params[1].asString()));
                    return null;
                }));

        registry.register("getmetatable",
                AtomicLuaFunction.forOneResult(registry, (vm, t) -> {
                    if (!t.isTable())
                        return LuaObject.NIL;

                    var mt = t.getMetaTable();
                    if (mt.isNil())
                        return mt;

                    return mt.get(LuaObject.of("__metatable"));
                }));

        registry.register("rawequal",
                AtomicLuaFunction.forOneResult(registry, (vm, v1, v2) -> {
                    var t1 = v1.getType();
                    if (t1 == Types.LONG || t1 == Types.DOUBLE)
                        t1 = Types.NUMBER;

                    var t2 = v2.getType();
                    if (t2 == Types.LONG || t2 == Types.DOUBLE)
                        t2 = Types.NUMBER;

                    return LuaObject.of(t1 == t2 && ((t1 == Types.NUMBER || t1 == Types.STRING) ? v1.eq(v2).getBool() : v1 == v2));
                }));

        registry.register("rawget",
                AtomicLuaFunction.forOneResult(registry, (vm, tbl, k) -> {
                    if (!tbl.isTable()) {
                        vm.error(funcArgTypeError("rawget", 0, tbl, "table"));
                        return null;
                    }
                    if (k.isNil() || k.isDouble() && Double.isNaN(k.asDouble()))
                        return null;

                    tbl.get(k); // dont call a metamethod, this is a 'raw' function
                    return tbl;
                }));

        registry.register("rawlen",
                AtomicLuaFunction.forOneResult(registry, (vm, v) -> {
                    if (!v.isTable() && !v.isString()) {
                        vm.error(funcArgAnyTypeError("rawlen", 0, v, "table", "string"));
                        return null;
                    }
                    return v.len();
                }));

        registry.register("rawset",
                AtomicLuaFunction.forOneResult(registry, (vm, tbl, k, v) -> {
                    if (!tbl.isTable()) {
                        vm.error(funcArgTypeError("rawset", 0, tbl, "table"));
                        return null;
                    }
                    if (k.isNil() || k.isNaN()) {
                        vm.error(funcBadArgError("rawset", 1, "table index can not be Nil or NaN"));
                        return null;
                    }

                    tbl.set(RTUtils.tryCoerceFloatToInt(k), v); // dont call a metamethod, this is a 'raw' function
                    return tbl;
                }));

        registry.register("select",
                AtomicLuaFunction.vaForManyResults(registry, (vm, args) -> {
                    if (args.length == 0) {
                        vm.error(funcBadArgError("select", 0, "number or \"#\" expected, got no value"));
                    }

                    var idx = args[0];
                    if (idx.isNumber()) {
                        if (!idx.isLong()) {
                            vm.error(funcBadArgError("select", 0, "number has no integer representation"));
                            return null;
                        }
                        return Arrays.stream(args).skip(idx.asLong()).toArray(LuaObject[]::new); // TODO make work for negative idx
                    } else {
                        if (!(idx.isString() && idx.asString().equals("#"))) {
                            vm.error(funcBadArgError("select", 0, "number or \"#\" expected, got %s".formatted(idx.getTypeAsString())));
                            return null;
                        }
                        // must be "#" then
                        return new LuaObject[]{LuaObject.of(args.length - 1)};
                    }
                }));

        registry.register("setmetatable",
                AtomicLuaFunction.forOneResult(registry, (vm, tbl, mt) -> {
                    if (!tbl.isTable()) {
                        vm.error(funcArgTypeError("setmetatable", 0, tbl, "table"));
                        return null;
                    }
                    if (!mt.isTable()) {
                        vm.error(funcArgTypeError("setmetatable", 1, mt, "table"));
                        return null;
                    }

                    if (!tbl.getMetaValueOrNil("__metatable").isNil()) {
                        vm.error(LuaObject.of("cannot change a protected metatable"));
                        return null;
                    }

                    var existingMt = tbl.getMetaTable();
                    if (existingMt != null && !existingMt.isNil())
                        return mt;
                    tbl.setMetatable(mt);

                    return tbl;
                }));

        registry.register("tostring",
                new LuaJavaApiFunction(registry) {
                    @Override
                    public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
                        //noinspection unused
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

                                var tostring = arg.getMetaValueOrNil("__tostring");
                                if (tostring.isNil()) { // we dont have a tostring field --> cook something up using __name, or without that
                                    var name = arg.getMetaValueOrNil("__name");
                                    vm.returnValue(LuaObject.of(!name.isNil() && name.isString() ? name.getString() + ": " + arg.asString() : arg.asString()));
                                    return;
                                }
                                // otherwise simply call it and see what happens
                                if (tostring.isFunction())
                                    vm.tailCall(tostring.getFunc(), arg);
                                else
                                    vm.callInternal(0, LuaFunction::callWithMeta, "::callWithMeta", tostring, arg);
                                return;
                            case 0:
                                var rval = returned.length > 0 ? returned[0] : LuaObject.nil();
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

        registry.register("type",
                AtomicLuaFunction.forOneResult(registry, (vm, x) -> LuaObject.of(x.getTypeAsString())));
    }
}

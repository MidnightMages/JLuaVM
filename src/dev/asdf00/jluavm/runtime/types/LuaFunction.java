package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.LuaRuntimeError;
import dev.asdf00.jluavm.internals.Coroutine;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.utils.LFunc;
import dev.asdf00.jluavm.runtime.utils.RTUtils;
import dev.asdf00.jluavm.runtime.utils.Singletons;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public abstract class LuaFunction {
    /**
     * _ENV should only ever
     */
    public final LuaObject _ENV;
    public final LuaObject[] closures;
    public LuaObject selfLuaObj; // should only be accessed by LuaObject#of(LuaFunction) and the StateDeserializer

    public LuaFunction() {
        this(null, Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public LuaFunction(LuaObject _ENV, LuaObject[] closures) {
        this._ENV = _ENV;
        this.closures = closures;
    }

    public abstract void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned);

    protected static <T extends LuaFunction> T newInnerFunction(Constructor<T> ctor, LuaObject _ENV, LuaObject... closures) {
        try {
            return ctor.newInstance(_ENV, closures);
        } catch (ReflectiveOperationException e) {
            throw new InternalLuaRuntimeError("error on generating inner function reference (%s)".formatted(e));
        }
    }

    // =================================================================================================================
    // overridable constants for stack frame setup
    // =================================================================================================================

    /**
     * @return the maximum size of the local variable stack including all arguments (even params) as locals.
     */
    public abstract int getMaxLocalsSize();

    /**
     * @return argument count INCLUDING a possible params argument.
     */
    public abstract int getArgCount();

    /**
     * @return if the last argument is a params argument.
     */
    public abstract boolean hasParamsArg();

    /**
     * This method serializes the function into the provided byte array builder.
     */
    public void serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, ByteArrayBuilder bb) {
        throw new UnsupportedOperationException("Unimplemented parent");
    }

    protected void debugPoint(Object... params) {
        /**
         * To set a break point to a line in lua, enable DEBUG_MODE in CompilationState and set a conditional break
         * point to the statement below using
         * {@code params.length > 0 && params[0] instanceof String st && st.startsWith("at lua l___:")}
         * as the condition where 'l___:' would be l5: for line 5 in lua. Here also stackFrame and the vm handle are
         * passed as secondary arguments.
         */
        int breakPoint = 0;
    }

    // =================================================================================================================
    // helper ops
    // =================================================================================================================

    /**
     * Performs an indexed GET operation. If {@code null} is returned, the VM is set up and the caller is expected to
     * return to the VM.
     */
    protected static LuaObject indexedGet(LuaVM_RT vm, int resumeLabel, LuaObject obj, LuaObject idx) {
        if (obj.isTable()) {
            LuaObject key = RTUtils.tryCoerceFloatToInt(idx);
            if (obj.hasKey(key)) {
                return obj.get(key);
            } else {
                LuaObject mtbl = obj.getMetaTable();
                if (mtbl == null || !mtbl.isTable() || !mtbl.hasKey(Singletons.__index)) {
                    return LuaObject.nil();
                } else {
                    vm.callInternal(resumeLabel, LuaFunction::getWithMeta, "::getWithMeta", obj, key, mtbl);
                    return null;
                }
            }
        } else if (obj.isUserData()) {
            LuaObject mtbl = obj.getMetaTable();
            if (mtbl == null || !mtbl.isTable() || !mtbl.hasKey(Singletons.__index)) {
                try {
                    Coroutine cco = vm.getCurrentCoroutine();
                    boolean prevYieldable = cco.isYieldable;
                    ;
                    cco.isYieldable = false;
                    var r = obj.get(idx);
                    cco.isYieldable = prevYieldable;
                    return r;
                } catch (LuaRuntimeError ex) {
                    vm.error(LuaObject.of("Foreign call error: " + ex.getMessage()));
                    return null;
                }
            } else {
                vm.callInternal(resumeLabel, LuaFunction::getWithMeta, "::getWithMeta", obj, idx, mtbl);
                return null;
            }
        } else {
            // invalid type for indexed get
            // LUAC DEVIATION. Indexing a string value yields nil for every index in lua c. We believe this behavior to
            // be inconsistent and error the same way we would do for the rest of the non-indexable types.
            vm.error(LuaObject.of("Attempt to index a %s value".formatted(obj.getTypeAsString())));
            return null;
        }
    }

    protected static LuaObject tryIndexedGet(LuaVM_RT vm, int resumeLabel, LuaObject obj, LuaObject idx) {
        if (obj.isTable()) {
            LuaObject key = RTUtils.tryCoerceFloatToInt(idx);
            if (obj.hasKey(key)) {
                return obj.get(key);
            } else {
                LuaObject mtbl = obj.getMetaTable();
                if (mtbl == null || !mtbl.isTable() || !mtbl.hasKey(Singletons.__index)) {
                    return LuaObject.nil();
                } else {
                    vm.callInternal(resumeLabel, LuaFunction::getWithMeta, "::getWithMeta", obj, key, mtbl);
                    return null;
                }
            }
        } else if (obj.isUserData()) {
            LuaObject mtbl = obj.getMetaTable();
            if (mtbl == null || !mtbl.isTable() || !mtbl.hasKey(Singletons.__index)) {
                try {
                    Coroutine cco = vm.getCurrentCoroutine();
                    boolean prevYieldable = cco.isYieldable;
                    ;
                    cco.isYieldable = false;
                    var r = obj.get(idx);
                    cco.isYieldable = prevYieldable;
                    return r;
                } catch (LuaRuntimeError ex) {
                    vm.error(LuaObject.of("Foreign call error: " + ex.getMessage()));
                    return null;
                }
            } else {
                vm.callInternal(resumeLabel, LuaFunction::getWithMeta, "::getWithMeta", obj, idx, mtbl);
                return null;
            }
        } else {
            // instead of producing an error when this is called on an invalid type, we just return nil
            return LuaObject.nil();
        }
    }

    protected static boolean indexedSet(LuaVM_RT vm, int resumeLabel, LuaObject obj, LuaObject idx, LuaObject val) {
        if (obj.isTable()) {
            LuaObject key = RTUtils.tryCoerceFloatToInt(idx);
            if (key.isNil() || key.isNaN()) {
                vm.error(LuaObject.of("Table index can not be Nil or NaN"));
                return true;
            }
            if (obj.hasKey(key)) {
                obj.set(key, val);
                return false;
            } else {
                LuaObject mtbl = obj.getMetaTable();
                if (mtbl == null || !mtbl.hasKey(Singletons.__newindex)) {
                    obj.set(key, val);
                    return false;
                } else {
                    vm.callInternal(resumeLabel, LuaFunction::setWithMeta, "::setWithMeta", obj, key, val, mtbl);
                    return true;
                }
            }
        } else if (obj.isUserData()) {
            LuaObject mtbl = obj.getMetaTable();
            if (mtbl == null || !mtbl.hasKey(Singletons.__newindex)) {
                try {
                    Coroutine cco = vm.getCurrentCoroutine();
                    boolean prevYieldable = cco.isYieldable;
                    ;
                    cco.isYieldable = false;
                    obj.set(idx, val);
                    cco.isYieldable = prevYieldable;
                    return false;
                } catch (LuaRuntimeError ex) {
                    vm.error(LuaObject.of("Foreign call error: " + ex.getMessage()));
                    return true;
                }
            } else {
                vm.callInternal(resumeLabel, LuaFunction::setWithMeta, "::setWithMeta", obj, idx, val, mtbl);
                return true;
            }
        } else {
            // invalid type for indexed set
            vm.error(LuaObject.of("Attempt to set an index for a %s value".formatted(obj.getTypeAsString())));
            return true;
        }
    }

    protected static LuaObject areEqual(LuaVM_RT vm, int resumeLabel, LuaObject x, LuaObject y) {
        if (x.isNumber() && y.isNumber() || x.isString() && y.isString()) {
            return x.eq(y);
        } else if (x.getType() == y.getType()) {
            if (x == y) {
                return LuaObject.TRUE;
            } else if (!x.getMetaValueOrNil(Singletons.__eq).isNil() || !y.getMetaValueOrNil(Singletons.__eq).isNil()) {
                vm.callInternal(resumeLabel, LuaFunction::binaryOpWithMeta, "::binaryOpWithMeta", Singletons.__eq, x, y);
                return null;
            } else {
                return LuaObject.FALSE;
            }
        } else {
            return LuaObject.FALSE;
        }
    }

    protected static LuaObject isLessThan(LuaVM_RT vm, int resumeLabel, LuaObject x, LuaObject y) {
        if (x.isNumber() && y.isNumber() || x.isString() && y.isString()) {
            return x.lt(y);
        } else if (x.isTable() || y.isTable()) {
            if (!x.getMetaValueOrNil(Singletons.__lt).isNil() || !y.getMetaValueOrNil(Singletons.__lt).isNil()) {
                vm.callInternal(resumeLabel, LuaFunction::binaryOpWithMeta, "::binaryOpWithMeta", Singletons.__lt, x, y);
            } else {
                vm.error(LuaObject.of("Attempted to compare %s with %s without __lt being found".formatted(x.getTypeAsString(), y.getTypeAsString())));
            }
        } else {
            vm.error(LuaObject.of("Attempted to compare %s with %s".formatted(x.getTypeAsString(), y.getTypeAsString())));
        }
        return null;
    }

    protected static LuaObject lengthOf(LuaVM_RT vm, int resumeLabel, LuaObject obj) {
        if (obj.isString()) {
            // LUAC DEVIATION. We intentionally return the number of character instead of the length in bytes.
            return LuaObject.of(obj.asString().length());
        }
        var mv = obj.getMetaValueOrNil(Singletons.__len);
        if (!mv.isNil()) {
            vm.callInternal(resumeLabel, LuaFunction::unaryOpWithMeta, "::unaryOpWithMeta", Singletons.__len, obj);
            return null;
        }
        if (obj.isTable()) {
            return LuaObject.of(obj.asMap().luaLen());
        } else {
            // type incompatible with length-of operator
            vm.error(LuaObject.of("Attempt to get length of a %s value".formatted(obj.getTypeAsString())));
            return null;
        }
    }

    protected static boolean numericForCheck(LuaVM_RT vm, LuaObject initial, LuaObject limit, LuaObject step) {
        if (!initial.isNumberCoercible()) {
            vm.error(LuaObject.of("Bad 'for' initial value (number expected, got %s)".formatted(initial.getTypeAsString())));
            return true;
        }
        if (!limit.isNumberCoercible()) {
            vm.error(LuaObject.of("Bad 'for' limit (number expected, got %s)".formatted(limit.getTypeAsString())));
            return true;
        }
        if (!step.isNumberCoercible()) {
            vm.error(LuaObject.of("Bad 'for' step (number expected, got %s)".formatted(step.getTypeAsString())));
            return true;
        }
        return false;
    }

    // =================================================================================================================
    // slow path methods for meta table stuff
    // =================================================================================================================

    /**
     * This method is meant to be called when a plain table lookup has failed and a metatable __index call is needed.
     * The arguments for this method takes the original table, the key and the metatable as arguments.
     */
    protected static void getWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        //noinspection UnusedAssignment
        LuaObject t0 = null, t1 = null, t2 = null;
        if (resume == -1) {
            vm.registerLocals(0);
            if (args.length != 3) {
                throw new InternalLuaRuntimeError("expected 3 arguments, got " + args.length);
            }
            t0 = args[0]; // original table
            t1 = args[1]; // original key
            t2 = args[2]; // metatable
        } else if (resume == 0) {
            t0 = returned.length > 0 ? returned[0] : LuaObject.nil();
        } else {
            throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
        switch (resume) {
            case -1:
                // getRaw from metatable
                if (!t2.isTable()) {
                    throw new InternalLuaRuntimeError("got a non-table value as a metatable for looking up __index");
                }
                // drop metatable, t2 is metaValue now
                t2 = t2.get(Singletons.__index);
                if (t2.isNil()) {
                    throw new InternalLuaRuntimeError("getWithMeta should only be called with the metatable having a non-nil value at __index");
                }
                if (t2.isFunction()) {
                    vm.callExternal(0, t2.getFunc(), t0, t1);
                    return;
                }
                // the table is not needed anymore, we reuse t0 for metaValue[key]
                t0 = indexedGet(vm, 0, t2, t1);
                if (t0 == null) {
                    return;
                }
            case 0:
                vm.internalReturn(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("should not reach end of fall-through switch");
        }
    }

    /**
     * This method is meant to be called when a plain table assignment has failed and a metatable __newindex call is needed.
     * The arguments for this method takes the original table, the key, the value and the metatable as arguments.
     */
    protected static void setWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        LuaObject t0 = null, t1 = null, t2 = null, t3 = null;
        if (resume == -1) {
            vm.registerLocals(0);
            if (args.length != 4) {
                throw new InternalLuaRuntimeError("expected 4 arguments, got " + args.length);
            }
            t0 = args[0]; // table
            t1 = args[1]; // key
            t2 = args[2]; // value
            t3 = args[3]; // metatable
        } else if (resume != 0) {
            throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
        switch (resume) {
            case -1:
                // getRaw from metatable
                if (!t3.isTable()) {
                    throw new InternalLuaRuntimeError("got a non-table value as a metatable for looking up __index");
                }
                // drop metatable, t3 is metaValue now
                t3 = t3.get(Singletons.__newindex);
                if (t3.isNil()) {
                    throw new InternalLuaRuntimeError("setWithMeta should only be called with the metatable having a non-nil value at __newindex");
                }
                if (t3.isFunction()) {
                    vm.callExternal(0, t3.getFunc(), t0, t1, t2);
                    return;
                }
                if (indexedSet(vm, 0, t3, t1, t2)) {
                    return;
                }
            case 0:
                vm.internalReturn();
                return;
            default:
                throw new InternalLuaRuntimeError("should not reach end of fall-through switch");
        }
    }

    /**
     * This method is meant to be called when the given argument is meant to be called without being a function
     */
    protected static void callWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        //noinspection UnusedAssignment
        LuaObject t0 = null, t1 = null;
        if (resume == -1) {
            vm.registerLocals(0);
            if (args.length < 1) {
                throw new InternalLuaRuntimeError("expected 1 or more arguments, got " + args.length);
            }
            t0 = args[0]; // x
        } else if (resume != 0) {
            throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
        switch (resume) {
            case -1:
                t1 = t0.getMetaValueOrNil(Singletons.__call);
                if (t1.isNil()) {
                    // non-callable value
                    vm.error(LuaObject.of("Trying to call a nil value"));
                    return;
                }
                if (t1.isFunction()) {
                    vm.callExternal(0, t1.getFunc(), t0, LuaObject.of(Arrays.copyOfRange(args, 1, args.length)));
                } else {
                    var nuArgs = new LuaObject[args.length + 1];
                    nuArgs[0] = t1;
                    System.arraycopy(args, 0, nuArgs, 1, nuArgs.length);
                    vm.callInternal(0, LuaFunction::callWithMeta, "::callWithMeta", nuArgs);
                }
                return;
            case 0:
                vm.internalReturn(returned);
                return;
            default:
                throw new InternalLuaRuntimeError("should not reach end of fall-through switch");
        }
    }

    /**
     * This method is meant to be called when at least one of the two arguments of a binary expression does not satisfy the necessary type restrictions for
     * the given operation and a metatable call is needed to resolve this expression.
     * The arguments this method takes are the name of the meta-method to call, x and y as arguments.
     */
    protected static void binaryOpWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        //noinspection UnusedAssignment
        LuaObject t0 = null, t1 = null, t2 = null, t3 = null;
        if (resume == -1) {
            vm.registerLocals(0);
            if (args.length != 3) {
                throw new InternalLuaRuntimeError("expected 3 arguments, got " + args.length);
            }
            t0 = args[0]; // metatable op entry
            t1 = args[1]; // x
            t2 = args[2]; // y
        } else if (resume == 0) {
            t0 = returned.length > 0 ? returned[0] : LuaObject.nil();
        } else {
            throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
        switch (resume) {
            case -1:
                t3 = t1.getMetaValueOrNil(t0);
                if (t3.isNil()) {
                    t3 = t2.getMetaValueOrNil(t0);
                    if (t3.isNil()) {
                        // type incompatible with given binary operation
                        vm.error(LuaObject.of("Attempt operation '%s' on a '%s' with a '%s'".formatted(t0.asString().substring(2), t1.getTypeAsString(), t2.getTypeAsString())));
                        return;
                    }
                }
                if (t3.isFunction()) {
                    vm.callExternal(0, t3.getFunc(), t1, t2);
                } else {
                    vm.callInternal(0, LuaFunction::callWithMeta, "::callWithMeta", t3, t1, t2);
                }
                return;
            case 0:
                vm.internalReturn(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("should not reach end of fall-through switch");
        }
    }

    /**
     * This method is meant to be called when the value of the unary expression does not satisfy the necessary type restrictions for
     * the given operation and a metatable call is needed to resolve this expression.
     * The arguments this method takes are the name of the meta-method to call and the value as arguments.
     */
    protected static void unaryOpWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        //noinspection UnusedAssignment
        LuaObject t0 = null, t1 = null;
        if (resume == -1) {
            vm.registerLocals(0);
            if (args.length != 2) {
                throw new InternalLuaRuntimeError("expected 2 arguments, got " + args.length);
            }
            t0 = args[0]; // metatable op entry
            t1 = args[1]; // value
        } else if (resume == 0) {
            t0 = returned.length > 0 ? returned[0] : LuaObject.nil();
        } else {
            throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
        switch (resume) {
            case -1:
                t0 = t1.getMetaValueOrNil(t0);
                if (t0.isNil()) {
                    // type incompatible with given unary operation
                    vm.error(LuaObject.of("Attempt operation '%s' on a '%s'".formatted(t0.asString().substring(2), t1.getTypeAsString())));
                    return;
                }
                if (t0.isFunction()) {
                    vm.callExternal(0, t0.getFunc(), t1);
                } else {
                    vm.callInternal(0, LuaFunction::callWithMeta, "::callWithMeta", t0, t1);
                }
                return;
            case 0:
                vm.internalReturn(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("should not reach end of fall-through switch");
        }
    }

    /**
     * This method performs the lookup {@code _ENV._EXT.<type>.<functionName>}. This is the slow path when calling a type
     * extension function.
     * The arguments this method takes are the {@code _ENV}, the object's type and the function name.
     */
    protected static void lookupExtension(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        LuaObject t0 = null, t1 = null, t2 = null;
        switch (resume) {
            case -1 -> {
                vm.registerLocals(0);
                expressionStack = vm.registerExpressionStack(3);
                vm.registerLocals(0);
                if (args.length != 3) {
                    throw new InternalLuaRuntimeError("expected 3 arguments, got " + args.length);
                }
                // assign locals in inverse order of consumption
                t0 = args[2]; // funcName
                t1 = args[1]; // type
                t2 = args[0]; // env
            }
            case 0 -> {
                t0 = expressionStack[0];
                t1 = expressionStack[1];
            }
            case 1 -> {
                t0 = expressionStack[0];
            }
            case 2 -> {
            }
            default -> throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
        switch (resume) {
            case -1:
                // _ENV
                t2 = indexedGet(vm, 0, t2, Singletons._EXT);
                if (t2 == null) {
                    expressionStack[0] = t0;
                    expressionStack[1] = t1;
                    return;
                }
            case 0:
                // _ENV._EXT
                t1 = indexedGet(vm, 1, t2, t1);
                if (t1 == null) {
                    expressionStack[0] = t0;
                    return;
                }
            case 1:
                // _ENV._EXT.<type>
                t0 = indexedGet(vm, 2, t1, t0);
                if (t0 == null) {
                    return;
                }
            case 2:
                // _ENV._EXT.<type>.<funcName>
                vm.internalReturn(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("should not reach end of fall-through switch");
        }
    }

    public static final Map<String, LFunc> staticLFuncs = Map.of(
            "::getWithMeta", LuaFunction::getWithMeta,
            "::setWithMeta", LuaFunction::setWithMeta,
            "::callWithMeta", LuaFunction::callWithMeta,
            "::binaryOpWithMeta", LuaFunction::binaryOpWithMeta,
            "::unaryOpWithMeta", LuaFunction::unaryOpWithMeta,
            "::lookupExtension", LuaFunction::lookupExtension
    );
}

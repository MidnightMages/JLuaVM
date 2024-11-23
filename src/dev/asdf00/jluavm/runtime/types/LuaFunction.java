package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.LuaRuntimeError;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.errors.LuaArgumentError;
import dev.asdf00.jluavm.runtime.errors.LuaForeignCallError;
import dev.asdf00.jluavm.runtime.errors.LuaMetaTableError;
import dev.asdf00.jluavm.runtime.errors.LuaTypeError;
import dev.asdf00.jluavm.runtime.utils.RTUtils;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.lang.reflect.Constructor;
import java.util.Arrays;

@SuppressWarnings("unused")
public abstract class LuaFunction {
    public final LuaObject[] _ENV;
    public final LuaObject[] closures;

    public LuaFunction() {
        this(Singletons.EMPTY_LUA_OBJ_ARRAY, Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public LuaFunction(LuaObject[] _ENV, LuaObject[] closures) {
        this._ENV = _ENV;
        this.closures = closures;
    }

    public abstract void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned);

    protected static <T extends LuaFunction> T newInnerFunction(Constructor<T> ctor, LuaObject[] _ENV, LuaObject... closures) {
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
     * @return argument count WITHOUT a possible params argument.
     */
    public abstract int getArgCount();

    /**
     * @return if the last argument is a params argument.
     */
    public abstract boolean hasParamsArg();

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
    // helper ops for generated code
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
                    vm.callInternal(resumeLabel, LuaFunction::getWithMeta, obj, key, mtbl);
                    return null;
                }
            }
        } else if (obj.isUserData()) {
            LuaObject mtbl = obj.getMetaTable();
            if (mtbl == null || !mtbl.isTable() || !mtbl.hasKey(Singletons.__index)) {
                try {
                    return obj.get(idx);
                } catch (LuaRuntimeError ex) {
                    vm.error(new LuaForeignCallError());
                    return null;
                }
            } else {
                vm.callInternal(resumeLabel, LuaFunction::getWithMeta, obj, idx, mtbl);
                return null;
            }
        } else {
            // invalid type for indexed get
            vm.error(new LuaTypeError());
            return null;
        }
    }

    protected static boolean indexedSet(LuaVM_RT vm, int resumeLabel, LuaObject obj, LuaObject idx, LuaObject val) {
        if (obj.isTable()) {
            LuaObject key = RTUtils.tryCoerceFloatToInt(idx);
            if (key.isNil() || key.isNaN()) {
                vm.error(new LuaArgumentError());
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
                    vm.callInternal(resumeLabel, LuaFunction::setWithMeta, obj, key, val, mtbl);
                    return true;
                }
            }
        } else if (obj.isUserData()) {
            LuaObject mtbl = obj.getMetaTable();
            if (mtbl == null || !mtbl.hasKey(Singletons.__newindex)) {
                try {
                    obj.set(idx, val);
                    return false;
                } catch (LuaRuntimeError ex) {
                    vm.error(new LuaForeignCallError());
                    return true;
                }
            } else {
                vm.callInternal(resumeLabel, LuaFunction::setWithMeta, obj, idx, val, mtbl);
                return true;
            }
        } else {
            // invalid type for indexed set
            vm.error(new LuaTypeError());
            return true;
        }
    }

    protected static LuaObject areEqual(LuaVM_RT vm, int resumeLabels, LuaObject x, LuaObject y) {
        if (x.isNumber() && y.isNumber() || x.isString() && y.isString()) {
            return x.eq(y);
        } else if (x.getType() == y.getType()) {
            if (x == y) {
                return LuaObject.TRUE;
            } else if (!x.getMetaValueOrNil(Singletons.__eq).isNil() || !y.getMetaValueOrNil(Singletons.__eq).isNil()) {
                vm.callInternal(resumeLabels, LuaFunction::binaryOpWithMeta, Singletons.__eq, x, y);
                return null;
            } else {
                return LuaObject.FALSE;
            }
        } else {
            return LuaObject.FALSE;
        }
    }

    protected static LuaObject getMetaClose(LuaVM_RT vm, int resumeLabel, LuaObject closable) {
        if (RTUtils.isTruthy(closable)) {
            LuaObject metaTable = closable.getMetaTable();
            if (metaTable.isTable()) {
                LuaObject key = Singletons.__close;
                if (metaTable.hasKey(key)) {
                    return metaTable.get(key);
                } else {
                    LuaObject mtbl = metaTable.getMetaTable();
                    if (mtbl == null) {
                        return LuaObject.nil();
                    } else {
                        vm.callInternal(resumeLabel, LuaFunction::getWithMeta, metaTable, key, mtbl);
                        return null;
                    }
                }
            } else if (metaTable.isUserData()) {
                try {
                    return metaTable.get(Singletons.__close);
                } catch (LuaRuntimeError ex) {
                    vm.error(new LuaForeignCallError());
                    return null;
                }
            } else {
                vm.error(new LuaTypeError());
                return null;
            }
        } else {
            return LuaObject.nil();
        }
    }

    // =================================================================================================================
    // closable magic
    // =================================================================================================================

    protected static void addClosable(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        LuaObject t0 = null, t1 = null, t2 = null;
        // on resume
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(3);
                if (args.length != 1) {
                    throw new InternalLuaRuntimeError("expected 1 arguments, got " + args.length);
                }
                t0 = args[0]; // obj
            }
            case 0 -> {
                // restore expression stack
                t0 = expressionStack[0];
                // use first return variable
                t1 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
        }
        returned = null;
        switch (resume) {
            case -1:
                // try getExpression __close meta method
                t1 = t0.getMetaTable();
                if (t1 == null) {
                    vm.error(new LuaMetaTableError());
                }
                t2 = Singletons.__close;
                // getExpression index
                if (t1.isTable()) {
                    LuaObject table = t1;
                    LuaObject key = RTUtils.tryCoerceFloatToInt(t2);
                    if (table.hasKey(key)) {
                        t1 = table.get(key);
                    } else {
                        LuaObject mtbl = table.getMetaTable();
                        if (mtbl == null) {
                            t1 = LuaObject.nil();
                        } else {
                            // save expression stack
                            expressionStack[0] = t0;
                            vm.callInternal(0, LuaFunction::getWithMeta, table, key, mtbl);
                            return;
                        }
                    }
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
                t2 = null;
            case 0:
                if (!t1.isFunction()) {
                    // no meta method __close found
                    vm.error(new LuaMetaTableError());
                    return;
                }
                vm.addClosable(t0);
                vm.internalReturn();
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    // =================================================================================================================
    // slow path methods for meta table involved stuff
    // =================================================================================================================

    /**
     * This method is meant to be called when a plain table lookup has failed and a metatable __index call is needed.
     * The arguments for this method takes the original table, the key and the metatable as arguments.
     */
    protected static void getWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
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
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
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
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    /**
     * This method is meant to be called when the given argument is meant to be called without being a function
     */
    protected static void callWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
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
                    vm.error(new LuaTypeError());
                    return;
                }
                if (t1.isFunction()) {
                    vm.tailCall(t1.getFunc(), t0,  LuaObject.of(Arrays.copyOfRange(args, 1, args.length)));
                    return;
                } else {
                    var nuArgs = new LuaObject[args.length + 1];
                    nuArgs[0] = t1;
                    System.arraycopy(args, 0, nuArgs, 1, nuArgs.length);
                    vm.callInternal(0, LuaFunction::callWithMeta, nuArgs);
                    return;
                }
            case 0:
                vm.internalReturn(returned);
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    /**
     * This method is meant to be called when at least one of the two arguments of a binary expression does not satisfy the necessary type restrictions for
     * the given operation and a metatable call is needed to resolve this expression.
     * The arguments this method takes are the name of the meta-method to call, x and y as arguments.
     */
    protected static void binaryOpWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
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
                        vm.error(new LuaTypeError());
                        return;
                    }
                }
                if (t3.isFunction()) {
                    vm.callExternal(0, t3.getFunc(), t1, t2);
                    return;
                } else {
                    vm.callInternal(0, LuaFunction::callWithMeta, t3, t1, t2);
                    return;
                }
            case 0:
                vm.returnValue(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    /**
     * This method is meant to be called when the value of the unary expression does not satisfy the necessary type restrictions for
     * the given operation and a metatable call is needed to resolve this expression.
     * The arguments this method takes are the name of the meta-method to call and the value as arguments.
     */
    protected static void unaryOpWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
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
                    vm.error(new LuaTypeError());
                    return;
                }
                if (t0.isFunction()) {
                    vm.callExternal(0, t0.getFunc(), t1);
                    return;
                } else {
                    vm.callInternal(0, LuaFunction::callWithMeta, t0, t1);
                    return;
                }
            case 0:
                vm.internalReturn(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }
}

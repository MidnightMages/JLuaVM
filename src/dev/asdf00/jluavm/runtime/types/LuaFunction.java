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

public abstract class LuaFunction {
    public final LuaObject[] _ENV;
    public final LuaObject[] closures;

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
                if (mtbl == null) {
                    return LuaObject.nil();
                } else {
                    vm.callInternal(resumeLabel, LuaFunction::getWithMeta, obj, key, mtbl);
                    return null;
                }
            }
        } else if (obj.isUserData()) {
            try {
                return obj.get(idx);
            } catch (LuaRuntimeError ex) {
                vm.error(new LuaForeignCallError());
                return null;
            }
        } else {
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
                if (mtbl == null) {
                    obj.set(key, val);
                    return false;
                } else {
                    vm.callInternal(resumeLabel, LuaFunction::setWithMeta, obj, key, val, mtbl);
                    return true;
                }
            }
        } else if (obj.isUserData()) {
            try {
                obj.set(idx, val);
                return false;
            } catch (LuaRuntimeError ex) {
                vm.error(new LuaForeignCallError());
                return true;
            }
        } else {
            vm.error(new LuaTypeError());
            return true;
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
        LuaObject t0 = null, t1 = null, t2 = null, t3 = null;
        // on resume
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(4);
                if (args.length != 3) {
                    throw new InternalLuaRuntimeError("expected 3 arguments, got " + args.length);
                }
                t0 = args[0]; // original table
                t1 = args[0]; // original key
                t2 = args[0]; // metatable
            }
            case 0 -> {
                // restore expression stack
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                // use first return variable
                t2 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
            case 2 -> {
                // use first return variable
                t0 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
        }
        returned = null;
        switch (resume) {
            case -1:
                // getExpression from metatable
                if (t2.isTable()) {
                    LuaObject metatable = t2;
                    LuaObject key = t1;
                    if (metatable.hasKey(key)) {
                        vm.internalReturn(metatable.get(key));
                        return;
                    } else {
                        LuaObject mmtbl = metatable.getMetaTable();
                        if (mmtbl == null) {
                            vm.internalReturn(LuaObject.nil());
                            return;
                        }
                        // save expresion stack
                        expressionStack[0] = t0;
                        expressionStack[1] = t1;
                        vm.callInternal(0, LuaFunction::getWithMeta, metatable, key, mmtbl);
                        return;
                    }
                } else {
                    vm.error(new LuaMetaTableError());
                    return;
                }
            case 0:
                if (t2.isFunction()) {
                    vm.callExternal(2, t2.getFunc(), t0, t1);
                    return;
                }
                // if the meta value is not a function, we return the value at t2.__index
                t3 = Singletons.__index;
                if (t2.isTable()) {
                    LuaObject table = t2;
                    LuaObject key = RTUtils.tryCoerceFloatToInt(t3);
                    if (table.hasKey(key)) {
                        t2 = table.get(key);
                    } else {
                        LuaObject mtbl = table.getMetaTable();
                        if (mtbl == null) {
                            t2 = LuaObject.nil();
                        } else {
                            vm.callInternal(0, LuaFunction::getWithMeta, table, key, mtbl);
                            return;
                        }
                    }
                } else if (t2.isUserData()) {
                    try {
                        t2 = t2.get(t3);
                    } catch (LuaRuntimeError ex) {
                        vm.error(new LuaForeignCallError());
                        return;
                    }
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
                t1 = null;
            case 2:
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
        LuaObject t0 = null, t1 = null, t2 = null, t3 = null, t4 = null;
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(5);
                if (args.length != 4) {
                    throw new InternalLuaRuntimeError("expected 4 arguments, got " + args.length);
                }
                t0 = args[0]; // table
                t1 = args[1]; // key
                t2 = args[2]; // value
                t3 = args[3]; // metatable
            }
            case 0 -> {
                // restore expression stack
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                t2 = expressionStack[2];
                // use first return variable
                t3 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
        }
        returned = null;
        switch (resume) {
            case -1:
                // load constant
                t4 = Singletons.__newindex;
                // getExpression index
                if (t3.isTable()) {
                    LuaObject table = t3;
                    LuaObject key = RTUtils.tryCoerceFloatToInt(t4);
                    if (table.hasKey(key)) {
                        t3 = table.get(key);
                    } else {
                        LuaObject mtbl = table.getMetaTable();
                        if (mtbl == null) {
                            t3 = LuaObject.nil();
                        } else {
                            // save expression stack
                            expressionStack[0] = t0;
                            expressionStack[1] = t1;
                            expressionStack[2] = t2;
                            vm.callInternal(0, LuaFunction::getWithMeta, table, key, mtbl);
                            return;
                        }
                    }
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
            case 0:
                if (t3.isFunction()) {
                    vm.callExternal(1, t3.getFunc(), t0, t1, t2);
                    return;
                } else if (t3.isNil()) {
                    // no __newindex found, doing plain assignment instead
                    t0.set(t1, t2);
                    vm.internalReturn();
                    return;
                }
                // repeat indexing assignment over meta value instead of original table
                t0 = t3;
                t3 = null;
                // set into meta value
                if (t0.isTable()) {
                    LuaObject table = t0;
                    LuaObject key = RTUtils.tryCoerceFloatToInt(t1);
                    if (key.isNil() || key.isNaN()) {
                        vm.error(new LuaArgumentError());
                        return;
                    }
                    if (table.hasKey(key)) {
                        table.set(key, t2);
                    } else {
                        LuaObject mtbl = table.getMetaTable();
                        if (mtbl == null) {
                            table.set(key, t2);
                        } else {
                            vm.callInternal(1, LuaFunction::setWithMeta, table, key, t2, mtbl);
                            return;
                        }
                    }
                } else if (t0.isUserData()) {
                    try {
                        t0.set(t1, t2);
                    } catch (LuaRuntimeError ex) {
                        vm.error(new LuaForeignCallError());
                        return;
                    }
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
            case 1:
                vm.internalReturn();
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    /**
     * This method is meant to be called when at least one of the two arguments of the addition is not ARITHMETIC and a metatable __add call is needed.
     * The arguments for this method takes x and y as arguments.
     */
    protected static void callWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
        LuaObject t0 = null, t1 = null, t2 = null, t3 = null;
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(4);
                if (args.length < 1) {
                    throw new InternalLuaRuntimeError("expected 1 or more arguments, got " + args.length);
                }
                t0 = args[0]; // x
                t1 = LuaObject.of(Arrays.copyOfRange(args, 1, args.length)); // args for call
            }
            case 0 -> {
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                // unpack fist return value (meta value for x)
                t2 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
            case 1 -> {
                // pack all return arguments
                t0 = LuaObject.of(returned);
            }
        }
        switch (resume) {
            case -1:
                if (t0.isFunction()) {
                    throw new InternalLuaRuntimeError("callWithMeta should only be called on non-function objects");
                }
                t2 = t0.getMetaTable();
                if (t2 == null) {
                    vm.error(new LuaMetaTableError());
                }
                t3 = Singletons.__call;
                // getExpression meta value for possibly callable
                if (t2.isTable()) {
                    LuaObject table = t2;
                    LuaObject key = RTUtils.tryCoerceFloatToInt(t3);
                    if (table.hasKey(key)) {
                        t2 = table.get(key);
                    } else {
                        LuaObject mtbl = table.getMetaTable();
                        if (mtbl == null) {
                            t2 = LuaObject.nil();
                        } else {
                            // save expression stack
                            expressionStack[0] = t0;
                            expressionStack[1] = t1;
                            vm.callInternal(0, LuaFunction::getWithMeta, table, key, mtbl);
                            return;
                        }
                    }
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
                t3 = null;
            case 0:
                if (t2.isFunction()) {
                    vm.tailCall(t2.getFunc(), t0, t1);
                    return;
                } else {
                    vm.callInternal(1, LuaFunction::callWithMeta, t2, t0, t1);
                    return;
                }
            case 1:
                vm.internalReturn(t0);
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
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(4);
                if (args.length != 3) {
                    throw new InternalLuaRuntimeError("expected 3 arguments, got " + args.length);
                }
                t0 = args[0]; // metatable op entry
                t1 = args[1]; // x
                t2 = args[2]; // y
            }
            case 0 -> {
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                t2 = expressionStack[2];
                // unpack fist return value (meta value for x)
                t3 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
            case 1 -> {
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                t2 = expressionStack[2];
                // unpack fist return value (meta value for y)
                t3 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
            case 2 -> {
                // unpack fist return value of meta call
                t0 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
        }
        returned = null;
        switch (resume) {
            case -1:
                t3 = t1.getMetaTable();
                if (t3 == null) {
                    // no meta value for x
                    t3 = LuaObject.nil();
                } else {
                    // getExpression meta value for x
                    if (t3.isTable()) {
                        LuaObject table = t3;
                        LuaObject key = RTUtils.tryCoerceFloatToInt(t0);
                        if (table.hasKey(key)) {
                            t3 = table.get(key);
                        } else {
                            LuaObject mtbl = table.getMetaTable();
                            if (mtbl == null) {
                                t3 = LuaObject.nil();
                            } else {
                                // save expression stack
                                expressionStack[0] = t0;
                                expressionStack[1] = t1;
                                expressionStack[2] = t2;
                                vm.callInternal(0, LuaFunction::getWithMeta, table, key, mtbl);
                                return;
                            }
                        }
                    } else {
                        vm.error(new LuaTypeError());
                        return;
                    }
                }
            case 0:
                if (t3.isFunction()) {
                    // call the meta function and return the value
                    vm.callExternal(2, t3.getFunc(), t1, t2);
                    return;
                } else if (!t3.isNil()) {
                    // if we found something that is not a function, we try to call with meta
                    vm.callInternal(2, LuaFunction::callWithMeta, t3, t1, t2);
                    return;
                }
                // no meta method found for x
                t3 = t2.getMetaTable();
                if (t3 == null) {
                    // no meta value for x
                    t3 = LuaObject.nil();
                } else {
                    // getExpression meta value for y
                    if (t3.isTable()) {
                        LuaObject table = t3;
                        LuaObject key = RTUtils.tryCoerceFloatToInt(t0);
                        if (table.hasKey(key)) {
                            t3 = table.get(key);
                        } else {
                            LuaObject mtbl = table.getMetaTable();
                            if (mtbl == null) {
                                t3 = LuaObject.nil();
                            } else {
                                // save expression stack
                                expressionStack[0] = t0;
                                expressionStack[1] = t1;
                                expressionStack[2] = t2;
                                vm.callInternal(1, LuaFunction::getWithMeta, table, key, mtbl);
                                return;
                            }
                        }
                    } else {
                        vm.error(new LuaTypeError());
                        return;
                    }
                }
            case 1:
                if (t3.isFunction()) {
                    // call the meta function and return the value
                    vm.callExternal(2, t3.getFunc(), t1, t2);
                    return;
                } else if (!t3.isNil()) {
                    // if we found something that is not a function, we try to call with meta
                    vm.callInternal(2, LuaFunction::callWithMeta, t3, t1, t2);
                    return;
                } else {
                    // we found no meta value
                    vm.error(new LuaMetaTableError());
                    return;
                }
            case 2:
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
        LuaObject t0 = null, t1 = null, t2 = null;
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(3);
                if (args.length != 2) {
                    throw new InternalLuaRuntimeError("expected 2 arguments, got " + args.length);
                }
                t0 = args[0]; // metatable op entry
                t1 = args[1]; // value
            }
            case 0 -> {
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                // unpack fist return value (meta value for x)
                t2 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
            case 1 -> {
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                // unpack fist return value (meta value for y)
                t2 = returned.length > 0 ? returned[0] : LuaObject.nil();
            }
        }
        returned = null;
        switch (resume) {
            case -1:
                t2 = t1.getMetaTable();
                if (t2 != null && t2.isTable()) {
                    LuaObject table = t2;
                    LuaObject key = RTUtils.tryCoerceFloatToInt(t0);
                    if (table.hasKey(key)) {
                        t2 = table.get(key);
                    } else {
                        LuaObject mtbl = table.getMetaTable();
                        if (mtbl == null) {
                            t2 = LuaObject.nil();
                        } else {
                            // save expression stack
                            expressionStack[0] = t0;
                            expressionStack[1] = t1;
                            vm.callInternal(0, LuaFunction::getWithMeta, table, key, mtbl);
                            return;
                        }
                    }
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
            case 0:
                if (t2.isFunction()) {
                    // call the meta function and return the value
                    vm.callExternal(1, t2.getFunc(), t1);
                    return;
                } else if (!t2.isNil()) {
                    // if we found something that is not a function, we try to call with meta
                    vm.callInternal(1, LuaFunction::callWithMeta, t2, t1);
                    return;
                } else {
                    // we found no meta value
                    vm.error(new LuaMetaTableError());
                    return;
                }
            case 1:
                vm.returnValue(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }
}

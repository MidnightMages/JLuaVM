package dev.asdf00.jluavm;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.LuaRuntimeError$;
import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.errors.*;
import dev.asdf00.jluavm.runtime.types.*;
import dev.asdf00.jluavm.runtime.utils.*;

/**
 * <pre>
 * function (a, b)
 *   local t = x;
 *   x = a + b;
 *   local function f(a)
 *     return 1.0, t
 *   end
 *   a, y, b = f(a)
 *   return 1
 * end
 * </pre>
 */
public class Sandoboxo extends LuaFunction {
    public Sandoboxo(LuaObject[] closures) {
        super(closures);
    }

    @Override
    public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject returned) {
        LuaObject t0 = null, t1 = null, t2 = null, t3 = null, t4 = null, t5 = null, t6 = null, t7 = null;
        LuaObject[] tempRetVals = returned.asArray();
        // on resume
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(8);
            }
            case 0 -> {
                // unpack fist return value
                t0 = tempRetVals.length > 0 ? tempRetVals[0] : LuaObject.nil();
            }
            case 1 -> {
                // restore expression stack
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                // unpack fist return value
                t2 = tempRetVals.length > 0 ? tempRetVals[0] : LuaObject.nil();
            }
            case 2 -> {
                // nothing to restore
            }
            case 3 -> {
                // restore expression stack
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                t2 = expressionStack[2];
                t3 = expressionStack[3];
                t4 = expressionStack[4];
                // unpack 3 return values
                t5 = tempRetVals.length > 0 ? tempRetVals[0] : LuaObject.nil();
                t6 = tempRetVals.length > 1 ? tempRetVals[1] : LuaObject.nil();
                t7 = tempRetVals.length > 2 ? tempRetVals[2] : LuaObject.nil();
            }
            case 4 -> {
                // restore expression stack
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                t2 = expressionStack[2];
                t3 = expressionStack[3];
                t4 = expressionStack[4];
            }
        }
        tempRetVals = null;
        returned = null;
        switch (resume) {
            case -1:
                // load constant
                t0 = _ENV;
                // load constant
                t1 = LuaObject.of("x");
                // get index
                if (t0.isTable()) {
                    LuaObject table = t0;
                    LuaObject key = RTUtils.tryCoerceFloatToInt(t1);
                    if (table.hasKey(key)) {
                        t0 = table.get(key);
                    } else {
                        LuaObject mtbl = table.getMetaTable();
                        if (mtbl == null) {
                            t0 = LuaObject.nil();
                        } else {
                            vm.callInternal(0, Sandoboxo::getWithMeta, table, key, mtbl);
                            return;
                        }
                    }
                } else if (t0.isUserData()) {
                    try {
                        t0 = t0.get(t1);
                    } catch (LuaRuntimeError$ ex) {
                        vm.error(new LuaForeignCallError());
                        return;
                    }
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
                t1 = null;
            case 0:
                // assign local
                stackFrame[2] = t0;
                t0 = null;

                // load constant
                t0 = _ENV;
                // load constant
                t1 = LuaObject.of("x");
                // load local
                t2 = stackFrame[0];
                // load local
                t3 = stackFrame[1];
                // add
                if (t2.isArithmetic() && t3.isArithmetic()) {
                    t2 = t2.add(t3);
                } else {
                    // save expression stack
                    expressionStack[0] = t0;
                    expressionStack[1] = t1;
                    vm.callInternal(1, Sandoboxo::addWithMeta, t2, t3);
                    return;
                }
            case 1:
                // set index
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
                            vm.callInternal(2, Sandoboxo::setWithMeta, table, key, t2, mtbl);
                            return;
                        }
                    }
                } else if (t0.isUserData()) {
                    try {
                        t0.set(t1, t2);
                    } catch (LuaRuntimeError$ ex) {
                        vm.error(new LuaForeignCallError());
                        return;
                    }
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
                t2 = null;
                t1 = null;
                t0 = null;
            case 2:

                // load t
                t0 = stackFrame[2];
                // declare inner function with closure
                t0 = LuaObject.of(new InnerFunction(t0));
                // assign to local variable f
                stackFrame[3] = t0;
                t0 = null;

                // reserve t0 for assignment 0
                // load _ENV
                t1 = _ENV;
                // load "y"
                t2 = LuaObject.of("y");
                // reserve t3 for assignment 1
                // reserve t4 for assignment 2
                // load f
                t5 = stackFrame[3];
                // load a
                t6 = stackFrame[0];
                // call f(a)
                if (t5.isFunction()) {
                    // save expresion stack
                    expressionStack[0] = t0;
                    expressionStack[1] = t1;
                    expressionStack[2] = t2;
                    expressionStack[3] = t3;
                    expressionStack[4] = t4;
                    vm.callExternal(3, t5.getFunc(), t6);
                    return;
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
            case 3:
                // shuffle local vars for assignment
                t0 = t5;
                t3 = t6;
                t4 = t7;
                t5 = null;
                t6 = null;
                t7 = null;
                // assign local variable b
                stackFrame[1] = t4;
                t4 = null;
                // set index
                if (t1.isTable()) {
                    LuaObject table = t1;
                    LuaObject key = RTUtils.tryCoerceFloatToInt(t2);
                    if (key.isNil() || key.isNaN()) {
                        vm.error(new LuaArgumentError());
                        return;
                    }
                    if (table.hasKey(key)) {
                        table.set(key, t3);
                    } else {
                        LuaObject mtbl = table.getMetaTable();
                        if (mtbl == null) {
                            table.set(key, t3);
                        } else {
                            vm.callInternal(4, Sandoboxo::setWithMeta, table, key, t3, mtbl);
                            return;
                        }
                    }
                } else if (t1.isUserData()) {
                    try {
                        t1.set(t2, t3);
                    } catch (LuaRuntimeError$ ex) {
                        vm.error(new LuaForeignCallError());
                        return;
                    }
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
                t3 = null;
                t2 = null;
                t1 = null;
            case 4:
                // assign local variable b
                stackFrame[1] = t0;
                t0 = null;

                // load constant
                t0 = LuaObject.of(1);
                // return
                vm.returnValue(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    /**
     * This method is meant to be called when a plain table lookup has failed and a metatable __index call is needed.
     * The arguments for this method takes the original table, the key and the metatable as arguments.
     */
    protected static void getWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject returned) {
        LuaObject t0 = null, t1 = null, t2 = null;
        LuaObject[] tempRetVals = returned.asArray();
        // on resume
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(3);
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
                t2 = tempRetVals.length > 0 ? tempRetVals[0] : LuaObject.nil();
            }
            case 1 -> {
                // use first return variable
                t0 = tempRetVals.length > 0 ? tempRetVals[0] : LuaObject.nil();
            }
        }
        tempRetVals = null;
        returned = null;
        switch (resume) {
            case -1:
                // get from metatable
                if (t2.isTable()) {
                    LuaObject metatable = t2;
                    LuaObject key = t1;
                    if (metatable.hasKey(key)) {
                        vm.returnValue(metatable.get(key));
                        return;
                    } else {
                        LuaObject mmtbl = metatable.getMetaTable();
                        if (mmtbl == null) {
                            vm.returnValue(LuaObject.nil());
                            return;
                        }
                        // save expresion stack
                        expressionStack[0] = t0;
                        expressionStack[1] = t1;
                        vm.callInternal(0, Sandoboxo::getWithMeta, metatable, key, mmtbl);
                        return;
                    }
                } else {
                    vm.error(new LuaMetaTableError());
                    return;
                }
            case 0:
                if (t2.isFunction()) {
                    vm.callExternal(1, t2.getFunc(), t0, t1);
                    return;
                } else {
                    vm.returnValue(t0);
                    return;
                }
            case 1:
                vm.returnValue(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    /**
     * This method is meant to be called when a plain table assignment has failed and a metatable __newindex call is needed.
     * The arguments for this method takes the original table, the key, the value and the metatable as arguments.
     */
    protected static void setWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject returned) {
        LuaObject t0 = null, t1 = null, t2 = null, t3 = null, t4 = null;
        LuaObject[] tempRetVals = returned.asArray();
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
                t3 = tempRetVals.length > 0 ? tempRetVals[0] : LuaObject.nil();
            }
        }
        tempRetVals = null;
        returned = null;
        switch (resume) {
            case -1:
                // load constant
                t4 = Singletons.__newindex;
                // get index
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
                            vm.callInternal(0, Sandoboxo::getWithMeta, table, key, mtbl);
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
                    vm.returnValue();
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
                            vm.callInternal(1, Sandoboxo::setWithMeta, table, key, t2, mtbl);
                            return;
                        }
                    }
                } else if (t0.isUserData()) {
                    try {
                        t0.set(t1, t2);
                    } catch (LuaRuntimeError$ ex) {
                        vm.error(new LuaForeignCallError());
                        return;
                    }
                } else {
                    vm.error(new LuaTypeError());
                    return;
                }
            case 1:
                vm.returnValue();
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    /**
     * This method is meant to be called when at least one of the two arguments of the addition is not ARITHMETIC and a metatable __add call is needed.
     * The arguments for this method takes x and y as arguments.
     */
    protected static void addWithMeta(LuaVM_RT vm, LuaObject[] stackFrame, LuaObject[] args, int resume, LuaObject[] expressionStack, LuaObject returned) {
        LuaObject t0 = null, t1 = null, t2 = null, t3 = null;
        LuaObject[] tempRetVals = returned.asArray();
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(4);
                if (args.length != 2) {
                    throw new InternalLuaRuntimeError("expected 2 arguments, got " + args.length);
                }
                t0 = args[0]; // x
                t1 = args[1]; // y
            }
            case 0 -> {
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                // unpack fist return value (meta value for x)
                t2 = tempRetVals.length > 0 ? tempRetVals[0] : LuaObject.nil();
            }
            case 1 -> {
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                // unpack fist return value (meta value for y)
                t2 = tempRetVals.length > 0 ? tempRetVals[0] : LuaObject.nil();
            }
        }
        tempRetVals = null;
        returned = null;
        switch (resume) {
            case -1:
                t2 = t0.getMetaTable();
                if (t2 == null) {
                    // no meta value for x
                    t2 = LuaObject.nil();
                } else {
                    t3 = Singletons.__add;
                    // get meta value for x
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
                                vm.callInternal(0, Sandoboxo::getWithMeta, table, key, mtbl);
                                return;
                            }
                        }
                    } else {
                        vm.error(new LuaTypeError());
                        return;
                    }
                    t3 = null;
                }
            case 0:
                if (t2.isFunction()) {
                    // call the meta function and return the value
                    vm.tailCall(t2.getFunc(), t0, t1);
                    return;
                }
                // no meta method found for x
                t2 = t1.getMetaTable();
                if (t2 == null) {
                    // no meta value for x
                    t2 = LuaObject.nil();
                } else {
                    t3 = Singletons.__add;
                    // get meta value for y
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
                                vm.callInternal(1, Sandoboxo::getWithMeta, table, key, mtbl);
                                return;
                            }
                        }
                    } else {
                        vm.error(new LuaTypeError());
                        return;
                    }
                    t3 = null;
                }
            case 1:
                if (t2.isFunction()) {
                    // call the meta function and return the value
                    vm.tailCall(t2.getFunc(), t0, t1);
                    return;
                } else {
                    // no meta method found
                    vm.error(new LuaMetaTableError());
                    return;
                }
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    public static class InnerFunction extends LuaFunction {
        public InnerFunction(LuaObject... closures) {
            super(closures);
        }

        @Override
        public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject returned) {
            LuaObject t0 = null, t1 = null;
            LuaObject[] tempRetVals = returned.asArray();
            switch (resume) {
                case -1 -> {
                    expressionStack = vm.registerExpressionStack(2);
                }
            }
            tempRetVals = null;
            returned = null;
            switch (resume) {
                case -1:
                    // load constant
                    t0 = LuaObject.of(1.0);
                    // load closure without box
                    t1 = closures[0];
                    // return multi value
                    vm.returnValue(t0, t1);
                    return;
                default:
                    throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
        }
    }
}

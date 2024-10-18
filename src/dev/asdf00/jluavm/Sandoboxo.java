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
 *     return 1, t
 *   end
 *   a, y, b = f(a)
 *   return 1
 * end
 * </pre>
 */
public class Sandoboxo extends LuaFunction {
    public Sandoboxo(ILuaVariable[] closures) {
        super(closures);
    }

    @Override
    public void invoke(LuaVM_RT vm, ILuaVariable[] stackFrame, int resume, ILuaVariable[] expressionStack, LuaArray returned) {
        ILuaVariable t0 = null, t1 = null, t2 = null, t3 = null, t4 = null, t5 = null, t6 = null, t7 = null;
        // on resume
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(8);
            }
            case 0 -> {
                // unpack fist return value
                t0 = returned.get(0);
            }
            case 1 -> {
                // restore expression stack
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                // unpack fist return value
                t2 = returned.get(0);
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
                t5 = returned.get(0);
                t6 = returned.get(1);
                t7 = returned.get(2);
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
        returned = null;
        switch (resume) {
            case -1:
                // load constant
                t0 = _ENV;
                // load constant
                t1 = LuaString.of("x");
                // get index
                if (t0 instanceof LuaTable tbl) {
                    ILuaVariable key = RTUtils.tryCoerceFloatToInt(t1);
                    if (tbl.hasKey(key)) {
                        t0 = tbl.get(key);
                    } else {
                        LuaTable mtbl = tbl.getMetaTable();
                        if (mtbl == null) {
                            t0 = Singletons.NIL;
                        } else {
                            // return value of getFromMetaTable lands in t0
                            vm.callInternal(0, Sandoboxo::getWithMeta, mtbl, Singletons.Meta.__index);
                            return;
                        }
                    }
                } else if (t0 instanceof ILuaUserData uData) {
                    try {
                        t0 = uData._luaGet(t1);
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
                t1 = LuaString.of("x");
                // load local
                t2 = stackFrame[0];
                // load local
                t3 = stackFrame[1];
                // add
                if (t2 instanceof ILuaSupportsArithmetic addX && t3 instanceof ILuaSupportsArithmetic addY) {
                    t2 = addX.add(addY);
                    t3 = null;
                } else {
                    // save expression stack
                    expressionStack[0] = t0;
                    expressionStack[1] = t1;
                    vm.callInternal(1, Sandoboxo::addWithMeta, t2, t3);
                    return;
                }
            case 1:
                // set index
                if (t0 instanceof LuaTable tbl) {
                    ILuaVariable key = RTUtils.tryCoerceFloatToInt(t1);
                    if (key.isNil() || key.isNaN()) {
                        vm.error(new LuaArgumentError());
                        return;
                    }
                    if (tbl.hasKey(key)) {
                        tbl.set(key, t2);
                    } else {
                        LuaTable mtbl = tbl.getMetaTable();
                        if (mtbl == null) {
                            tbl.set(key, t2);
                        } else {
                            vm.callInternal(2, Sandoboxo::setWithMeta, tbl, key, t2, mtbl);
                            return;
                        }
                    }
                } else if (t0 instanceof ILuaUserData uData) {
                    try {
                        uData._luaSet(t1, t2);
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
                // declare inner function
                t0 = new InnerFunction(RTUtils.pack(t0));
                // assign to local variable f
                stackFrame[3] = t0;
                t0 = null;

                // reserve t0 for assignment 0
                // load _ENV
                t1 = _ENV;
                // load "y"
                t2 = LuaString.of("y");
                // reserve t3 for assignment 1
                // reserve t4 for assignment 2
                // load f
                t5 = stackFrame[3];
                // load a
                t6 = stackFrame[0];
                // call f(a)
                if (t5 instanceof LuaFunction func) {
                    // save expresion stack
                    expressionStack[0] = t0;
                    expressionStack[1] = t1;
                    expressionStack[2] = t2;
                    expressionStack[3] = t3;
                    expressionStack[4] = t4;
                    vm.callExternal(3, func, t6);
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
                if (t1 instanceof LuaTable tbl) {
                    ILuaVariable key = RTUtils.tryCoerceFloatToInt(t2);
                    if (key.isNil() || key.isNaN()) {
                        vm.error(new LuaArgumentError());
                        return;
                    }
                    if (tbl.hasKey(key)) {
                        tbl.set(key, t3);
                    } else {
                        LuaTable mtbl = tbl.getMetaTable();
                        if (mtbl == null) {
                            tbl.set(key, t3);
                        } else {
                            // save expresion stack
                            expressionStack[0] = t0;
                            vm.callInternal(4, Sandoboxo::setWithMeta, tbl, key, t3, mtbl);
                            return;
                        }
                    }
                } else if (t1 instanceof ILuaUserData uData) {
                    try {
                        uData._luaSet(t2, t3);
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
                t0 = LuaDouble.of(1);
                // return
                vm.returnValue(t0);
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    protected static void getWithMeta(LuaVM_RT vm, ILuaVariable[] stackFrame, ILuaVariable[] args, int resume, ILuaVariable[] expressionStack, LuaArray returned) {
        ILuaVariable t0 = null;
        // on resume
        switch (resume) {
            case -1 -> vm.registerExpressionStack(1);
            case 0 -> {
                // use first return variable
                t0 = returned.get(0);
            }
            case 1 -> {
                // use first return variable
                t0 = returned.get(0);
            }
        }
        returned = null;
        switch (resume) {
            case -1:
                if (args.length != 2) {
                    throw new InternalLuaRuntimeError("expected 2 arguments, got " + args.length);
                }
                if (args[0] instanceof LuaTable tbl) {
                    ILuaVariable key = RTUtils.tryCoerceFloatToInt(args[1]);
                    if (tbl.hasKey(key)) {
                        vm.returnValue(tbl.get(key));
                        return;
                    } else {
                        LuaTable mtbl = tbl.getMetaTable();
                        if (mtbl == null) {
                            vm.returnValue(Singletons.NIL);
                            return;
                        }
                        vm.callInternal(0, Sandoboxo::getWithMeta, mtbl, Singletons.Meta.__index);
                        return;
                    }
                } else {
                    vm.error(new LuaMetaTableError());
                    return;
                }
            case 0:
                if (t0 instanceof LuaFunction func) {
                    vm.callExternal(1,func, args[0], RTUtils.tryCoerceFloatToInt(args[1]));
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

    protected static void setWithMeta(LuaVM_RT vm, ILuaVariable[] stackFrame, ILuaVariable[] args, int resume, ILuaVariable[] expressionStack, LuaArray returned) {
        ILuaVariable t0 = null, t1 = null, t2 = null, t3 = null, t4 = null, t5 = null, t6 = null, t7 = null;
        switch (resume) {
            case -1 -> {
                t0 = args.length > 0 ? args[0] : Singletons.NIL; // table
                t1 = args.length > 1 ? args[1] : Singletons.NIL; // key
                t2 = args.length > 2 ? args[2] : Singletons.NIL; // value
                t3 = args.length > 3 ? args[3] : Singletons.NIL; // meta-value
            }
            case 0 -> {
                // restore expression stack
                t0 = expressionStack[0];
                t1 = expressionStack[1];
                t2 = expressionStack[2];
                // use first return variable
                t3 = returned.get(0);
            }
        }
        returned = null;
        switch (resume) {
            case -1:
                // load constant
                t4 = Singletons.Meta.__newindex;
                // get index
                if (t3 instanceof LuaTable tbl) {
                    ILuaVariable key = RTUtils.tryCoerceFloatToInt(t4);
                    if (tbl.hasKey(key)) {
                        t3 = tbl.get(key);
                        t4 = null;
                    } else {
                        LuaTable mtbl = tbl.getMetaTable();
                        if (mtbl == null) {
                            t3 = Singletons.NIL;
                            t4 = null;
                        } else {
                            // save expression stack
                            expressionStack[0] = t0;
                            expressionStack[1] = t1;
                            expressionStack[2] = t2;
                            // return value of getFromMetaTable lands in t3
                            vm.callInternal(0, Sandoboxo::getWithMeta, mtbl, Singletons.Meta.__newindex);
                            return;
                        }
                    }
                } else if (t0 instanceof ILuaUserData uData) {
                    try {
                        t0 = uData._luaGet(t1);
                        t1 = null;
                    } catch (LuaRuntimeError$ ex) {
                        vm.error(new LuaForeignCallError());
                        return;
                    }
                } else {
                    throw new InternalLuaRuntimeError("should not reach");
                }
            case 0:
                if (t3 instanceof LuaFunction func) {
                    vm.callExternal(1, func, t0, t1, t2);
                    return;
                } else {
                    vm.callInternal(1, Sandoboxo::setWithMeta, t0, t1, t2, t3);
                    return;
                }
            case 1:
                vm.returnValue();
                return;
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    protected static void addWithMeta(LuaVM_RT vm, ILuaVariable[] stackFrame, ILuaVariable[] args, int resume, ILuaVariable[] expressionStack, LuaArray returned) {
        ILuaVariable t0 = null, t1 = null, t2 = null, t3 = null;
        switch (resume) {
            case -1 -> {
                expressionStack = vm.registerExpressionStack(4);
                t0 = args.length > 0 ? args[0] : Singletons.NIL; // x
                t1 = args.length > 1 ? args[1] : Singletons.NIL; // y
            }
            case 0 -> {
               t0 = expressionStack[0];
               t1 = expressionStack[1];
               t2 = returned.get(0); // meta-value
            }
            case 1 -> {
                t0 = returned.get(0); // meta call result
            }
        }
        returned = null;
        switch (resume) {
            case -1:
                t2 = t0.getMetaTable();
                if (t2 == null) {
                    t2 = t1.getMetaTable();
                }
                if (t2 == null) {
                    vm.error(new LuaMetaTableError());
                    return;
                }
                // save expression stack
                expressionStack[0] = t0;
                expressionStack[1] = t1;
                vm.callInternal(0, Sandoboxo::getWithMeta, t2, Singletons.Meta.__add);
                return;
            case 0:
                if (t2 instanceof LuaFunction mfunc) {
                    vm.callExternal(1, mfunc, t0, t1);
                    return;
                } else {
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

    public static class InnerFunction extends LuaFunction {
        public InnerFunction(ILuaVariable[] closures) {
            super(closures);
        }

        @Override
        public void invoke(LuaVM_RT vm, ILuaVariable[] stackFrame, int resume, ILuaVariable[] expressionStack, LuaArray returned) {
            ILuaVariable t0 = null, t1 = null;
            switch (resume) {
                case -1 -> {
                    expressionStack = vm.registerExpressionStack(2);
                }
            }
            returned = null;
            switch (resume) {
                case -1:
                    // load constant
                    t0 = LuaDouble.of(1);
                    // load closure without box
                    t1 = closures[0];
                    // return multi value
                    vm.returnValue(RTUtils.pack(t0, t1));
                    return;
                default:
                    throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
        }
    }
}

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
    public LuaReturnValue invoke(LuaVM_RT vm, ILuaVariable[] stackFrame, int resume, ILuaVariable[] expressionStack, LuaArray returned) {
        ILuaVariable t0 = null, t1 = null, t2 = null, t3 = null, t4 = null, t5 = null, t6 = null, t7 = null;
        // on resume
        switch (resume) {
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
                            return LuaReturnValue.callInternal(0, RTUtils.pack(), Sandoboxo::getWithMeta, RTUtils.pack(mtbl, Singletons.Meta.__index));
                        }
                    }
                } else if (t0 instanceof ILuaUserData uData) {
                    try {
                        t0 = uData._luaGet(t1);
                    } catch (LuaRuntimeError$ ex) {
                        return LuaReturnValue.error(new LuaForeignCallError());
                    }
                } else {
                    return LuaReturnValue.error(new LuaTypeError());
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
                    return LuaReturnValue.callInternal(1, RTUtils.pack(t0, t1), Sandoboxo::addWithMeta, RTUtils.pack(t2, t3));
                }
            case 1:
                // set index
                if (t0 instanceof LuaTable tbl) {
                    ILuaVariable key = RTUtils.tryCoerceFloatToInt(t1);
                    if (key.isNil() || key.isNaN()) {
                        return LuaReturnValue.error(new LuaArgumentError());
                    }
                    if (tbl.hasKey(key)) {
                        tbl.set(key, t2);
                    } else {
                        LuaTable mtbl = tbl.getMetaTable();
                        if (mtbl == null) {
                            tbl.set(key, t2);
                        } else {
                            return LuaReturnValue.callInternal(2, RTUtils.pack(), Sandoboxo::setWithMeta, RTUtils.pack(tbl, key, t2, mtbl));
                        }
                    }
                } else if (t0 instanceof ILuaUserData uData) {
                    try {
                        uData._luaSet(t1, t2);
                    } catch (LuaRuntimeError$ ex) {
                        return LuaReturnValue.error(new LuaForeignCallError());
                    }
                } else {
                    return LuaReturnValue.error(new LuaTypeError());
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
                    return LuaReturnValue.callExternal(3, RTUtils.pack(t0, t1, t2, t3, t4), func, RTUtils.pack(t6));
                } else {
                    return LuaReturnValue.error(new LuaTypeError());
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
                        return LuaReturnValue.error(new LuaArgumentError());
                    }
                    if (tbl.hasKey(key)) {
                        tbl.set(key, t3);
                    } else {
                        LuaTable mtbl = tbl.getMetaTable();
                        if (mtbl == null) {
                            tbl.set(key, t3);
                        } else {
                            return LuaReturnValue.callInternal(4, RTUtils.pack(t0), Sandoboxo::setWithMeta, RTUtils.pack(tbl, key, t3, mtbl));
                        }
                    }
                } else if (t1 instanceof ILuaUserData uData) {
                    try {
                        uData._luaSet(t2, t3);
                    } catch (LuaRuntimeError$ ex) {
                        return LuaReturnValue.error(new LuaForeignCallError());
                    }
                } else {
                    return LuaReturnValue.error(new LuaTypeError());
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
                return LuaReturnValue.returnValue(t0);
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    protected static LuaReturnValue getWithMeta(LuaVM_RT vm, ILuaVariable[] stackFrame, ILuaVariable[] args, int resume, ILuaVariable[] expressionStack, LuaArray returned) {
        ILuaVariable t0 = null;
        // on resume
        switch (resume) {
            case 0 -> {
                // use first return variable
                t0 = returned.get(0);
            }
            case 1 -> {
                // use first return variable
                t0 = returned.get(0);
            }
        }
        switch (resume) {
            case -1:
                if (args.length != 2) {
                    throw new InternalLuaRuntimeError("expected 2 arguments, got " + args.length);
                }
                if (args[0] instanceof LuaTable tbl) {
                    ILuaVariable key = RTUtils.tryCoerceFloatToInt(args[1]);
                    if (tbl.hasKey(key)) {
                        return LuaReturnValue.returnValue(tbl.get(key));
                    } else {
                        LuaTable mtbl = tbl.getMetaTable();
                        if (mtbl == null) {
                            return LuaReturnValue.returnValue(Singletons.NIL);
                        }
                        return LuaReturnValue.callInternal(0, RTUtils.pack(), Sandoboxo::getWithMeta, RTUtils.pack(mtbl, Singletons.Meta.__index));
                    }
                } else {
                    return LuaReturnValue.error(new LuaMetaTableError());
                }
            case 0:
                if (t0 instanceof LuaFunction func) {
                    return LuaReturnValue.callExternal(1, RTUtils.pack(), func, RTUtils.pack(args[0], RTUtils.tryCoerceFloatToInt(args[1])));
                } else {
                    return LuaReturnValue.returnValue(t0);
                }
            case 1:
                return LuaReturnValue.returnValue(t0);
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    protected static LuaReturnValue setWithMeta(LuaVM_RT vm, ILuaVariable[] stackFrame, ILuaVariable[] args, int resume, ILuaVariable[] expressionStack, LuaArray returned) {
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
                            // return value of getFromMetaTable lands in t3
                            return LuaReturnValue.callInternal(0, RTUtils.pack(t0, t1, t2), Sandoboxo::getWithMeta, RTUtils.pack(mtbl, Singletons.Meta.__newindex));
                        }
                    }
                } else if (t0 instanceof ILuaUserData uData) {
                    try {
                        t0 = uData._luaGet(t1);
                        t1 = null;
                    } catch (LuaRuntimeError$ ex) {
                        return LuaReturnValue.error(new LuaForeignCallError());
                    }
                } else {
                    throw new InternalLuaRuntimeError("should not reach");
                }
            case 0:
                if (t3 instanceof LuaFunction func) {
                    return LuaReturnValue.callExternal(1, RTUtils.pack(), func, RTUtils.pack(t0, t1, t2));
                } else {
                    return LuaReturnValue.callInternal(1, RTUtils.pack(), Sandoboxo::setWithMeta, RTUtils.pack(t0, t1, t2, t3));
                }
            case 1:
                return LuaReturnValue.returnValue();
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    protected static LuaReturnValue addWithMeta(LuaVM_RT vm, ILuaVariable[] stackFrame, ILuaVariable[] args, int resume, ILuaVariable[] expressionStack, LuaArray returned) {
        ILuaVariable t0 = null, t1 = null, t2 = null, t3 = null;
        switch (resume) {
            case -1 -> {
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
        switch (resume) {
            case -1:
                t2 = t0.getMetaTable();
                if (t2 == null) {
                    t2 = t1.getMetaTable();
                }
                if (t2 == null) {
                    return LuaReturnValue.error(new LuaMetaTableError());
                }
                return LuaReturnValue.callInternal(0, RTUtils.pack(t0, t1), Sandoboxo::getWithMeta, RTUtils.pack(t2, Singletons.Meta.__add));
            case 0:
                if (t2 instanceof LuaFunction mfunc) {
                    return LuaReturnValue.callExternal(1, RTUtils.pack(), mfunc, RTUtils.pack(t0, t1));
                } else {
                    return LuaReturnValue.error(new LuaMetaTableError());
                }
            case 1:
                return LuaReturnValue.returnValue(t0);
            default:
                throw new InternalLuaRuntimeError("unknown resume point " + resume);
        }
    }

    public static class InnerFunction extends LuaFunction {
        public InnerFunction(ILuaVariable[] closures) {
            super(closures);
        }

        @Override
        public LuaReturnValue invoke(LuaVM_RT vm, ILuaVariable[] stackFrame, int resume, ILuaVariable[] expressionStack, LuaArray returned) {
            ILuaVariable t0 = null, t1 = null;
            switch (resume) {
            }
            switch (resume) {
                case -1:
                    // load constant
                    t0 = LuaDouble.of(1);
                    // load closure without box
                    t1 = closures[0];
                    // return multi value
                    return LuaReturnValue.returnValue(RTUtils.pack(t0, t1));
                default:
                    throw new InternalLuaRuntimeError("unknown resume point " + resume);
            }
        }
    }
}
